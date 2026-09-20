package com.acme.tm.model;

public enum CandidateStatus {
    PENDING,    // imported, awaiting review
    CONFLICT,   // same source/lang-pair/product-line with diverging targets across vendors
    BLOCKED,    // quarantined by an anomaly (placeholder, case, polysemy); never auto-migrated
    ACCEPTED,   // reviewer approved; eligible for migration
    REJECTED,   // reviewer rejected; never migrated
    MIGRATED    // committed by a migration task
}
