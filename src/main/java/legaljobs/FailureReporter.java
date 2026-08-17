package legaljobs;

public interface FailureReporter {
    void capture(JobFailure failure);
}
