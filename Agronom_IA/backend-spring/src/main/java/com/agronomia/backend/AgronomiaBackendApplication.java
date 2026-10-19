package com.agronomia.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class AgronomiaBackendApplication {
    public static void main(String[] args) {
        SpringApplication.run(AgronomiaBackendApplication.class, args);
    }
}
