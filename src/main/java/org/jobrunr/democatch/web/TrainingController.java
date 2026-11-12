package org.jobrunr.democatch.web;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import org.jobrunr.democatch.config.AppPaths;
import org.jobrunr.democatch.jobs.CardTrainingJob;
import org.jobrunr.democatch.jobs.TrainingStatus;
import org.jobrunr.jobs.context.JobContext;
import org.jobrunr.scheduling.JobScheduler;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/jobs")
public class TrainingController {

    /** What the start page needs: the running job, and what is known about the model on disk. */
    public record StatusResponse(TrainingStatus.Snapshot job, boolean modelReady, Double accuracy,
                                 Double totalSeconds, Double embedSeconds, Double trainSeconds, Integer pictures) {
    }

    private final JobScheduler jobScheduler;
    private final CardTrainingJob trainingJob;
    private final TrainingStatus status;
    private final AppPaths paths;

    public TrainingController(JobScheduler jobScheduler, CardTrainingJob trainingJob, TrainingStatus status,
                              AppPaths paths) {
        this.jobScheduler = jobScheduler;
        this.trainingJob = trainingJob;
        this.status = status;
        this.paths = paths;
    }

    @PostMapping("/train")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public StatusResponse train() throws IOException {
        if (status.isRunning()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A training is already running");
        }
        status.queued();
        jobScheduler.enqueue(() -> trainingJob.train(JobContext.Null));
        return status();
    }

    @GetMapping("/status")
    public StatusResponse status() throws IOException {
        Path classes = paths.model().resolve("classes.txt");
        boolean ready = Files.exists(classes);
        Properties props = new Properties();
        Path propsFile = paths.model().resolve("model.properties");
        if (ready && Files.exists(propsFile)) {
            try (var in = Files.newInputStream(propsFile)) {
                props.load(in);
            }
        }
        return new StatusResponse(status.snapshot(), ready,
                number(props, "accuracy"), number(props, "totalSeconds"),
                number(props, "embedSeconds"), number(props, "trainSeconds"),
                number(props, "pictures") == null ? null : number(props, "pictures").intValue());
    }

    @GetMapping("/evaluation")
    public ResponseEntity<String> evaluation() throws IOException {
        Path report = paths.model().resolve("evaluation.json");
        if (!Files.exists(report)) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(Files.readString(report));
    }

    private static Double number(Properties props, String key) {
        String value = props.getProperty(key);
        return value == null ? null : Double.valueOf(value);
    }
}
