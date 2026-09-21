package cires.dft.remotescheduler.service;

/** A refused change to a week's wishes or on-site days, with a message meant for whoever tried. */
public class WeekPlanException extends RuntimeException {

    public WeekPlanException(String message) {
        super(message);
    }
}
