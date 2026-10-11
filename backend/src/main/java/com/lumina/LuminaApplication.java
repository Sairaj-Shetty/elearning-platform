package com.lumina;

import com.lumina.config.AppProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(AppProperties.class)
public class LuminaApplication {
    public static void main(String[] args) {
        SpringApplication.run(LuminaApplication.class, args);
    }
}
