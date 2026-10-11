package com.lumina.config;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.IndexOptions;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.stereotype.Component;

@Component
@Order(1)
public class MongoSetup implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(MongoSetup.class);
    private final MongoTemplate mongo;
    private final MongoCapabilities capabilities;

    public MongoSetup(MongoTemplate mongo, MongoCapabilities capabilities) {
        this.mongo = mongo;
        this.capabilities = capabilities;
    }

    @Override
    public void run(ApplicationArguments args) {
        detectReplicaSet();
        ensure("users", userValidator());
        ensure("categories", categoryValidator());
        ensure("courses", courseValidator());
        ensure("enrollments", enrollmentValidator());
        ensure("lessonProgress", progressValidator());
        mongo.indexOps("refreshTokens").ensureIndex(new Index().on("expiresAt", Sort.Direction.ASC).expire(0).named("refresh_ttl"));
        mongo.indexOps("aiInteractions").ensureIndex(new Index().on("expiresAt", Sort.Direction.ASC).expire(0).named("ai_interaction_ttl"));
        mongo.indexOps("aiSessions").ensureIndex(new Index().on("expiresAt", Sort.Direction.ASC).expire(0).named("ai_session_ttl"));
        try {
            MongoCollection<Document> courses = mongo.getCollection("courses");
            courses.createIndex(new Document("title", "text").append("shortDescription", "text")
                    .append("tags", "text").append("skills", "text").append("instructorName", "text"),
                    new IndexOptions().name("course_text"));
        } catch (Exception ex) {
            log.debug("Text index already present or could not be created: {}", ex.getMessage());
        }
    }

    private void detectReplicaSet() {
        try {
            Document hello = mongo.executeCommand("{ hello: 1 }");
            boolean replica = hello.get("setName") != null;
            capabilities.setReplicaSet(replica);
            log.info("MongoDB replica set available: {}", replica);
        } catch (Exception ex) {
            capabilities.setReplicaSet(false);
            log.warn("Could not detect replica set. Multi-document transactions will be skipped. {}", ex.getMessage());
        }
    }

    private void ensure(String name, Document validator) {
        if (!mongo.collectionExists(name)) {
            mongo.createCollection(name);
        }
        try {
            mongo.executeCommand(new Document("collMod", name)
                    .append("validator", validator)
                    .append("validationLevel", "moderate")
                    .append("validationAction", "error"));
        } catch (Exception ex) {
            log.warn("Schema validator for {} was not applied: {}", name, ex.getMessage());
        }
    }

    private static Document userValidator() {
        return Document.parse("""
                { $jsonSchema: { bsonType: "object", required: ["email", "passwordHash", "role", "status", "fullName"],
                  properties: { email: { bsonType: "string" }, role: { enum: ["STUDENT", "INSTRUCTOR", "ADMIN"] },
                    status: { enum: ["ACTIVE", "SUSPENDED"] } } } }
                """);
    }

    private static Document categoryValidator() {
        return Document.parse("""
                { $jsonSchema: { bsonType: "object", required: ["name", "slug", "domain"],
                  properties: { domain: { enum: ["TECHNICAL", "NON_TECHNICAL"] }, active: { bsonType: "bool" } } } }
                """);
    }

    private static Document courseValidator() {
        return Document.parse("""
                { $jsonSchema: { bsonType: "object", required: ["title", "slug", "instructorId", "status"],
                  properties: { status: { enum: ["DRAFT", "PENDING_REVIEW", "PUBLISHED", "REJECTED", "ARCHIVED"] } } } }
                """);
    }

    private static Document enrollmentValidator() {
        return Document.parse("""
                { $jsonSchema: { bsonType: "object", required: ["studentId", "courseId", "status"],
                  properties: { status: { enum: ["ACTIVE", "COMPLETED", "WITHDRAWN"] } } } }
                """);
    }

    private static Document progressValidator() {
        return Document.parse("""
                { $jsonSchema: { bsonType: "object", required: ["studentId", "courseId", "lessonId"],
                  properties: { status: { enum: ["NOT_STARTED", "IN_PROGRESS", "COMPLETED"] } } } }
                """);
    }
}
