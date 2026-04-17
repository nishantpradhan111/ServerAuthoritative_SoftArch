package com.codereboot.gameboot.api;

import com.codereboot.gameboot.api.dto.SystemStatusResponse;
import com.codereboot.gameboot.security.RequestIdResolver;
import jakarta.servlet.http.HttpServletRequest;
import java.lang.management.ManagementFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/system")
public class SystemController {

    @GetMapping("/health")
    public SystemStatusResponse health(HttpServletRequest request) {
        return new SystemStatusResponse(
                "UP",
                "CodeReboot",
                System.getProperty("java.version"),
                ManagementFactory.getRuntimeMXBean().getUptime(),
                RequestIdResolver.resolve(request)
        );
    }
}