package org.jobrunr.democatch.web;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(PredictionControllerTest.TestConfig.class)
class PredictionControllerTest {

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
    void predictWithoutModelIs409() throws Exception {
        mockMvc.perform(multipart("/api/predict")
                        .file(new MockMultipartFile("image", "frame.jpg", "image/jpeg", new byte[]{1, 2, 3})))
                .andExpect(status().isConflict());
    }

    @Test
    void softmaxSumsToOneAndKeepsOrder() {
        float[] softmax = CardPredictionService.softmax(new float[]{2.0f, 1.0f, 0.0f});
        assertEquals(1.0, softmax[0] + softmax[1] + softmax[2], 1e-6);
        assertTrue(softmax[0] > softmax[1] && softmax[1] > softmax[2]);
    }
}
