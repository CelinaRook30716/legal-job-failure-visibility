package legaljobs;

import java.time.Instant;

public final class DeadlineFollowUpExample {
    private DeadlineFollowUpExample() {
    }

    public static void main(String[] args) throws Exception {
        LegalJobMonitor monitor = new LegalJobMonitor(new InfraiErrorClient(InfraiConfig.fromEnvironment()));
        LegalJobMonitor.ScheduledRun run = new LegalJobMonitor.ScheduledRun(
                "run-2026-08-17-0900",
                "matter-1842",
                LegalJobMonitor.JobKind.DEADLINE_FOLLOW_UP,
                Instant.parse("2026-08-17T01:00:00Z"));

        try {
            monitor.execute(run, () -> {
                throw new IllegalStateException("court deadline feed unavailable");
            });
            throw new AssertionError("failed deadline follow-up was not rethrown");
        } catch (IllegalStateException expected) {
            if (!"court deadline feed unavailable".equals(expected.getMessage())) {
                throw expected;
            }
            System.out.println("deadline follow-up failure captured and rethrown for " + run.matterId());
        }
    }
}
