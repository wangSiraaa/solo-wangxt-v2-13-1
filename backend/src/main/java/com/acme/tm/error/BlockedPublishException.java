package com.acme.tm.error;

import java.util.List;

/** Raised when a publish is attempted while quarantined or unresolved entries remain. */
public class BlockedPublishException extends RuntimeException {
    private final List<String> blockers;

    public BlockedPublishException(List<String> blockers) {
        super("Publish blocked: " + blockers.size() + " entr(ies) require manual handling");
        this.blockers = blockers;
    }

    public List<String> getBlockers() { return blockers; }
}
