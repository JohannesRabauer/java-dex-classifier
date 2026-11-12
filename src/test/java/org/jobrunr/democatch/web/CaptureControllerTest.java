package org.jobrunr.democatch.web;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(CaptureControllerTest.TestConfig.class)
class CaptureControllerTest {

    @TempDir
    static Path tempDir;

    @Autowired
    MockMvc mockMvc;

    @TestConfiguration
    static class TestConfig {
        @Bean
        public org.jobrunr.democatch.config.AppPaths appPaths() {
            return new org.jobrunr.democatch.config.AppPaths(tempDir.toString());
        }
    }

    @Test
    void unknownCardReturns404() throws Exception {
        mockMvc.perform(multipart("/api/captures/jd99")
                        .file(new MockMultipartFile("image", "frame.jpg", "image/jpeg", new byte[]{1, 2, 3})))
                .andExpect(status().isNotFound());
    }

    @Test
    void savesCaptureAndReturnsCount() throws Exception {
        mockMvc.perform(multipart("/api/captures/jd01")
                        .file(new MockMultipartFile("image", "frame.jpg", "image/jpeg", new byte[]{1, 2, 3})))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.count").value(1));

        try (var files = Files.list(tempDir.resolve("captures").resolve("jd01"))) {
            assertEquals1(files.filter(p -> p.toString().endsWith(".jpg")).count());
        }
        mockMvc.perform(multipart("/api/captures/jd01")
                        .file(new MockMultipartFile("image", "frame2.jpg", "image/jpeg", new byte[]{4, 5})))
                .andExpect(jsonPath("$.count").value(2));
    }

    private static void assertEquals1(long actual) {
        org.junit.jupiter.api.Assertions.assertEquals(1, actual);
    }

    @Test
    void listsCards() throws Exception {
        mockMvc.perform(get("/api/cards"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("jd01"))
                .andExpect(jsonPath("$.length()").value(20));
    }
}
