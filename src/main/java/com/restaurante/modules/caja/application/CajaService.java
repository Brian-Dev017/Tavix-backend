package com.restaurante.modules.caja.application;

import com.restaurante.modules.caja.infrastructure.persistence.ComprobanteEntity;
import com.restaurante.modules.caja.infrastructure.persistence.ComprobanteJpaRepo;
import com.restaurante.modules.caja.infrastructure.persistence.DatosComprobanteEntity;
import com.restaurante.modules.caja.infrastructure.persistence.DatosComprobanteJpaRepo;
import com.restaurante.modules.caja.infrastructure.persistence.ArqueoEntity;
import com.restaurante.modules.caja.infrastructure.persistence.ArqueoJpaRepo;
import com.restaurante.modules.caja.infrastructure.web.dto.ComprobanteResponseDTO;
import com.restaurante.modules.caja.infrastructure.web.dto.DatosComprobanteRequest;
import com.restaurante.modules.caja.infrastructure.web.dto.EmitirComprobanteRequest;
import com.restaurante.modules.caja.infrastructure.web.dto.PedidoResumenDTO;
import com.restaurante.modules.configuracion.infrastructure.persistence.SerieComprobanteEntity;
import com.restaurante.modules.configuracion.infrastructure.persistence.SerieComprobanteJpaRepo;
import com.restaurante.modules.configuracion.infrastructure.persistence.NegocioConfigJpaRepo;
import com.restaurante.modules.configuracion.infrastructure.persistence.NegocioConfigEntity;
import com.restaurante.modules.catalogo.infrastructure.persistence.ProductoJpaRepo;
import com.restaurante.modules.mesas.application.MesaService;
import com.restaurante.modules.pedidos.infrastructure.persistence.DetallePedidoJpaRepo;
import com.restaurante.modules.pedidos.infrastructure.persistence.PedidoEntity;
import com.restaurante.modules.pedidos.infrastructure.persistence.PedidoJpaRepo;
import com.restaurante.modules.pedidos.infrastructure.web.dto.ItemPedidoDTO;
import com.restaurante.shared.exception.BusinessException;
import com.restaurante.shared.audit.AuditoriaValidacionService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.nio.charset.StandardCharsets;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@Service
public class CajaService {

    private static final Set<String> TIPOS_COMPROBANTE = Set.of("T", "B", "F");
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(CajaService.class);

    @PersistenceContext
    private EntityManager em;

    private final ComprobanteJpaRepo comprobanteRepo;
    private final ArqueoJpaRepo arqueoRepo;
    private final DatosComprobanteJpaRepo datosRepo;
    private final PedidoJpaRepo pedidoRepo;
    private final SerieComprobanteJpaRepo serieRepo;
    private final DetallePedidoJpaRepo detalleRepo;
    private final ProductoJpaRepo productoRepo;
    private final MesaService mesaService;
    private final NegocioConfigJpaRepo negocioRepo;
    private final AuditoriaValidacionService auditoriaValidacionService;

    public CajaService(ComprobanteJpaRepo comprobanteRepo,
                       ArqueoJpaRepo arqueoRepo,
                       DatosComprobanteJpaRepo datosRepo,
                       PedidoJpaRepo pedidoRepo,
                       SerieComprobanteJpaRepo serieRepo,
                       DetallePedidoJpaRepo detalleRepo,
                       ProductoJpaRepo productoRepo,
                       MesaService mesaService,
                       NegocioConfigJpaRepo negocioRepo,
                       AuditoriaValidacionService auditoriaValidacionService) {
        this.comprobanteRepo = comprobanteRepo;
        this.arqueoRepo = arqueoRepo;
        this.datosRepo = datosRepo;
        this.pedidoRepo = pedidoRepo;
        this.serieRepo = serieRepo;
        this.detalleRepo = detalleRepo;
        this.productoRepo = productoRepo;
        this.mesaService = mesaService;
        this.negocioRepo = negocioRepo;
        this.auditoriaValidacionService = auditoriaValidacionService;
    }

