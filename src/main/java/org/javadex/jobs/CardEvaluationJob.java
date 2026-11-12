package org.javadex.jobs;

import ai.djl.modality.cv.ImageFactory;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.javadex.JavaDexApplicationConfiguration;
import org.javadex.ml.CardClassifier;
import org.javadex.ml.ConfusionMatrix;
import org.jobrunr.jobs.annotations.Job;
import org.jobrunr.jobs.context.JobContext;
import org.jobrunr.jobs.context.JobRunrDashboardLogger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Component
public class CardEvaluationJob {
}
