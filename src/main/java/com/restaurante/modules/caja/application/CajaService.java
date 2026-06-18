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
import com.restaurante.modules.mesas.infrastructure.persistence.MesaEntity;
import com.restaurante.modules.mesas.infrastructure.persistence.MesaJpaRepo;
import com.restaurante.modules.mesas.infrastructure.persistence.SesionMesaJpaRepo;
import com.restaurante.modules.pedidos.infrastructure.persistence.DetallePedidoEntity;
import com.restaurante.modules.pedidos.infrastructure.persistence.DetallePedidoJpaRepo;
import com.restaurante.modules.pedidos.infrastructure.persistence.PedidoEntity;
import com.restaurante.modules.pedidos.infrastructure.persistence.PedidoJpaRepo;
import com.restaurante.modules.pedidos.infrastructure.web.dto.ItemCocinaDTO;
import com.restaurante.modules.pedidos.infrastructure.web.dto.ItemPedidoDTO;
import com.restaurante.modules.pedidos.infrastructure.ws.PedidoEventPublisher;
import com.restaurante.shared.audit.AuditoriaContexto;
import com.restaurante.shared.audit.AuditoriaGlobalService;
import com.restaurante.shared.exception.BusinessException;
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
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.Map;
import java.util.Set;

@Service
public class CajaService {

    private static final Set<String> TIPOS_COMPROBANTE = Set.of("T", "B", "F");
    private static final int TICKET_WIDTH = 42;
    private static final DateTimeFormatter TICKET_DATE_FORMAT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
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
    private final SesionMesaJpaRepo sesionRepo;
    private final MesaJpaRepo mesaRepo;
    private final PedidoEventPublisher eventPublisher;
    private final NegocioConfigJpaRepo negocioRepo;
    private final AuditoriaGlobalService auditoriaGlobalService;

    public CajaService(ComprobanteJpaRepo comprobanteRepo,
                       ArqueoJpaRepo arqueoRepo,
                       DatosComprobanteJpaRepo datosRepo,
                       PedidoJpaRepo pedidoRepo,
                       SerieComprobanteJpaRepo serieRepo,
                       DetallePedidoJpaRepo detalleRepo,
                       ProductoJpaRepo productoRepo,
                       MesaService mesaService,
                       SesionMesaJpaRepo sesionRepo,
                       MesaJpaRepo mesaRepo,
                       PedidoEventPublisher eventPublisher,
                       NegocioConfigJpaRepo negocioRepo,
                       AuditoriaGlobalService auditoriaGlobalService) {
        this.comprobanteRepo = comprobanteRepo;
        this.arqueoRepo = arqueoRepo;
        this.datosRepo = datosRepo;
        this.pedidoRepo = pedidoRepo;
        this.serieRepo = serieRepo;
        this.detalleRepo = detalleRepo;
        this.productoRepo = productoRepo;
        this.mesaService = mesaService;
        this.sesionRepo = sesionRepo;
        this.mesaRepo = mesaRepo;
        this.eventPublisher = eventPublisher;
        this.negocioRepo = negocioRepo;
        this.auditoriaGlobalService = auditoriaGlobalService;
    }