    @SuppressWarnings("unchecked")
    public List<PedidoResumenDTO> getPedidosActivos() {
        List<Object[]> rows = em.createNativeQuery(
                "SELECT pedido_id, mesa, mesero, total_items, subtotal, igv, total_con_igv, estado_pedido " +
                "FROM v_consumo_por_pedido " +
                "WHERE estado_pedido IN ('ABIERTO','EN_COCINA','LISTO') " +
                "AND total_items > 0 " +
                "AND total_con_igv > 0 " +
                "ORDER BY pedido_id ASC"
        ).getResultList();

        return rows.stream().map(r -> new PedidoResumenDTO(
                ((Number) r[0]).longValue(),
                (String) r[1],
                (String) r[2],
                ((Number) r[3]).intValue(),
                (BigDecimal) r[4],
                (BigDecimal) r[5],
                (BigDecimal) r[6],
                (String) r[7],
                cargarItems(((Number) r[0]).longValue())
        )).toList();
    }

    @Transactional
    public ComprobanteResponseDTO emitirComprobante(Long cajeroId, boolean puedeAplicarDescuento,
                                                    EmitirComprobanteRequest request) {
        String rolActor = puedeAplicarDescuento ? "AD" : "CA";
        Long referenciaId = request != null ? request.pedidoId() : null;
        String datosAuditoria = resumirRequest(request);
        try {
            validarRequestBasico(request);
            if (!puedeAplicarDescuento && tieneDescuento(request.descuento())) {
                throw new BusinessException("Solo un administrador puede aplicar descuentos", HttpStatus.FORBIDDEN);
            }

            if (comprobanteRepo.findByPedidoId(request.pedidoId()).isPresent()) {
                throw new BusinessException("Ya existe un comprobante para este pedido", HttpStatus.CONFLICT);
            }

            PedidoEntity pedido = pedidoRepo.findById(request.pedidoId())
                    .orElseThrow(() -> new BusinessException("Pedido no encontrado", HttpStatus.NOT_FOUND));

            if (pedido.getEstado() == PedidoEntity.EstadoPedido.COBRADO
                    || pedido.getEstado() == PedidoEntity.EstadoPedido.CANCELADO) {
                throw new BusinessException("El pedido ya fue cerrado", HttpStatus.BAD_REQUEST);
            }
            if (pedido.getEstado() != PedidoEntity.EstadoPedido.LISTO) {
                throw new BusinessException("Solo se puede cobrar un pedido LISTO", HttpStatus.CONFLICT);
            }

            String tipo = normalizarTipo(request.tipoComprobanteId());
            validarDatosComprobante(tipo, request.datosComprobante());
            ComprobanteEntity.MetodoPago metodoPago = parseMetodoPago(request.metodoPago());
            ArqueoEntity arqueo = arqueoRepo
                    .findTopByCajeroIdAndEstadoOrderByAperturaEnDesc(cajeroId, ArqueoEntity.EstadoArqueo.ABIERTO)
                    .orElseThrow(() -> new BusinessException("Debes aperturar tu caja antes de cobrar",
                            HttpStatus.CONFLICT));

            Object[] t = cargarTotales(request.pedidoId());
            BigDecimal subtotal = (BigDecimal) t[0];
            BigDecimal igv = (BigDecimal) t[1];
            BigDecimal totalBruto = (BigDecimal) t[2];
            BigDecimal descuento = normalizarDescuento(request.descuento(), totalBruto, request.motivoDescuento());
            BigDecimal total = aplicarRedondeoSegunMetodo(totalBruto.subtract(descuento), metodoPago);
            BigDecimal efectivoRecibido = normalizarEfectivoRecibido(metodoPago, request.efectivoRecibido(), total);
            BigDecimal vuelto = metodoPago == ComprobanteEntity.MetodoPago.EFECTIVO
                    ? efectivoRecibido.subtract(total)
                    : BigDecimal.ZERO;

            Long datosId = guardarDatosSiCorresponde(tipo, request.datosComprobante());
            SerieComprobanteEntity serie = serieRepo.findTopByTipoAndActivoTrueOrderByIdAsc(tipo)
                    .orElseThrow(() -> new BusinessException("No hay serie activa para el comprobante " + tipo,
                            HttpStatus.CONFLICT));
            int numero = serie.getCorrelativoActual();
            serie.setCorrelativoActual(numero + 1);
            serieRepo.save(serie);

            ComprobanteEntity comp = new ComprobanteEntity();
            comp.setPedidoId(request.pedidoId());
            comp.setCajeroId(cajeroId);
            comp.setArqueoCajaId(arqueo.getId());
            comp.setTipoComprobanteId(tipo);
            comp.setSerie(serie.getSerie());
            comp.setNumero(numero);
            comp.setDatosComprobanteId(datosId);
            comp.setSubtotal(subtotal);
            comp.setIgv(igv);
            comp.setDescuento(descuento);
            comp.setMotivoDescuento(limpiar(request.motivoDescuento()));
            comp.setTotal(total);
            comp.setMetodoPago(metodoPago);
            comp.setEfectivoRecibido(efectivoRecibido);
            comp.setVuelto(vuelto);
            ComprobanteEntity saved = comprobanteRepo.save(comp);

            saved.setEstado(ComprobanteEntity.EstadoComprobante.COMPLETADO);
            saved.setPagadoEn(LocalDateTime.now());
            saved.setActualizadoEn(LocalDateTime.now());
            saved = comprobanteRepo.saveAndFlush(saved);

            pedido.setEstado(PedidoEntity.EstadoPedido.COBRADO);
            pedidoRepo.save(pedido);
            if (pedido.getSesionMesaId() != null) {
                programarLiberacionMesa(pedido.getSesionMesaId(), request.pedidoId());
            }

            auditoriaValidacionService.registrar(
                    "CAJA", "EMITIR_COMPROBANTE", cajeroId, rolActor, request.pedidoId(),
                    "OK", "Comprobante emitido correctamente", datosAuditoria);

            return new ComprobanteResponseDTO(
                    saved.getId(), saved.getPedidoId(),
                    saved.getTipoComprobanteId(), saved.getSerie(), saved.getNumero(),
                    saved.getMetodoPago().name(), nombreTipoComprobante(saved.getTipoComprobanteId()),
                    subtotal, igv, descuento, total,
                    saved.getEfectivoRecibido(), saved.getVuelto(),
                    saved.getEstado().name(), saved.getPagadoEn()
            );
        } catch (BusinessException ex) {
            auditoriaValidacionService.registrar(
                    "CAJA", "EMITIR_COMPROBANTE", cajeroId, rolActor, referenciaId,
                    "ERROR", ex.getMessage(), datosAuditoria);
            throw ex;
        } catch (RuntimeException ex) {
            auditoriaValidacionService.registrar(
                    "CAJA", "EMITIR_COMPROBANTE", cajeroId, rolActor, referenciaId,
                    "ERROR", "Error inesperado al emitir comprobante", datosAuditoria);
            throw ex;
        }
    }

