package com.lumina.config;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.IndexOptions;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

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
        ensureTtl("refreshTokens", "refresh_ttl");
        ensureTtl("aiInteractions", "ai_interaction_ttl");
        ensureTtl("aiSessions", "ai_session_ttl");
        ensureTextIndex();
        log.info("MongoDB startup checks finished.");
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

    private void ensureTtl(String collection, String indexName) {
        try {
            if (!mongo.collectionExists(collection)) {
                mongo.createCollection(collection);
            }
            MongoCollection<Document> coll = mongo.getCollection(collection);
            boolean ready = false;
            List<String> drop = new ArrayList<>();
            for (Document index : coll.listIndexes()) {
                String name = index.getString("name");
                Document key = index.get("key", Document.class);
                if (name == null || key == null || key.get("expiresAt") == null || key.size() != 1) {
                    continue;
                }
                boolean ttl = index.get("expireAfterSeconds") != null;
                if (indexName.equals(name) && ttl) {
                    ready = true;
                    continue;
                }
                drop.add(name);
            }
            for (String name : drop) {
                coll.dropIndex(name);
            }
            if (!ready) {
                coll.createIndex(new Document("expiresAt", 1),
                        new IndexOptions().name(indexName).expireAfter(0L, TimeUnit.SECONDS));
            }
            log.info("TTL index {} is ready on {}", indexName, collection);
        } catch (Exception ex) {
            log.warn("TTL index {} on {} was not applied. The API will stay up. {}", indexName, collection, ex.getMessage());
        }
    }

    private void ensureTextIndex() {
        try {
            MongoCollection<Document> courses = mongo.getCollection("courses");
            courses.createIndex(new Document("title", "text").append("shortDescription", "text")
                            .append("tags", "text").append("skills", "text").append("instructorName", "text"),
                    new IndexOptions().name("course_text"));
        } catch (Exception ex) {
            log.debug("Text index already present or could not be created: {}", ex.getMessage());
        }
    }

    private void ensure(String name, Document validator) {
        try {
            if (!mongo.collectionExists(name)) {
                mongo.createCollection(name);
            }
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