    @SuppressWarnings("unchecked")
    public List<PedidoResumenDTO> getPedidosActivos() {
        List<Object[]> rows = em.createNativeQuery(
                "SELECT v.pedido_id, v.mesa, v.mesero, v.total_items, v.subtotal, v.igv, " +
                "v.total_con_igv, v.estado_pedido, m.tipo " +
                "FROM v_consumo_por_pedido v " +
                "JOIN pedido p ON p.id = v.pedido_id " +
                "JOIN sesion_mesa sm ON sm.id = p.sesion_mesa_id " +
                "JOIN mesa m ON m.id = sm.mesa_id " +
                "WHERE v.estado_pedido IN ('ABIERTO','EN_COCINA','LISTO') " +
                "AND v.total_items > 0 " +
                "AND v.total_con_igv > 0 " +
                "AND NOT EXISTS (SELECT 1 FROM comprobante_venta cv WHERE cv.pedido_id = v.pedido_id) " +
                "ORDER BY v.pedido_id ASC"
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
                "PARA_LLEVAR".equals(String.valueOf(r[8])),
                cargarItems(((Number) r[0]).longValue())
        )).toList();
    }

    @Transactional
    public ComprobanteResponseDTO emitirComprobante(Long cajeroId, boolean puedeAplicarDescuento,
                                                    EmitirComprobanteRequest request,
                                                    AuditoriaContexto auditoriaContexto) {
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
        boolean pedidoParaLlevar = esPedidoParaLlevar(pedido);
        if (!pedidoParaLlevar && pedido.getEstado() != PedidoEntity.EstadoPedido.LISTO) {
            throw new BusinessException("Solo se puede cobrar un pedido LISTO", HttpStatus.CONFLICT);
        }
        if (pedidoParaLlevar && pedido.getEstado() != PedidoEntity.EstadoPedido.ABIERTO
                && pedido.getEstado() != PedidoEntity.EstadoPedido.EN_COCINA
                && pedido.getEstado() != PedidoEntity.EstadoPedido.LISTO) {
            throw new BusinessException("El pedido para llevar no esta disponible para cobro", HttpStatus.CONFLICT);
        }

        String tipo = normalizarTipo(request.tipoComprobanteId());
        validarDatosComprobante(tipo, request.datosComprobante());
        ComprobanteEntity.MetodoPago metodoPago = parseMetodoPago(request.metodoPago());
        ArqueoEntity arqueo = arqueoRepo
                .findTopByCajeroIdAndEstadoOrderByAperturaEnDesc(cajeroId, ArqueoEntity.EstadoArqueo.ABIERTO)
                .orElseThrow(() -> new BusinessException("Debes aperturar tu caja antes de cobrar",
                        HttpStatus.CONFLICT));

        Object[] t = cargarTotales(request.pedidoId());
        BigDecimal igvBruto = (BigDecimal) t[1];     // IGV ya extraido del total que incluye IGV
        BigDecimal totalBruto = (BigDecimal) t[2];   // bruto: el precio ya incluye IGV
        BigDecimal descuento = normalizarDescuento(request.descuento(), totalBruto, request.motivoDescuento());
        BigDecimal totalAntesRedondeo = totalBruto.subtract(descuento);
        BigDecimal total = aplicarRedondeoSegunMetodo(totalAntesRedondeo, metodoPago);
        // IGV proporcional al total final (ajusta por descuento y redondeo); base = total - IGV
        BigDecimal igv = igvProporcional(igvBruto, total, totalBruto);
        BigDecimal subtotal = total.subtract(igv);
        BigDecimal efectivoRecibido = normalizarEfectivoRecibido(metodoPago, request.efectivoRecibido(), total);
        BigDecimal vuelto = metodoPago == ComprobanteEntity.MetodoPago.EFECTIVO
                ? efectivoRecibido.subtract(total)
                : BigDecimal.ZERO;

        DatosComprobanteEntity datosComprobante = guardarDatosSiCorresponde(tipo, request.datosComprobante());
        Long datosId = datosComprobante != null ? datosComprobante.getId() : null;
        if (datosComprobante != null) {
            auditoriaGlobalService.registrar(
                    "caja",
                    "datos_comprobante",
                    datosComprobante.getId().toString(),
                    "CREAR",
                    "Se registraron los datos del comprobante para el pedido " + request.pedidoId(),
                    null,
                    snapshotDatosComprobante(datosComprobante),
                    auditoriaContexto
            );
        }

        SerieComprobanteEntity serie = serieRepo.findTopByTipoAndActivoTrueOrderByIdAsc(tipo)
                .orElseThrow(() -> new BusinessException("No hay serie activa para el comprobante " + tipo,
                        HttpStatus.CONFLICT));
        Map<String, Object> serieAntes = snapshotSerieComprobante(serie);
        int numero = serie.getCorrelativoActual();
        serie.setCorrelativoActual(numero + 1);
        serieRepo.save(serie);
        auditoriaGlobalService.registrar(
                "caja",
                "serie_comprobante",
                serie.getId().toString(),
                "ACTUALIZAR",
                "Se actualizo el correlativo de la serie " + serie.getSerie(),
                serieAntes,
                snapshotSerieComprobante(serie),
                auditoriaContexto
        );

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
        Map<String, Object> comprobanteAntes = snapshotComprobante(saved);

        saved.setEstado(ComprobanteEntity.EstadoComprobante.COMPLETADO);
        saved.setPagadoEn(LocalDateTime.now());
        saved.setActualizadoEn(LocalDateTime.now());
        saved = comprobanteRepo.saveAndFlush(saved);
        registrarAuditoriaComprobante(saved, comprobanteAntes, auditoriaContexto);

        PedidoEntity.EstadoPedido estadoPedidoAnterior = pedido.getEstado();
        pedido.setEstado(PedidoEntity.EstadoPedido.COBRADO);
        pedidoRepo.save(pedido);
        auditoriaGlobalService.registrar(
                "caja",
                "pedido",
                pedido.getId().toString(),
                "CAMBIO_ESTADO",
                "El pedido paso de " + estadoPedidoAnterior.name() + " a " + pedido.getEstado().name()
                        + " durante la emision del comprobante",
                snapshotPedidoEstado(pedido.getId(), estadoPedidoAnterior),
                snapshotPedidoEstado(pedido.getId(), pedido.getEstado()),
                auditoriaContexto
        );
        if (pedido.getSesionMesaId() != null) {
            if (pedidoParaLlevar) {
                programarEnvioCocinaParaLlevar(pedido);
            } else {
                programarLiberacionMesa(pedido.getSesionMesaId(), request.pedidoId());
            }
        }

        // Notificar la venta en tiempo real (dashboard/caja) tras confirmar la transacción
        Map<String, Object> ventaEvento = new LinkedHashMap<>();
        ventaEvento.put("comprobanteId", saved.getId());
        ventaEvento.put("serie", saved.getSerie());
        ventaEvento.put("numero", saved.getNumero());
        ventaEvento.put("total", total);
        ventaEvento.put("metodoPago", saved.getMetodoPago().name());
        ventaEvento.put("pagadoEn", saved.getPagadoEn());
        publicarTrasCommit(() -> eventPublisher.publicarVenta(ventaEvento));

        return new ComprobanteResponseDTO(
                saved.getId(), saved.getPedidoId(),
                saved.getTipoComprobanteId(), saved.getSerie(), saved.getNumero(),
                saved.getMetodoPago().name(), nombreTipoComprobante(saved.getTipoComprobanteId()),
                subtotal, igv, descuento, total,
                saved.getEfectivoRecibido(), saved.getVuelto(),
                saved.getEstado().name(), saved.getPagadoEn()
        );
    }

    /** Ejecuta la acción tras el commit (o de inmediato si no hay transacción activa). */
    private void publicarTrasCommit(Runnable accion) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    try { accion.run(); } catch (RuntimeException ex) {
                        log.warn("No se pudo publicar el evento de venta: {}", ex.getMessage());
                    }
                }
            });
        } else {
            try { accion.run(); } catch (RuntimeException ignored) {}
        }
    }

    public byte[] generarEscPos(Long comprobanteId) {
        ComprobanteEntity comp = comprobanteRepo.findById(comprobanteId)
                .orElseThrow(() -> new BusinessException("Comprobante no encontrado", HttpStatus.NOT_FOUND));
        NegocioConfigEntity negocio = negocioRepo.findById(1L).orElse(null);
        DatosComprobanteEntity datos = comp.getDatosComprobanteId() == null
                ? null
                : datosRepo.findById(comp.getDatosComprobanteId()).orElse(null);
        List<ItemPedidoDTO> items = cargarItems(comp.getPedidoId());
        List<String> lines = "T".equals(comp.getTipoComprobanteId())
                ? construirTicketVenta(comp, negocio, items)
                : construirBoletaFactura(comp, negocio, datos, items);
        String payload = "\u001B@" + String.join("\n", lines) + "\n\n\n\u001DV\u0001";
        return payload.getBytes(StandardCharsets.ISO_8859_1);
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

    private boolean esPedidoParaLlevar(PedidoEntity pedido) {
        if (pedido.getSesionMesaId() == null) return false;
        return sesionRepo.findById(pedido.getSesionMesaId())
                .flatMap(sesion -> mesaRepo.findById(sesion.getMesaId()))
                .map(MesaEntity::isParaLlevar)
                .orElse(false);
    }

    private DatosComprobanteEntity guardarDatosSiCorresponde(String tipo, DatosComprobanteRequest request) {
        if (!"B".equals(tipo) && !"F".equals(tipo)) return null;
        if (request == null) return null;

        DatosComprobanteEntity datos = new DatosComprobanteEntity();
        datos.setTipoComprobanteId(tipo);
        datos.setRucDni(limpiar(request.rucDni()));
        datos.setRazonSocial(limpiar(request.razonSocial()));
        datos.setDireccion(limpiar(request.direccion()));
        return datosRepo.save(datos);
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

    /**
     * IGV ya viene EXTRAIDO del total bruto (precio incluye IGV). Si hubo descuento o
     * redondeo, se ajusta proporcionalmente para mantener base + IGV = total.
     */
    private BigDecimal igvProporcional(BigDecimal igvBruto, BigDecimal total, BigDecimal totalBruto) {
        if (totalBruto == null || totalBruto.signum() == 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal igvBase = igvBruto == null ? BigDecimal.ZERO : igvBruto;
        return igvBase.multiply(total).divide(totalBruto, 2, RoundingMode.HALF_UP);
    }

    private String nombreTipoComprobante(String tipo) {
        return switch (tipo) {
            case "B" -> "Boleta";
            case "F" -> "Factura";
            default -> "Ticket";
        };
    }

    private List<String> construirTicketVenta(ComprobanteEntity comp,
                                              NegocioConfigEntity negocio,
                                              List<ItemPedidoDTO> items) {
        List<String> lines = new ArrayList<>();
        lines.add(center(nombreNegocio(negocio).toUpperCase()));
        Optional.ofNullable(rucNegocio(negocio)).ifPresent(ruc -> lines.add(center("RUC: " + ruc)));
        lines.add("");
        lines.add(center("Ticket de venta #" + comp.getNumero()));
        lines.add("");
        lines.add(center(formatFecha(comp.getPagadoEn())));
        lines.add(center("Lo atendio: Cajero #" + comp.getCajeroId()));
        lines.add(center("Cliente: Mostrador"));
        lines.add("");
        lines.add(center(repeat("-", 24)));
        agregarItemsTicket(lines, items);
        lines.add(center(repeat("-", 24)));
        lines.add(row("Total", money(comp.getTotal())));
        if (comp.getMetodoPago() == ComprobanteEntity.MetodoPago.EFECTIVO) {
            lines.add(row("Su pago", money(comp.getEfectivoRecibido())));
            lines.add(row("Cambio", money(comp.getVuelto())));
        } else {
            lines.add(row("Pago", comp.getMetodoPago().name()));
        }
        lines.add("");
        lines.add(center("Gracias por su visita"));
        return lines;
    }

    private List<String> construirBoletaFactura(ComprobanteEntity comp,
                                                NegocioConfigEntity negocio,
                                                DatosComprobanteEntity datos,
                                                List<ItemPedidoDTO> items) {
        List<String> lines = new ArrayList<>();
        lines.add(center(nombreNegocio(negocio).toUpperCase()));
        Optional.ofNullable(rucNegocio(negocio)).ifPresent(ruc -> lines.add(center("RUC: " + ruc)));
        Optional.ofNullable(direccionNegocio(negocio)).ifPresent(dir -> wrap(dir, TICKET_WIDTH).forEach(lines::add));
        lines.add(repeat("*", TICKET_WIDTH));
        lines.add(center(nombreTipoComprobante(comp.getTipoComprobanteId()).toUpperCase()));
        lines.add(center(comp.getSerie() + "-" + String.format("%08d", comp.getNumero())));
        lines.add(row("Fecha", formatFecha(comp.getPagadoEn())));
        lines.add(row("Pedido", "P-" + comp.getPedidoId()));
        lines.add(row("Cajero", "#" + comp.getCajeroId()));
        lines.add(repeat("-", TICKET_WIDTH));
        if (datos != null) {
            lines.add(row("Doc.", safe(datos.getRucDni())));
            wrap("Cliente: " + safe(datos.getRazonSocial()), TICKET_WIDTH).forEach(lines::add);
            if (!estaVacio(datos.getDireccion())) {
                wrap("Direccion: " + safe(datos.getDireccion()), TICKET_WIDTH).forEach(lines::add);
            }
            lines.add(repeat("-", TICKET_WIDTH));
        }
        lines.add(row("DESCRIPCION", "IMPORTE"));
        agregarItemsTicket(lines, items);
        lines.add(repeat("=", TICKET_WIDTH));
        lines.add(row("SUBTOTAL", money(comp.getSubtotal())));
        lines.add(row("IGV 18%", money(comp.getIgv())));
        if (comp.getDescuento() != null && comp.getDescuento().signum() > 0) {
            lines.add(row("DESCUENTO", "-" + money(comp.getDescuento())));
        }
        lines.add(row("TOTAL A PAGAR", money(comp.getTotal())));
        lines.add(repeat("=", TICKET_WIDTH));
        lines.add(row("METODO", comp.getMetodoPago().name()));
        if (comp.getMetodoPago() == ComprobanteEntity.MetodoPago.EFECTIVO) {
            lines.add(row("RECIBIDO", money(comp.getEfectivoRecibido())));
            lines.add(row("VUELTO", money(comp.getVuelto())));
        }
        lines.add("");
        lines.add(center("Representacion impresa"));
        lines.add(center("Gracias por su compra"));
        return lines;
    }

    private void agregarItemsTicket(List<String> lines, List<ItemPedidoDTO> items) {
        for (ItemPedidoDTO item : items) {
            wrap(item.productoNombre(), TICKET_WIDTH).forEach(lines::add);
            String cantidadPrecio = item.cantidad() + " x " + money(item.precio());
            lines.add(row(cantidadPrecio, money(item.subtotal())));
            lines.add(repeat("-", TICKET_WIDTH));
        }
    }

    private String nombreNegocio(NegocioConfigEntity negocio) {
        return negocio != null && !estaVacio(negocio.getNombreComercial())
                ? limpiar(negocio.getNombreComercial())
                : "LA FLOR DEL TUMBO";
    }

    private String rucNegocio(NegocioConfigEntity negocio) {
        return negocio != null && !estaVacio(negocio.getRucNegocio()) ? limpiar(negocio.getRucNegocio()) : null;
    }

    private String direccionNegocio(NegocioConfigEntity negocio) {
        return negocio != null && !estaVacio(negocio.getDireccion()) ? limpiar(negocio.getDireccion()) : null;
    }

    private String formatFecha(LocalDateTime value) {
        return value == null ? "--" : value.format(TICKET_DATE_FORMAT);
    }

    private String money(BigDecimal value) {
        return "S/ " + (value == null ? BigDecimal.ZERO : value).setScale(2, RoundingMode.HALF_UP);
    }

    private String row(String left, String right) {
        String l = safe(left);
        String r = safe(right);
        int available = Math.max(1, TICKET_WIDTH - r.length());
        if (l.length() > available) {
            l = l.substring(0, available);
        }
        return l + repeat(" ", Math.max(1, TICKET_WIDTH - l.length() - r.length())) + r;
    }

    private String center(String value) {
        String text = safe(value);
        if (text.length() >= TICKET_WIDTH) {
            return text.substring(0, TICKET_WIDTH);
        }
        int left = (TICKET_WIDTH - text.length()) / 2;
        return repeat(" ", left) + text;
    }

    private List<String> wrap(String value, int width) {
        String clean = safe(value);
        if (clean.isBlank()) {
            return List.of("");
        }
        List<String> lines = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String word : clean.split("\\s+")) {
            if (current.length() == 0) {
                current.append(word);
            } else if (current.length() + 1 + word.length() <= width) {
                current.append(' ').append(word);
            } else {
                lines.add(current.toString());
                current = new StringBuilder(word);
            }
        }
        if (current.length() > 0) {
            lines.add(current.toString());
        }
        return lines;
    }

    private String repeat(String value, int count) {
        return value.repeat(Math.max(0, count));
    }

    private String safe(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", " ");
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

    private void registrarAuditoriaComprobante(ComprobanteEntity comprobante,
                                               Map<String, Object> comprobanteAntes,
                                               AuditoriaContexto auditoriaContexto) {
        String accion = comprobante.getEstado() == ComprobanteEntity.EstadoComprobante.COMPLETADO
                ? "COMPLETAR"
                : "CREAR";
        Object valorAnterior = "COMPLETAR".equals(accion) ? comprobanteAntes : null;
        String descripcion = "COMPLETAR".equals(accion)
                ? "Se completo el comprobante del pedido " + comprobante.getPedidoId()
                : "Se creo el comprobante del pedido " + comprobante.getPedidoId();

        auditoriaGlobalService.registrar(
                "caja",
                "comprobante_venta",
                comprobante.getId().toString(),
                accion,
                descripcion,
                valorAnterior,
                snapshotComprobante(comprobante),
                auditoriaContexto
        );
    }

    private Map<String, Object> snapshotDatosComprobante(DatosComprobanteEntity datosComprobante) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("id", datosComprobante.getId());
        snapshot.put("tipoComprobanteId", datosComprobante.getTipoComprobanteId());
        snapshot.put("rucDni", datosComprobante.getRucDni());
        snapshot.put("razonSocial", datosComprobante.getRazonSocial());
        snapshot.put("direccion", datosComprobante.getDireccion());
        return snapshot;
    }

    private Map<String, Object> snapshotSerieComprobante(SerieComprobanteEntity serie) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("id", serie.getId());
        snapshot.put("tipo", serie.getTipo());
        snapshot.put("serie", serie.getSerie());
        snapshot.put("correlativoActual", serie.getCorrelativoActual());
        snapshot.put("activo", serie.isActivo());
        return snapshot;
    }

    private Map<String, Object> snapshotComprobante(ComprobanteEntity comprobante) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("id", comprobante.getId());
        snapshot.put("pedidoId", comprobante.getPedidoId());
        snapshot.put("cajeroId", comprobante.getCajeroId());
        snapshot.put("arqueoCajaId", comprobante.getArqueoCajaId());
        snapshot.put("tipoComprobanteId", comprobante.getTipoComprobanteId());
        snapshot.put("serie", comprobante.getSerie());
        snapshot.put("numero", comprobante.getNumero());
        snapshot.put("datosComprobanteId", comprobante.getDatosComprobanteId());
        snapshot.put("subtotal", comprobante.getSubtotal());
        snapshot.put("igv", comprobante.getIgv());
        snapshot.put("descuento", comprobante.getDescuento());
        snapshot.put("motivoDescuento", comprobante.getMotivoDescuento());
        snapshot.put("total", comprobante.getTotal());
        snapshot.put("metodoPago", comprobante.getMetodoPago() != null ? comprobante.getMetodoPago().name() : null);
        snapshot.put("efectivoRecibido", comprobante.getEfectivoRecibido());
        snapshot.put("vuelto", comprobante.getVuelto());
        snapshot.put("estado", comprobante.getEstado() != null ? comprobante.getEstado().name() : null);
        snapshot.put("pagadoEn", comprobante.getPagadoEn());
        return snapshot;
    }

    private Map<String, Object> snapshotPedidoEstado(Long pedidoId, PedidoEntity.EstadoPedido estado) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("id", pedidoId);
        snapshot.put("estado", estado != null ? estado.name() : null);
        return snapshot;
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

    private void programarEnvioCocinaParaLlevar(PedidoEntity pedido) {
        Runnable enviarCocina = () -> {
            detalleRepo.findByPedidoId(pedido.getId()).stream()
                    .filter(d -> d.getEstado() == DetallePedidoEntity.EstadoDetalle.PENDIENTE)
                    .forEach(d -> {
                        String productoNombre = productoRepo.findById(d.getProductoId())
                                .map(p -> p.getNombre())
                                .orElse("Desconocido");
                        eventPublisher.publicarNuevoItem(new ItemCocinaDTO(
                                d.getId(),
                                pedido.getId(),
                                numeroMesa(pedido),
                                productoNombre,
                                d.getCantidad(),
                                d.getObservaciones(),
                                d.getCreadoEn()
                        ));
                    });
            mesaService.cerrarSesion(pedido.getSesionMesaId());
        };

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    enviarCocina.run();
                }
            });
            return;
        }

        enviarCocina.run();
    }

    private String numeroMesa(PedidoEntity pedido) {
        if (pedido.getSesionMesaId() == null) return "?";
        return sesionRepo.findById(pedido.getSesionMesaId())
                .flatMap(sesion -> mesaRepo.findById(sesion.getMesaId()))
                .map(MesaEntity::getNumero)
                .orElse("?");
    }
}