    public byte[] generarEscPos(Long comprobanteId) {
        ComprobanteEntity comp = comprobanteRepo.findById(comprobanteId)
                .orElseThrow(() -> new BusinessException("Comprobante no encontrado", HttpStatus.NOT_FOUND));
        NegocioConfigEntity negocio = negocioRepo.findById(1L).orElse(null);
        String nombreNegocio = negocio != null && !estaVacio(negocio.getNombreComercial())
                ? limpiar(negocio.getNombreComercial())
                : "LA FLOR DEL TUMBO";
        String rucNegocio = negocio != null && !estaVacio(negocio.getRucNegocio())
                ? limpiar(negocio.getRucNegocio())
                : null;
        String direccionNegocio = negocio != null && !estaVacio(negocio.getDireccion())
                ? limpiar(negocio.getDireccion())
                : null;

        String serieNumero = comp.getSerie() + "-" + String.format("%08d", comp.getNumero());
        String ticket = "\u001B@"
                + nombreNegocio + "\n"
                + (rucNegocio != null ? "RUC: " + rucNegocio + "\n" : "")
                + (direccionNegocio != null ? direccionNegocio + "\n" : "")
                + "COMPROBANTE " + serieNumero + "\n"
                + "TIPO: " + nombreTipoComprobante(comp.getTipoComprobanteId()) + "\n"
                + "PAGO: " + comp.getMetodoPago().name() + "\n"
                + "------------------------------\n"
                + "SUBTOTAL: S/ " + comp.getSubtotal() + "\n"
                + "IGV:      S/ " + comp.getIgv() + "\n"
                + "DSCTO:    S/ " + comp.getDescuento() + "\n"
                + "TOTAL:    S/ " + comp.getTotal() + "\n"
                + (comp.getMetodoPago() == ComprobanteEntity.MetodoPago.EFECTIVO
                    ? "RECIBIDO: S/ " + comp.getEfectivoRecibido() + "\n"
                    + "VUELTO:   S/ " + comp.getVuelto() + "\n"
                    : "")
                + "------------------------------\n"
                + "Gracias por su visita\n\n\n"
                + "\u001DV\u0001";
        return ticket.getBytes(StandardCharsets.ISO_8859_1);
    }

