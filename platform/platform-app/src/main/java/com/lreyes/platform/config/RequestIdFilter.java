package com.lreyes.platform.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Filtro que genera un ID de correlación (requestId) para cada request HTTP.
 * <p>
 * Flujo:
 * <ol>
 *   <li>Si el request trae header {@code X-Request-Id}, lo usa</li>
 *   <li>Si no, genera un UUID corto</li>
 *   <li>Lo agrega al MDC de SLF4J (para que aparezca en los logs)</li>
 *   <li>Lo devuelve como header {@code X-Request-Id} en la respuesta</li>
 * </ol>
 * Se registra con prioridad alta para ejecutarse antes que otros filtros.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

    private static final String HEADER = "X-Request-Id";
    private static final String MDC_KEY = "requestId";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String requestId = request.getHeader(HEADER);
        if (requestId == null || requestId.isBlank()) {
            requestId = UUID.randomUUID().toString().substring(0, 8);
        }

        MDC.put(MDC_KEY, requestId);
        response.setHeader(HEADER, requestId);

        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        // No filtrar recursos estáticos de ZK ni webjars
        return path.startsWith("/zkau/") || path.startsWith("/webjars/");
    }
}
