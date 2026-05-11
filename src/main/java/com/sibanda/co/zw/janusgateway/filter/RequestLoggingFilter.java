package com.sibanda.co.zw.janusgateway.filter;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class RequestLoggingFilter implements Filter {

    private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        long startTime = System.currentTimeMillis();

        log.info("→ REQUEST {} {} from {}",
                httpRequest.getMethod(),
                httpRequest.getRequestURI(),
                request.getRemoteAddr());

        try {
            chain.doFilter(request, response);
        } finally {
            long duration = System.currentTimeMillis() - startTime;
            int status = 0;
            try {
                status = ((jakarta.servlet.http.HttpServletResponse) response).getStatus();
            } catch (Exception ignored) {}

            if (status >= 400) {
                log.warn("← RESPONSE {} {} ({}ms)", status, httpRequest.getRequestURI(), duration);
            } else {
                log.info("← RESPONSE {} {} ({}ms)", status, httpRequest.getRequestURI(), duration);
            }
        }
    }
}