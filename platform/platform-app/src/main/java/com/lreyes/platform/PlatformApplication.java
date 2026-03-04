package com.lreyes.platform;

import jakarta.servlet.http.HttpSessionEvent;
import jakarta.servlet.http.HttpSessionListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.servlet.ServletListenerRegistrationBean;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class PlatformApplication {

    private static final Logger log = LoggerFactory.getLogger(PlatformApplication.class);

    public static void main(String[] args) {
        SpringApplication.run(PlatformApplication.class, args);
    }

    @Bean
    ServletListenerRegistrationBean<HttpSessionListener> sessionDiagnostics() {
        HttpSessionListener listener = new HttpSessionListener() {
            @Override
            public void sessionCreated(HttpSessionEvent se) {
                log.info("=== SESSION CREATED: id={}, maxInactiveInterval={}s ({}min) ===",
                        se.getSession().getId(),
                        se.getSession().getMaxInactiveInterval(),
                        se.getSession().getMaxInactiveInterval() / 60);
            }

            @Override
            public void sessionDestroyed(HttpSessionEvent se) {
                log.info("=== SESSION DESTROYED: id={}, maxInactiveInterval={}s ===",
                        se.getSession().getId(),
                        se.getSession().getMaxInactiveInterval());
            }
        };
        return new ServletListenerRegistrationBean<>(listener);
    }
}
