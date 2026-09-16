public enum JoinStatus {
    OPEN("Open"),
    WHITELIST("Whitelist"),
    ONLINE_MODE("Online mode"),
    MODS_REQUIRED("Client mods required"),
    BANNED("Banned"),
    SERVER_FULL("Server full"),
    VERSION_MISMATCH("Wrong version"),
    AUTH_FAILED("Authentication failed"),
    RATE_LIMITED("Rate limited"),
    REJECTED("Rejected"),
    UNKNOWN("Unknown");

    private final String label;

    JoinStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
