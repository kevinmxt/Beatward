package dev.beatward.monitor;

public class QuotaExceededException extends RuntimeException {

    private final long current;
    private final int limit;

    public QuotaExceededException(long current, int limit) {
        super("Monitor quota exceeded: " + current + " of " + limit
                + " monitors are already in use. Delete an existing monitor before creating a new one.");
        this.current = current;
        this.limit = limit;
    }

    public long getCurrent() {
        return current;
    }

    public int getLimit() {
        return limit;
    }
}
