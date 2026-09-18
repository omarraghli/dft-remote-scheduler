package cires.dft.remotescheduler.solver;

/** Raised when the configured constraints admit no valid week. */
public class NoFeasibleScheduleException extends RuntimeException {

    public NoFeasibleScheduleException(String message) {
        super(message);
    }
}
