package org.javadex.jobs;

import org.javadex.jobs.CardEvaluationJob.EvaluationReport;
import org.javadex.ml.ConfusionMatrix;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EvaluationReportTest {

    @Test
    void serializesToSameJsonShapeAsTheHandWrittenReport() {
        ConfusionMatrix matrix = new ConfusionMatrix(
                List.of("jd01", "jd02"),
                List.of(
                        new ConfusionMatrix.Record("jd01", "jd01"),
                        new ConfusionMatrix.Record("jd01", "jd01"),
                        new ConfusionMatrix.Record("jd02", "jd02"),
                        new ConfusionMatrix.Record("jd02", "jd01")));

        String json = new ObjectMapper()
                .writerWithDefaultPrettyPrinter()
                .writeValueAsString(EvaluationReport.from(matrix));

        var root = new ObjectMapper().readTree(json);
        assertEquals(0.75, root.path("overallAccuracy").asDouble());
        var perClass = root.path("perClass");
        assertEquals(2, perClass.size());
        assertEquals("jd01", perClass.get(0).path("class").asString());
        assertEquals(1.0, perClass.get(0).path("accuracy").asDouble());
        assertEquals("jd02", perClass.get(1).path("class").asString());
        assertEquals(0.5, perClass.get(1).path("accuracy").asDouble());
        var confusion = root.path("confusion");
        assertEquals(2, confusion.size());
        assertEquals(2, confusion.get(0).get(0).asInt());
        assertEquals(0, confusion.get(0).get(1).asInt());
        assertEquals(1, confusion.get(1).get(0).asInt());
        assertEquals(1, confusion.get(1).get(1).asInt());
    }

    @Test
    void keepsTheEstablishedFieldOrder() {
        String json = new ObjectMapper()
                .writeValueAsString(EvaluationReport.from(new ConfusionMatrix(
                        List.of("jd01"), List.of(new ConfusionMatrix.Record("jd01", "jd01")))));

        int overall = json.indexOf("overallAccuracy");
        int perClass = json.indexOf("perClass");
        int confusion = json.indexOf("confusion");
        assertTrue(overall < perClass && perClass < confusion,
                "fields must appear in order overallAccuracy < perClass < confusion but was: " + json);
    }
}
