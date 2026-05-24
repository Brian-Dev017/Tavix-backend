package com.restaurante.shared.audit;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditoriaValidacionService {
    private static final org.slf4j.Logger log =
            org.slf4j.LoggerFactory.getLogger(AuditoriaValidacionService.class);

    private final AuditoriaValidacionJpaRepo auditoriaRepo;

    public AuditoriaValidacionService(AuditoriaValidacionJpaRepo auditoriaRepo) {
        this.auditoriaRepo = auditoriaRepo;
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void registrar(String modulo, String accion, Long usuarioId, String rolId,
                          Long referenciaId, String resultado, String detalle, String datos) {
        try {
            AuditoriaValidacionEntity entity = new AuditoriaValidacionEntity();
            entity.setModulo(safe(modulo, 50));
            entity.setAccion(safe(accion, 80));
            entity.setUsuarioId(usuarioId);
            entity.setRolId(safe(rolId, 2));
            entity.setReferenciaId(referenciaId);
            entity.setResultado(safe(resultado, 10));
            entity.setDetalle(safe(detalle, 255));
            entity.setDatos(datos == null ? null : datos.trim());
            auditoriaRepo.save(entity);
        } catch (Exception ex) {
            // La auditoria no debe romper el flujo principal.
            log.warn("No se pudo registrar auditoria: {}", ex.getMessage());
        }
    }

    private String safe(String value, int max) {
        if (value == null) return null;
        String clean = value.trim();
        return clean.length() <= max ? clean : clean.substring(0, max);
    }
}