    private List<ItemPedidoDTO> cargarItems(Long pedidoId) {
        return detalleRepo.findByPedidoId(pedidoId).stream()
                .map(d -> {
                    String nombre = productoRepo.findById(d.getProductoId())
                            .map(p -> p.getNombre())
                            .orElse("Desconocido");
                    BigDecimal subtotal = d.getPrecioUnitario()
                            .multiply(BigDecimal.valueOf(d.getCantidad()));
                    return new ItemPedidoDTO(
                            d.getId(), d.getPedidoId(), d.getProductoId(),
                            nombre, d.getCantidad(), d.getPrecioUnitario(),
                            subtotal, d.getEstado().name(), d.getObservaciones(), d.getCreadoEn()
                    );
                })
                .toList();
    }

    private void validarRequestBasico(EmitirComprobanteRequest request) {
        if (request == null || request.pedidoId() == null) {
            throw new BusinessException("El pedido es obligatorio", HttpStatus.BAD_REQUEST);
        }
        normalizarTipo(request.tipoComprobanteId());
        if (request.metodoPago() == null || request.metodoPago().isBlank()) {
            throw new BusinessException("El metodo de pago es obligatorio", HttpStatus.BAD_REQUEST);
        }
    }

    private String normalizarTipo(String tipo) {
        String normalized = tipo == null || tipo.isBlank() ? "T" : tipo.trim().toUpperCase();
        if (!TIPOS_COMPROBANTE.contains(normalized)) {
            throw new BusinessException("Tipo de comprobante invalido", HttpStatus.BAD_REQUEST);
        }
        return normalized;
    }

    private ComprobanteEntity.MetodoPago parseMetodoPago(String metodoPago) {
        try {
            return ComprobanteEntity.MetodoPago.valueOf(metodoPago.trim().toUpperCase());
        } catch (RuntimeException e) {
            throw new BusinessException("Metodo de pago invalido", HttpStatus.BAD_REQUEST);
        }
    }

    private void validarDatosComprobante(String tipo, DatosComprobanteRequest datos) {
        if ("F".equals(tipo)) {
            if (datos == null
                    || !soloDigitos(datos.rucDni(), 11)
                    || !textoValido(datos.razonSocial(), 3, 120)
                    || !textoValido(datos.direccion(), 5, 160)) {
                throw new BusinessException("Factura requiere RUC de 11 digitos, razon social y direccion",
                        HttpStatus.BAD_REQUEST);
            }
        }
        if ("B".equals(tipo)) {
            if (datos == null
                    || !soloDigitos(datos.rucDni(), 8)
                    || !textoNombreBoletaValido(datos.razonSocial())
                    || !estaVacio(datos.direccion())) {
                throw new BusinessException("Boleta requiere DNI de 8 digitos, nombre y apellido; no lleva direccion",
                        HttpStatus.BAD_REQUEST);
            }
        }
    }

    private Long guardarDatosSiCorresponde(String tipo, DatosComprobanteRequest request) {
        if (!"B".equals(tipo) && !"F".equals(tipo)) return null;
        if (request == null) return null;

        DatosComprobanteEntity datos = new DatosComprobanteEntity();
        datos.setTipoComprobanteId(tipo);
        datos.setRucDni(limpiar(request.rucDni()));
        datos.setRazonSocial(limpiar(request.razonSocial()));
        datos.setDireccion(limpiar(request.direccion()));
        return datosRepo.save(datos).getId();
    }

    private Object[] cargarTotales(Long pedidoId) {
        List<Object[]> totales = em.createNativeQuery(
                "SELECT subtotal, igv, total_con_igv FROM v_consumo_por_pedido WHERE pedido_id = ?1"
        ).setParameter(1, pedidoId).getResultList();

        if (totales.isEmpty()) {
            throw new BusinessException("El pedido no tiene items", HttpStatus.BAD_REQUEST);
        }
        return totales.get(0);
    }

