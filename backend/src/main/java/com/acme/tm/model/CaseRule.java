package com.acme.tm.model;

/** Casing rule enforced on approved terms for a target language. */
public enum CaseRule {
    PRESERVE,
    LOWER,
    UPPER;

    public String apply(String term) {
        return switch (this) {
            case PRESERVE -> term;
            case LOWER -> term.toLowerCase();
            case UPPER -> term.toUpperCase();
        };
    }
}
