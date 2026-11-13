package com.example.demo.audit;

import org.springframework.boot.test.system.CapturedOutput;

import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lee de la consola capturada las líneas del logger de auditoría escritas después de una marca. Extrae los campos
 * del patrón JSON de consola por posición (no con un parser JSON) porque el patrón actual no escapa las comillas
 * del mensaje; si el mensaje viene escapado, se des-escapa igual.
 */
final class AuditLogLines {

    private static final Pattern LINE = Pattern.compile(
            "^\\{\"timestamp\":\"([^\"]*)\",\"level\":\"([^\"]*)\",\"logger\":\"" + WebServiceAuditAspect.LOGGER_NAME
                    + "\",\"correlationId\":\"([^\"]*)\",\"message\":\"(.*)\"}$");

    record Line(String timestamp, String level, String correlationId, String message) {
    }

    private AuditLogLines() {
    }

    static int mark(CapturedOutput output) {
        return output.getOut().length();
    }

    /** Líneas de {@code WEB_SERVICE_AUDIT} escritas después de la marca. */
    static List<Line> since(CapturedOutput output, int mark) {
        return output.getOut().substring(mark).lines()
                .map(AuditLogLines::parse)
                .flatMap(Optional::stream)
                .toList();
    }

    /** Mensajes {@code ws_audit ...} (sin los {@code ws_audit_failed}) escritos después de la marca. */
    static List<String> auditMessages(CapturedOutput output, int mark) {
        return since(output, mark).stream()
                .map(Line::message)
                .filter(message -> message.startsWith("ws_audit "))
                .toList();
    }

    static Optional<Line> parse(String raw) {
        Matcher matcher = LINE.matcher(raw);
        if (!matcher.matches()) {
            return Optional.empty();
        }
        return Optional.of(new Line(matcher.group(1), matcher.group(2), matcher.group(3),
                matcher.group(4).replace("\\\"", "\"")));
    }
}
