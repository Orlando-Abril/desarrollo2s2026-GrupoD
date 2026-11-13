package com.example.demo.audit;

import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Component;
import org.springframework.validation.Errors;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.Writer;
import java.security.Principal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Arma la representación de los parámetros de una invocación para la auditoría de web services (spec 034):
 * omite objetos técnicos del contenedor y de seguridad, enmascara contraseñas y tokens a cualquier profundidad y
 * trunca cada parámetro a {@value #MAX_VALUE_LENGTH} caracteres. Nunca lanza.
 */
@Component
public class ParameterSanitizer {

    static final int MAX_VALUE_LENGTH = 200;
    static final String TRUNCATED_MARK = "…(truncated)";
    static final String MASK = "***";
    static final String UNAVAILABLE = "<unavailable>";

    private static final List<Class<?>> EXCLUDED_TYPES = List.of(
            ServletRequest.class, ServletResponse.class, HttpSession.class, Errors.class, Principal.class,
            WebRequest.class, MultipartFile.class, InputStream.class, OutputStream.class, Reader.class, Writer.class);

    private final ObjectMapper objectMapper;

    public ParameterSanitizer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /** Devuelve un objeto JSON compacto {@code {nombre: valor}} en el orden de la firma del método. */
    public String sanitize(String[] names, Object[] args) {
        try {
            ObjectNode params = objectMapper.createObjectNode();
            if (args == null) {
                return params.toString();
            }
            for (int i = 0; i < args.length; i++) {
                Object arg = args[i];
                if (isExcluded(arg)) {
                    continue;
                }
                String name = names != null && names.length == args.length ? names[i] : "arg" + i;
                put(params, name, arg);
            }
            return params.toString();
        } catch (RuntimeException ex) {
            return UNAVAILABLE;
        }
    }

    private void put(ObjectNode params, String name, Object arg) {
        if (isSensitive(name)) {
            params.put(name, MASK);
            return;
        }
        if (arg == null) {
            params.putNull(name);
            return;
        }
        JsonNode value;
        try {
            value = objectMapper.valueToTree(arg);
        } catch (RuntimeException ex) {
            params.put(name, "<unserializable:" + arg.getClass().getSimpleName() + ">");
            return;
        }
        mask(value);
        String json = value.toString();
        if (json.length() > MAX_VALUE_LENGTH) {
            params.put(name, json.substring(0, MAX_VALUE_LENGTH) + TRUNCATED_MARK);
        } else {
            params.set(name, value);
        }
    }

    private static void mask(JsonNode node) {
        if (node instanceof ObjectNode object) {
            List<String> sensitive = new ArrayList<>();
            for (Map.Entry<String, JsonNode> property : object.properties()) {
                if (isSensitive(property.getKey())) {
                    sensitive.add(property.getKey());
                } else {
                    mask(property.getValue());
                }
            }
            sensitive.forEach(key -> object.put(key, MASK));
        } else if (node instanceof ArrayNode array) {
            for (int i = 0; i < array.size(); i++) {
                mask(array.get(i));
            }
        }
    }

    private static boolean isSensitive(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        return lower.contains("password") || lower.contains("token");
    }

    private static boolean isExcluded(Object arg) {
        return arg != null && EXCLUDED_TYPES.stream().anyMatch(type -> type.isInstance(arg));
    }
}
