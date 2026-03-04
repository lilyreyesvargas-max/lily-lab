package com.lreyes.platform.config;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.EnumSet;

/**
 * Registra un filtro que intercepta requests /zul/** y las reenvía al
 * DHtmlUpdateServlet de ZK vía /zkau/web/zul/**.
 * <p>
 * En Spring Boot JAR packaging, ZK sirve páginas ZUL desde el classpath
 * a través del DHtmlUpdateServlet (mapping /zkau/*), no del DHtmlLayoutServlet
 * (que solo resuelve del filesystem docbase, inexistente en modo JAR).
 * <p>
 * El filtro tiene prioridad más alta que cualquier servlet, asegurando que
 * las requests a /zul/** se redirijan antes de que DHtmlLayoutServlet (*.zul)
 * las procese.
 */
@Configuration
public class ZulForwardController {

    @Bean
    FilterRegistrationBean<Filter> zulForwardFilter() {
        FilterRegistrationBean<Filter> reg = new FilterRegistrationBean<>();
        reg.setFilter(new OncePerRequestFilter() {
            @Override
            protected void doFilterInternal(HttpServletRequest request,
                                            HttpServletResponse response,
                                            FilterChain chain)
                    throws ServletException, IOException {
                String uri = request.getRequestURI();
                String zkPath = "/zkau/web" + uri;
                request.getRequestDispatcher(zkPath).forward(request, response);
            }
        });
        reg.addUrlPatterns("/zul/*");
        reg.setOrder(Ordered.HIGHEST_PRECEDENCE);
        reg.setDispatcherTypes(EnumSet.of(DispatcherType.REQUEST));
        return reg;
    }
}
