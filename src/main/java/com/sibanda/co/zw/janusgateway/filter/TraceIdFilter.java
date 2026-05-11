package com.sibanda.co.zw.janusgateway.filter;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.UUID;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdFilter implements Filter {

    public static final String TRACE_ID_HEADER = "X-Request-ID";
    public static final String TRACE_ID_MDC_KEY = "traceId";
    public static final String CLIENT_ID_MDC_KEY = "clientId";
    public static final String PLAN_MDC_KEY = "plan";

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        // Extract or generate trace ID
        String traceId = httpRequest.getHeader(TRACE_ID_HEADER);
        if (traceId == null || traceId.isBlank()) {
            traceId = UUID.randomUUID().toString();
        }

        // Set MDC context for all logging in this request
        MDC.put(TRACE_ID_MDC_KEY, traceId);
        MDC.put("requestMethod", httpRequest.getMethod());
        MDC.put("requestUri", httpRequest.getRequestURI());

        try {
            // Echo trace ID back to caller
            httpResponse.setHeader(TRACE_ID_HEADER, traceId);
            chain.doFilter(request, response);
        } finally {
            // Set response code before clearing
            MDC.put("responseCode", String.valueOf(httpResponse.getStatus()));
            // Clean up MDC to prevent memory leaks
            MDC.clear();
        }
    }
}