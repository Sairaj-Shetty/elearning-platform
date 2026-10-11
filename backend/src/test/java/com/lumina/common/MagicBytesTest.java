package com.lumina.common;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MagicBytesTest {
    @Test
    void sampleLessonIsAnMp4() throws Exception {
        try (var in = getClass().getResourceAsStream("/sample/sample-lesson.mp4")) {
            byte[] header = new byte[32];
            int read = in.read(header);
            assertTrue(read > 12);
            assertTrue(MagicBytes.isMp4(header));
        }
    }

    @Test
    void sluggerKeepsWords() {
        assertEquals("java-and-spring-boot", Slugger.slug("Java and Spring Boot"));
    }
}
