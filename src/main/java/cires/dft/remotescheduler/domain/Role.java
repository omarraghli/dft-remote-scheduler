package cires.dft.remotescheduler.domain;

/** What an account is allowed to do. */
public enum Role {

    /** Reads the schedule and exports it. Cannot generate, re-roll or manage accounts. */
    USER("User"),

    /** Everything a user can do, plus generating, re-rolling and managing accounts. */
    ADMIN("Admin");

    private final String label;

    Role(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    /** Spring Security's convention: authorities carry a {@code ROLE_} prefix. */
    public String authority() {
        return "ROLE_" + name();
    }
}
