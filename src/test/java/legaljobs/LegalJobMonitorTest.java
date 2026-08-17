package legaljobs;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public final class LegalJobMonitorTest {
    public static void main(String[] args) {
        List<JobFailure> captured = new ArrayList<>();
        LegalJobMonitor monitor = new LegalJobMonitor(captured::add);
        LegalJobMonitor.ScheduledRun run = new LegalJobMonitor.ScheduledRun(
                "delivery-73", "matter-1842",
                LegalJobMonitor.JobKind.SIGNED_DOCUMENT_DELIVERY,
                Instant.parse("2026-08-17T01:00:00Z"));

        try {
            monitor.execute(run, () -> { throw new IllegalStateException("recipient address rejected"); });
            throw new AssertionError("job failure should be rethrown");
        } catch (IllegalStateException expected) {
            check(captured.size() == 1, "one failure must be captured");
            JobFailure failure = captured.get(0);
            check(failure.fingerprint().equals(List.of("legal-job", "SIGNED_DOCUMENT_DELIVERY")),
                    "job kind must define the stable group");
            check("matter-1842".equals(failure.context().get("matterId")),
                    "matter must remain available for compliance review");
            check("delivery-73".equals(failure.context().get("runId")),
                    "scheduled run must remain traceable");
        } catch (Exception unexpected) {
            throw new AssertionError(unexpected);
        }

        System.out.println("PASS: failed signed-document delivery is captured and rethrown");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
