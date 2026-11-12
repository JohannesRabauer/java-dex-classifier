package org.jobrunr.democatch.jobs;

import org.springframework.stereotype.Component;

/**
 * What the training job is doing right now, for the start page to show. Lives in memory: the job runs inside this
 * application. After a restart the page falls back on the files of the trained model.
 */
@Component
public class TrainingStatus {

    public record Snapshot(String state, String phase, int done, int total, long elapsedMillis, String message) {
    }

    private String state = "idle";
    private String phase = "";
    private String message;
    private int done;
    private int total;
    private long startedAt;
    private long finishedAt;

    public synchronized boolean isRunning() {
        return "running".equals(state);
    }

    /** Called when the job is enqueued, before a worker picked it up. */
    public synchronized void queued() {
        if (!isRunning()) {
            state = "queued";
            phase = "Waiting for a worker";
            message = null;
            done = 0;
            total = 0;
            startedAt = 0;
        }
    }

    /** @return false when a training is already running */
    public synchronized boolean start(int totalSteps) {
        if (isRunning()) {
            return false;
        }
        state = "running";
        phase = "Starting";
        message = null;
        done = 0;
        total = totalSteps;
        startedAt = System.currentTimeMillis();
        finishedAt = 0;
        return true;
    }

    public synchronized void phase(String phase) {
        this.phase = phase;
    }

    public synchronized void step() {
        done++;
    }

    public synchronized void finish(String message) {
        state = "done";
        phase = "Done";
        this.message = message;
        done = total;
        finishedAt = System.currentTimeMillis();
    }

    public synchronized void fail(String message) {
        state = "failed";
        phase = "Failed";
        this.message = message;
        finishedAt = System.currentTimeMillis();
    }

    public synchronized Snapshot snapshot() {
        long end = finishedAt > 0 ? finishedAt : System.currentTimeMillis();
        long elapsed = startedAt > 0 ? end - startedAt : 0;
        return new Snapshot(state, phase, done, total, elapsed, message);
    }
}
