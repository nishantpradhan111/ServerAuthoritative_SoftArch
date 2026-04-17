package com.codereboot.gameboot.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.servlet.FilterChain;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RequestIdFilterTest {

    private final RequestIdFilter filter = new RequestIdFilter();

    @Test
    void generatesRequestIdWhenHeaderMissing() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/system/health");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chainInvoked = new AtomicBoolean(false);

        filter.doFilter(request, response, (FilterChain) (servletRequest, servletResponse) -> chainInvoked.set(true));

        String requestId = response.getHeader(RequestIdFilter.HEADER_NAME);
        assertTrue(chainInvoked.get());
        assertNotNull(requestId);
        assertTrue(!requestId.isBlank());
        assertEquals(requestId, request.getAttribute(RequestIdFilter.REQUEST_ATTRIBUTE));
    }

    @Test
    void preservesIncomingRequestId() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/system/health");
        request.addHeader(RequestIdFilter.HEADER_NAME, "req-123");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (FilterChain) (servletRequest, servletResponse) -> {
        });

        assertEquals("req-123", response.getHeader(RequestIdFilter.HEADER_NAME));
        assertEquals("req-123", request.getAttribute(RequestIdFilter.REQUEST_ATTRIBUTE));
    }
}