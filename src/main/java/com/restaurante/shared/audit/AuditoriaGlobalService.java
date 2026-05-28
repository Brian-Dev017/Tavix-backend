package com.restaurante.shared.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditoriaGlobalService {

    private final AuditoriaGlobalJpaRepo auditoriaGlobalJpaRepo;
    private final AuditoriaPayloadSanitizer auditoriaPayloadSanitizer;
    private final ObjectMapper objectMapper;

    public AuditoriaGlobalService(AuditoriaGlobalJpaRepo auditoriaGlobalJpaRepo,
                                  AuditoriaPayloadSanitizer auditoriaPayloadSanitizer,
                                  ObjectMapper objectMapper) {
        this.auditoriaGlobalJpaRepo = auditoriaGlobalJpaRepo;
        this.auditoriaPayloadSanitizer = auditoriaPayloadSanitizer;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public AuditoriaGlobalEntity registrar(String modulo,
                                           String tablaNombre,
                                           String registroId,
                                           String accion,
                                           String descripcion,
                                           Object valorAnterior,
                                           Object valorNuevo,
                                           AuditoriaContexto contexto) {
        AuditoriaGlobalEntity entity = new AuditoriaGlobalEntity();
        entity.setModulo(modulo);
        entity.setTablaNombre(tablaNombre);
        entity.setRegistroId(registroId);
        entity.setAccion(accion);
        entity.setDescripcion(descripcion);
        entity.setValorAnterior(serializarSeguro(valorAnterior));
        entity.setValorNuevo(serializarSeguro(valorNuevo));

        if (contexto != null) {
            entity.setUsuarioId(contexto.usuarioId());
            entity.setUsuarioLogin(contexto.usuarioLogin());
            entity.setRolId(contexto.rolId());
            entity.setIpOrigen(contexto.ipOrigen());
            entity.setEndpoint(contexto.endpoint());
        }

        return auditoriaGlobalJpaRepo.save(entity);
    }

    private String serializarSeguro(Object payload) {
        if (payload == null) {
            return null;
        }

        JsonNode sanitized = auditoriaPayloadSanitizer.sanitize(payload);
        try {
            return objectMapper.writeValueAsString(sanitized);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("No se pudo serializar el payload de auditoria de forma segura", ex);
        }
    }
}
