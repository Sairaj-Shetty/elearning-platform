package com.lumina.config;

import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import java.util.Arrays;

@Configuration
public class ProductionGuard {
    static final String DEV_SECRET = "dev-only-change-me-please-use-32chars-min";

    @Bean
    ApplicationRunner refuseUnsafeProduction(Environment environment, AppProperties props) {
        return args -> {
            boolean production = Arrays.asList(environment.getActiveProfiles()).contains("prod");
            if (!production) {
                return;
            }
            if (props.seed().enabled()) {
                throw new IllegalStateException("SEED_ENABLED must be false when the prod profile is active.");
            }
            String secret = props.jwt().secret() == null ? "" : props.jwt().secret();
            if (secret.length() < 32 || secret.equals(DEV_SECRET) || secret.contains("change-me")) {
                throw new IllegalStateException("Set a unique JWT_SECRET of at least 32 characters before using the prod profile.");
            }
        };
    }
}
