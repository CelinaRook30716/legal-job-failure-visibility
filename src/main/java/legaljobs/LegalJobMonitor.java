package legaljobs;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class LegalJobMonitor {
    public enum JobKind {
        MATTER_INTAKE, SIGNED_DOCUMENT_DELIVERY, DEADLINE_FOLLOW_UP
    }

    public record ScheduledRun(String runId, String matterId, JobKind kind, Instant scheduledAt) {
    }

    @FunctionalInterface
    public interface JobAction {
        void run() throws Exception;
    }

    private final FailureReporter reporter;

    public LegalJobMonitor(FailureReporter reporter) {
        this.reporter = reporter;
    }

    public void execute(ScheduledRun run, JobAction action) throws Exception {
        try {
            action.run();
        } catch (Exception cause) {
            Map<String, String> context = new LinkedHashMap<>();
            context.put("runId", run.runId());
            context.put("matterId", run.matterId());
            context.put("jobKind", run.kind().name());
            context.put("scheduledAt", run.scheduledAt().toString());

            reporter.capture(new JobFailure(
                    "Scheduled legal job failed: " + run.kind().name(),
                    cause.getClass().getSimpleName() + ": " + cause.getMessage(),
                    stackTrace(cause),
                    List.of("legal-job", run.kind().name()),
                    context));
            throw cause;
        }
    }

    private static String stackTrace(Exception cause) {
        StringBuilder trace = new StringBuilder(cause.toString());
        for (StackTraceElement frame : cause.getStackTrace()) {
            trace.append("\n\tat ").append(frame);
        }
        return trace.toString();
    }
}
