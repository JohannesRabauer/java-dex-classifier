package org.jobrunr.democatch.web;

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
    void enqueueingTrainAnswersWithAQueuedJob() throws Exception {
        mockMvc.perform(post("/api/jobs/train"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.job.state").value("queued"));
    }

    @Test
    void statusWithoutModelSaysNotReady() throws Exception {
        mockMvc.perform(get("/api/jobs/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.modelReady").value(false));
    }

    @Test
    void evaluationReportIs404BeforeFirstTraining() throws Exception {
        mockMvc.perform(get("/api/jobs/evaluation"))
                .andExpect(status().isNotFound());
    }
}
