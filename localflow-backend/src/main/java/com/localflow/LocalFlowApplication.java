package com.localflow;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class LocalFlowApplication {
    public static void main(String[] args) {
        SpringApplication.run(LocalFlowApplication.class, args);
    }
}
