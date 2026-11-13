package com.example.demo.audit;

import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Auditoría de web services (spec 034): cada invocación a un método de un {@code @RestController} del paquete
 * {@code controller} deja exactamente una línea en el logger {@value #LOGGER_NAME}, también cuando termina en
 * excepción. Va primero en la cadena de advisors para envolver también a {@code @PreAuthorize} y a la validación
 * de métodos de los controllers {@code @Validated}. La auditoría nunca altera la respuesta ni la excepción.
 */
@Aspect
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@ConditionalOnProperty(name = "audit.web-services.enabled", havingValue = "true", matchIfMissing = true)
public class WebServiceAuditAspect {

    static final String LOGGER_NAME = "WEB_SERVICE_AUDIT";
    static final String OK = "OK";

    private static final Logger AUDIT = LoggerFactory.getLogger(LOGGER_NAME);
    private static final String ANONYMOUS = "anonymous";
    private static final String UNKNOWN = "-";

    private final ParameterSanitizer sanitizer;

    public WebServiceAuditAspect(ParameterSanitizer sanitizer) {
        this.sanitizer = sanitizer;
    }

    @Around("within(com.example.demo.controller..*) "
            + "&& @within(org.springframework.web.bind.annotation.RestController)")
    public Object audit(ProceedingJoinPoint joinPoint) throws Throwable {
        long start = System.nanoTime();
        String outcome = OK;
        try {
            return joinPoint.proceed();
        } catch (Throwable ex) {
            outcome = ex.getClass().getSimpleName();
            throw ex;
        } finally {
            log(joinPoint, outcome, (System.nanoTime() - start) / 1_000_000);
        }
    }

    private void log(ProceedingJoinPoint joinPoint, String outcome, long durationMs) {
        try {
            if (!AUDIT.isInfoEnabled()) {
                return;
            }
            MethodSignature signature = (MethodSignature) joinPoint.getSignature();
            HttpServletRequest request = currentRequest();
            AUDIT.info("ws_audit user={} method={} path={} operation={} params={} durationMs={} outcome={}",
                    currentUser(),
                    request != null ? request.getMethod() : UNKNOWN,
                    request != null ? request.getRequestURI() : UNKNOWN,
                    signature.toShortString(),
                    sanitizer.sanitize(signature.getParameterNames(), joinPoint.getArgs()),
                    durationMs,
                    outcome);
        } catch (RuntimeException ex) {
            AUDIT.warn("ws_audit_failed operation={} error={}", safeOperation(joinPoint), ex.getClass().getSimpleName());
        }
    }

    private static String currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return ANONYMOUS;
        }
        return authentication.getName();
    }

    private static HttpServletRequest currentRequest() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        return attributes instanceof ServletRequestAttributes servletAttributes ? servletAttributes.getRequest() : null;
    }

    private static String safeOperation(ProceedingJoinPoint joinPoint) {
        try {
            return joinPoint.getSignature().toShortString();
        } catch (RuntimeException ex) {
            return UNKNOWN;
        }
    }
}
