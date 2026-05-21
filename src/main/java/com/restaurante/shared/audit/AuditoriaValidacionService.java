package com.restaurante.shared.audit;

import org.springframework.stereotype.Service;

@Service
public class AuditoriaValidacionService {

    private final AuditoriaValidacionJpaRepo auditoriaRepo;

    public AuditoriaValidacionService(AuditoriaValidacionJpaRepo auditoriaRepo) {
        this.auditoriaRepo = auditoriaRepo;
    }

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
        } catch (Exception ignored) {
            // La auditoria no debe romper el flujo principal.
        }
    }

    private String safe(String value, int max) {
        if (value == null) return null;
        String clean = value.trim();
        return clean.length() <= max ? clean : clean.substring(0, max);
    }
}
