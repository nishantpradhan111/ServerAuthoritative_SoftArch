package com.codereboot.gameboot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class GameBootApplication {

    public static void main(String[] args) {
        SpringApplication.run(GameBootApplication.class, args);
    }
}