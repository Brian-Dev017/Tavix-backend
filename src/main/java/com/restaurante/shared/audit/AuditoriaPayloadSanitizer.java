package com.restaurante.shared.audit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.Set;

@Service
public class AuditoriaPayloadSanitizer {

    private static final String VALOR_ENMASCARADO = "***";
    private static final Set<String> CAMPOS_SENSIBLES = Set.of(
            "contrasena",
            "contrasenahash",
            "password",
            "token",
            "refreshtoken",
            "accesstoken"
    );

    private final ObjectMapper objectMapper;

    public AuditoriaPayloadSanitizer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public JsonNode sanitize(Object payload) {
        if (payload == null) {
            return null;
        }

        JsonNode root = objectMapper.valueToTree(payload);
        return sanitizeNode(root);
    }

    private JsonNode sanitizeNode(JsonNode node) {
        if (node == null || node.isNull()) {
            return node;
        }

        if (node.isObject()) {
            ObjectNode sanitized = JsonNodeFactory.instance.objectNode();
            node.fields().forEachRemaining(entry -> {
                if (esCampoSensible(entry.getKey())) {
                    sanitized.put(entry.getKey(), VALOR_ENMASCARADO);
                    return;
                }
                sanitized.set(entry.getKey(), sanitizeNode(entry.getValue()));
            });
            return sanitized;
        }

        if (node.isArray()) {
            ArrayNode sanitized = JsonNodeFactory.instance.arrayNode();
            for (JsonNode item : node) {
                sanitized.add(sanitizeNode(item));
            }
            return sanitized;
        }

        return node.deepCopy();
    }

    private boolean esCampoSensible(String nombreCampo) {
        return nombreCampo != null
                && CAMPOS_SENSIBLES.contains(nombreCampo.trim().toLowerCase(Locale.ROOT));
    }
}
