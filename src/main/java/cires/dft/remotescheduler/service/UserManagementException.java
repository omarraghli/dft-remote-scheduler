package cires.dft.remotescheduler.service;

/** A refused account change, with a message meant to be shown to the admin who tried it. */
public class UserManagementException extends RuntimeException {

    public UserManagementException(String message) {
        super(message);
    }
}
