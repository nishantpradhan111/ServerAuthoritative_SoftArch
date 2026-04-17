package com.codereboot.gameboot.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.util.StringUtils;

public final class RequestIdResolver {

    private RequestIdResolver() {
    }

    public static String resolve(HttpServletRequest request) {
        Object requestId = request.getAttribute(RequestIdFilter.REQUEST_ATTRIBUTE);
        if (requestId instanceof String requestIdValue && StringUtils.hasText(requestIdValue)) {
            return requestIdValue;
        }

        String headerValue = request.getHeader(RequestIdFilter.HEADER_NAME);
        if (!StringUtils.hasText(headerValue)) {
            return null;
        }

        return headerValue;
    }
}