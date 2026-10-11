package com.lumina.domain;

import com.lumina.domain.Enums.ReportStatus;
import com.lumina.domain.Enums.ReportTarget;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@Document("reports")
public class Report {
    @Id
    private String id;
    private String reporterId;
    private String reporterName;
    private ReportTarget targetType;
    private String targetId;
    private String courseId;
    private String reason;
    private String description;
    private ReportStatus status = ReportStatus.OPEN;
    private AiAssessment aiAssessment;
    private String resolution;
    private String resolvedBy;
    private Instant resolvedAt;
    private Instant createdAt;
    private boolean seedData;

    @Getter
    @Setter
    @NoArgsConstructor
    public static class AiAssessment {
        private String riskCategory;
        private String severity;
        private String explanation;
        private String model;
        private Instant assessedAt;
        private boolean humanReviewRequired = true;
    }
}
