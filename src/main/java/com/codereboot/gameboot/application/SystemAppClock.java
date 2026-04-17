package com.codereboot.gameboot.application;

import org.springframework.stereotype.Component;

@Component
public class SystemAppClock implements AppClock {

    @Override
    public long nowMillis() {
        return System.currentTimeMillis();
    }
}