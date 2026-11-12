package org.javadex.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "app.data-dir=target/test-data")
@AutoConfigureMockMvc
class TrainingControllerTest {

    @Autowired
    MockMvc mockMvc;

    @Test
    void enqueueingPrepareReturnsJobId() throws Exception {
        mockMvc.perform(post("/api/jobs/prepare"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.jobId").isNotEmpty());
    }

    @Test
    void enqueueingTrainReturnsJobId() throws Exception {
        mockMvc.perform(post("/api/jobs/train"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.jobId").isNotEmpty());
    }

    @Test
    void enqueueingEvaluateReturnsJobId() throws Exception {
        mockMvc.perform(post("/api/jobs/evaluate"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.jobId").isNotEmpty());
    }

    @Test
    void evaluationReportIs404BeforeFirstEvaluation() throws Exception {
        mockMvc.perform(get("/api/jobs/evaluation"))
                .andExpect(status().isNotFound());
    }
}
