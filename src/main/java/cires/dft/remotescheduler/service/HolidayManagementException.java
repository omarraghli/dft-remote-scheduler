package cires.dft.remotescheduler.service;

/** A refused holiday change, with a message meant to be shown to the admin who tried it. */
public class HolidayManagementException extends RuntimeException {

    public HolidayManagementException(String message) {
        super(message);
    }
}
