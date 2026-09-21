package cires.dft.remotescheduler.service;

/** A refused change to somebody's leave, with a message meant for the admin who tried it. */
public class VacationManagementException extends RuntimeException {

    public VacationManagementException(String message) {
        super(message);
    }
}
