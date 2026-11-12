package org.javadex.api;

import org.javadex.JavaDexApplicationConfiguration;
import org.javadex.jobs.CardDataPreparationJob;
import org.javadex.jobs.CardEvaluationJob;
import org.javadex.jobs.CardTrainingJob;
import org.jobrunr.jobs.context.JobContext;
import org.jobrunr.scheduling.JobScheduler;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

@RestController
@RequestMapping("/api/jobs")
public class TrainingController {

    private final JobScheduler jobScheduler;
    private final CardDataPreparationJob preparationJob;
    private final CardTrainingJob trainingJob;
    private final CardEvaluationJob evaluationJob;
    private final JavaDexApplicationConfiguration config;

    public TrainingController(JobScheduler jobScheduler, CardDataPreparationJob preparationJob,
                              CardTrainingJob trainingJob, CardEvaluationJob evaluationJob,
                              JavaDexApplicationConfiguration config) {
        this.jobScheduler = jobScheduler;
        this.preparationJob = preparationJob;
        this.trainingJob = trainingJob;
        this.evaluationJob = evaluationJob;
        this.config = config;
    }

    @PostMapping("/prepare")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Map<String, String> prepare() {
        var id = jobScheduler.enqueue(() -> preparationJob.prepare(JobContext.Null));
        return Map.of("jobId", id.toString());
    }

    @PostMapping("/evaluate")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Map<String, String> evaluate() {
        var id = jobScheduler.enqueue(() -> evaluationJob.evaluate(JobContext.Null));
        return Map.of("jobId", id.toString());
    }

    @GetMapping("/evaluation")
    public org.springframework.http.ResponseEntity<String> evaluation() throws java.io.IOException {
        Path report = config.trainedModelPath().resolve("evaluation.json");
        if (!Files.exists(report)) {
            return org.springframework.http.ResponseEntity.notFound().build();
        }
        return org.springframework.http.ResponseEntity.ok()
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .body(Files.readString(report));
    }

    @PostMapping("/train")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Map<String, String> train(Integer epochs, Integer batchSize) {
        var id = jobScheduler.enqueue(() -> trainingJob.train(epochs, batchSize, JobContext.Null));
        return Map.of("jobId", id.toString());
    }
}
