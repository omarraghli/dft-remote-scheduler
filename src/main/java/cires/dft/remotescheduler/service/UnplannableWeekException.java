package cires.dft.remotescheduler.service;

/** A week-plan change refused because the week would have no schedule at all afterwards. */
public class UnplannableWeekException extends WeekPlanException {

    public UnplannableWeekException(String message) {
        super(message);
    }
}
