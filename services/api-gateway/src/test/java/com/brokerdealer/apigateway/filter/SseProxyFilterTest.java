package com.brokerdealer.apigateway.filter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequest;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SseProxyFilterTest {

    private static final String NOTIFICATION_SERVICE_URL = "http://localhost:8088";
    private static final String STREAM_URI = NOTIFICATION_SERVICE_URL + "/api/v1/notifications/stream";

    @Mock
    private ClientHttpRequestFactory sseRequestFactory;

    @Mock
    private ClientHttpRequest mockRequest;

    @Mock
    private ClientHttpResponse mockResponse;

    private SseProxyFilter filter;

    @BeforeEach
    void setUp() {
        filter = new SseProxyFilter(sseRequestFactory, NOTIFICATION_SERVICE_URL);
    }

    @Test
    void shouldReturn200WithTextEventStreamWhenUpstreamConnects() throws Exception {
        HttpHeaders requestHeaders = new HttpHeaders();
        when(sseRequestFactory.createRequest(URI.create(STREAM_URI), HttpMethod.GET))
                .thenReturn(mockRequest);
        when(mockRequest.getHeaders()).thenReturn(requestHeaders);
        when(mockRequest.execute()).thenReturn(mockResponse);
        when(mockResponse.getBody())
                .thenReturn(new ByteArrayInputStream("event:connected\ndata:{}\n\n".getBytes()));

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/notifications/stream");
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer valid-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilterInternal(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getContentType()).isEqualTo(MediaType.TEXT_EVENT_STREAM_VALUE);
        assertThat(chain.getRequest()).isNull(); // chain not called — filter handled it
    }

    @Test
    void shouldForwardAuthorizationHeaderToUpstream() throws Exception {
        HttpHeaders requestHeaders = new HttpHeaders();
        when(sseRequestFactory.createRequest(any(URI.class), eq(HttpMethod.GET)))
                .thenReturn(mockRequest);
        when(mockRequest.getHeaders()).thenReturn(requestHeaders);
        when(mockRequest.execute()).thenReturn(mockResponse);
        when(mockResponse.getBody()).thenReturn(new ByteArrayInputStream(new byte[0]));

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/notifications/stream");
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer my-jwt-token-123");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, new MockFilterChain());

        assertThat(requestHeaders.getFirst(HttpHeaders.AUTHORIZATION)).isEqualTo("Bearer my-jwt-token-123");
    }

    @Test
    void shouldReturn503WhenUpstreamUnreachable() throws Exception {
        HttpHeaders requestHeaders = new HttpHeaders();
        when(sseRequestFactory.createRequest(any(URI.class), eq(HttpMethod.GET)))
                .thenReturn(mockRequest);
        when(mockRequest.getHeaders()).thenReturn(requestHeaders);
        when(mockRequest.execute()).thenThrow(new IOException("Connection refused"));

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/notifications/stream");
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer valid-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(503);
    }

    @Test
    void shouldPassThroughNonStreamPaths() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/orders");
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilterInternal(request, response, chain);

        assertThat(chain.getRequest()).isNotNull(); // chain WAS called
        verify(sseRequestFactory, never()).createRequest(any(), any());
    }

    @Test
    void shouldPassThroughWhenAuthorizationHeaderMissing() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/notifications/stream");
        // No Authorization header — JwtValidationFilter already handled 401
        // but we still pass through here to be safe
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilterInternal(request, response, chain);

        assertThat(chain.getRequest()).isNotNull();
        verify(sseRequestFactory, never()).createRequest(any(), any());
    }
}
