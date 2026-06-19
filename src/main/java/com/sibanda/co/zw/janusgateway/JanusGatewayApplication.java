package com.sibanda.co.zw.janusgateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class JanusGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(JanusGatewayApplication.class, args);
    }
}