    private BigDecimal normalizarDescuento(BigDecimal descuento, BigDecimal totalBruto, String motivo) {
        BigDecimal value = descuento == null ? BigDecimal.ZERO : descuento;
        if (value.signum() < 0 || value.compareTo(totalBruto) > 0) {
            throw new BusinessException("El descuento debe estar entre 0 y el total", HttpStatus.BAD_REQUEST);
        }
        if (value.signum() > 0 && estaVacio(motivo)) {
            throw new BusinessException("El motivo de descuento es obligatorio", HttpStatus.BAD_REQUEST);
        }
        return value;
    }

    private BigDecimal normalizarEfectivoRecibido(ComprobanteEntity.MetodoPago metodoPago,
                                                  BigDecimal recibido,
                                                  BigDecimal total) {
        if (metodoPago != ComprobanteEntity.MetodoPago.EFECTIVO) {
            return null;
        }
        if (recibido == null) {
            throw new BusinessException("El efectivo recibido es obligatorio", HttpStatus.BAD_REQUEST);
        }
        BigDecimal recibidoNormalizado = recibido.setScale(2, RoundingMode.HALF_UP);
        BigDecimal totalExigible = aplicarRedondeoSegunMetodo(total, metodoPago).setScale(2, RoundingMode.HALF_UP);
        if (recibidoNormalizado.compareTo(totalExigible) < 0) {
            throw new BusinessException("El efectivo recibido no cubre el total", HttpStatus.BAD_REQUEST);
        }
        return recibidoNormalizado;
    }

    private BigDecimal aplicarRedondeoSegunMetodo(BigDecimal total,
                                                  ComprobanteEntity.MetodoPago metodoPago) {
        if (metodoPago != ComprobanteEntity.MetodoPago.EFECTIVO) {
            return total;
        }
        return total
                .movePointRight(1)
                .setScale(0, RoundingMode.DOWN)
                .movePointLeft(1)
                .setScale(2, RoundingMode.UNNECESSARY);
    }

    private String nombreTipoComprobante(String tipo) {
        return switch (tipo) {
            case "B" -> "Boleta";
            case "F" -> "Factura";
            default -> "Ticket";
        };
    }

    private boolean tieneDescuento(BigDecimal descuento) {
        return descuento != null && descuento.signum() > 0;
    }

    private boolean soloDigitos(String value, int length) {
        return value != null && value.matches("\\d{" + length + "}");
    }

    private boolean estaVacio(String value) {
        return value == null || value.isBlank();
    }

    private boolean textoValido(String value, int min, int max) {
        if (estaVacio(value)) return false;
        String limpio = value.trim().replaceAll("\\s+", " ");
        if (limpio.length() < min || limpio.length() > max) return false;
        return limpio.matches("[A-Za-zÁÉÍÓÚÜÑáéíóúüñ0-9 .,'#\\-/]+");
    }

    private boolean textoNombreBoletaValido(String value) {
        if (estaVacio(value)) return false;
        String limpio = value.trim().replaceAll("\\s+", " ");
        if (limpio.length() < 3 || limpio.length() > 120) return false;
        return limpio.matches("[A-Za-zÁÉÍÓÚÜÑáéíóúüñ ]+");
    }

    private String limpiar(String value) {
        return value == null ? null : value.trim();
    }

    private String resumirRequest(EmitirComprobanteRequest request) {
        if (request == null) return "request=null";
        return "pedidoId=" + request.pedidoId()
                + ", tipo=" + limpiar(request.tipoComprobanteId())
                + ", metodo=" + limpiar(request.metodoPago())
                + ", descuento=" + request.descuento()
                + ", doc=" + (request.datosComprobante() != null ? limpiar(request.datosComprobante().rucDni()) : null);
    }

    private void programarLiberacionMesa(Long sesionMesaId, Long pedidoId) {
        Runnable liberarMesa = () -> {
            try {
                mesaService.cerrarSesion(sesionMesaId);
            } catch (RuntimeException ex) {
                log.warn("El comprobante del pedido {} se emitio, pero no se pudo liberar la sesion de mesa {}: {}",
                        pedidoId, sesionMesaId, ex.getMessage(), ex);
            }
        };

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    liberarMesa.run();
                }
            });
            return;
        }

        liberarMesa.run();
    }
}
