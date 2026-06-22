package com.brokerdealer.apigateway.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequest;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.util.List;

/**
 * Servlet filter that proxies SSE streams from notification-service to the browser.
 *
 * This filter intercepts GET /api/v1/notifications/stream AFTER JwtValidationFilter
 * has validated the JWT, and BEFORE the DispatcherServlet / Spring Cloud Gateway MVC
 * handler mappings. By operating at the raw servlet layer, it avoids the response
 * buffering that Spring Cloud Gateway MVC 4.1.x applies to all proxied responses.
 *
 * Filter execution order:
 *   -100  Spring Security FilterChainProxy (contains JwtValidationFilter)
 *     ↓   JWT validated here or 401 returned immediately
 *  MAX-1  SseProxyFilter (Ordered.LOWEST_PRECEDENCE - 1)
 *     ↓   /stream path handled here — DispatcherServlet never reached
 *  MAX    DispatcherServlet (all other paths)
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE - 1)
@Slf4j
public class SseProxyFilter extends OncePerRequestFilter {

    private static final String SSE_PATH = "/api/v1/notifications/stream";
    private static final int BUFFER_SIZE = 4096;

    private final ClientHttpRequestFactory requestFactory;
    private final String notificationServiceUrl;

    public SseProxyFilter(
            ClientHttpRequestFactory sseRequestFactory,
            @Value("${notification.service.url:http://notification-service:8088}") String notificationServiceUrl) {
        this.requestFactory = sseRequestFactory;
        this.notificationServiceUrl = notificationServiceUrl;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        if (!SSE_PATH.equals(request.getRequestURI()) || !"GET".equals(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }

        // JWT has already been validated by JwtValidationFilter upstream.
        // If the token was missing or invalid, JwtValidationFilter returned 401 and
        // short-circuited the chain — so we never reach this point with a bad token.
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authorization == null) {
            filterChain.doFilter(request, response);
            return;
        }

        String upstreamUrl = notificationServiceUrl + "/api/v1/notifications/stream";
        log.debug("SseProxyFilter: opening upstream SSE connection to {}", upstreamUrl);

        ClientHttpRequest upstreamRequest = requestFactory.createRequest(
                URI.create(upstreamUrl), HttpMethod.GET);
        upstreamRequest.getHeaders().set(HttpHeaders.AUTHORIZATION, authorization);
        upstreamRequest.getHeaders().setAccept(List.of(MediaType.TEXT_EVENT_STREAM));

        try (ClientHttpResponse upstreamResponse = upstreamRequest.execute();
             InputStream inputStream = upstreamResponse.getBody()) {

            response.setStatus(HttpServletResponse.SC_OK);
            response.setContentType(MediaType.TEXT_EVENT_STREAM_VALUE);
            response.setHeader(HttpHeaders.CACHE_CONTROL, "no-cache");

            OutputStream outputStream = response.getOutputStream();
            outputStream.flush(); // commit headers immediately

            byte[] buffer = new byte[BUFFER_SIZE];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
                outputStream.flush();
            }
            log.debug("SseProxyFilter: upstream SSE stream completed");

        } catch (IOException e) {
            if (!response.isCommitted()) {
                response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            }
            log.warn("SseProxyFilter: SSE stream ended: {}", e.getMessage());
        }
    }
}
