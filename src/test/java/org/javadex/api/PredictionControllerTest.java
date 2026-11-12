package org.javadex.api;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Path;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class PredictionControllerTest {

    @TempDir
    static Path tempDir;

    @Autowired
    MockMvc mockMvc;

    @DynamicPropertySource
    static void dataDir(DynamicPropertyRegistry registry) {
        registry.add("app.data-dir", () -> tempDir.toString());
    }

    @Test
    void predictWithoutModelIs409() throws Exception {
        mockMvc.perform(multipart("/api/predict")
                        .file(new MockMultipartFile("image", "frame.jpg", "image/jpeg", new byte[]{1, 2, 3})))
                .andExpect(status().isConflict());
    }
}
