package com.lumina.web;

import com.lumina.api.Views.HealthView;
import com.lumina.api.Views.PublicConfig;
import com.lumina.common.ApiResponse;
import com.lumina.config.AppProperties;
import com.lumina.config.MongoCapabilities;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Platform")
public class PlatformController {
    private final MongoTemplate mongo;
    private final AppProperties props;
    private final MongoCapabilities capabilities;

    public PlatformController(MongoTemplate mongo, AppProperties props, MongoCapabilities capabilities) {
        this.mongo = mongo;
        this.props = props;
        this.capabilities = capabilities;
    }

    @GetMapping("/health")
    @Operation(summary = "Check the API and MongoDB connection")
    public ApiResponse<HealthView> health() {
        String database = "unknown";
        String mongoStatus = "DOWN";
        try {
            var result = mongo.executeCommand("{ ping: 1 }");
            Object ok = result.get("ok");
            if (ok instanceof Number number && number.doubleValue() == 1.0) mongoStatus = "UP";
            database = mongo.getDb().getName();
        } catch (Exception ignored) {
            mongoStatus = "DOWN";
        }
        return ApiResponse.ok(new HealthView("UP".equals(mongoStatus) ? "UP" : "DEGRADED", mongoStatus, database));
    }

    @GetMapping("/config/public")
    @Operation(summary = "Public flags for the UI, with no secrets")
    public ApiResponse<PublicConfig> config() {
        boolean ai = props.gemini().apiKey() != null && !props.gemini().apiKey().isBlank();
        return ApiResponse.ok(new PublicConfig(props.seed().enabled(), ai, "Lumina", capabilities.replicaSet()));
    }
}
