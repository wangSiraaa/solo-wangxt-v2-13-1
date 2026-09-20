-- TM Hub schema: incremental migration batches, replayable tasks, rollbackable releases.
-- All history-bearing tables are append-only; published versions are immutable.

CREATE TABLE tm_version (
    id           BIGSERIAL PRIMARY KEY,
    label        VARCHAR(200) NOT NULL,
    kind         VARCHAR(20)  NOT NULL,           -- BASELINE, LEGACY_IMPORT, INCREMENTAL, ROLLBACK
    parent_id    BIGINT REFERENCES tm_version(id),
    status       VARCHAR(20)  NOT NULL,           -- PUBLISHED, SUPERSEDED
    checksum     VARCHAR(64),                     -- sha256 over canonical entry content
    reason       TEXT,                            -- mandatory for ROLLBACK versions
    created_by   VARCHAR(100),
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- Effective pointer: exactly one row, moved only forward to another published version.
CREATE TABLE version_pointer (
    id         SMALLINT PRIMARY KEY CHECK (id = 1),
    version_id BIGINT NOT NULL REFERENCES tm_version(id),
    moved_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    moved_by   VARCHAR(100),
    note       TEXT
);

-- Pointer movement audit (rollback-by-pointer leaves a trace; nothing is deleted).
CREATE TABLE version_pointer_history (
    id            BIGSERIAL PRIMARY KEY,
    from_version  BIGINT REFERENCES tm_version(id),
    to_version    BIGINT NOT NULL REFERENCES tm_version(id),
    moved_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    moved_by      VARCHAR(100),
    note          TEXT
);

CREATE TABLE tm_entry (
    id             BIGSERIAL PRIMARY KEY,
    version_id     BIGINT NOT NULL REFERENCES tm_version(id),
    source_lang    VARCHAR(10) NOT NULL,
    target_lang    VARCHAR(10) NOT NULL,
    product_line   VARCHAR(50) NOT NULL,
    source_text    TEXT NOT NULL,
    target_text    TEXT NOT NULL,
    identity_key   VARCHAR(64) NOT NULL,          -- sha256(source_lang|target_lang|product_line|source_text)
    origin         VARCHAR(20) NOT NULL,          -- LEGACY_IMPORT, BATCH_MIGRATION, ROLLBACK, BASELINE
    origin_candidate_id BIGINT,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (version_id, identity_key)
);
CREATE INDEX idx_tm_entry_version ON tm_entry(version_id);

CREATE TABLE term_mapping_rule (
    id         BIGSERIAL PRIMARY KEY,
    name       VARCHAR(200) NOT NULL,
    vendor     VARCHAR(100),
    rules      JSONB NOT NULL,   -- [{sourceTerm, targetTerms[], caseSensitive, requiredCaseForm}]
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE language_case_rule (
    id              BIGSERIAL PRIMARY KEY,
    language        VARCHAR(10) NOT NULL,
    term            VARCHAR(200) NOT NULL,        -- glossary term whose case is enforced
    required_form   VARCHAR(200) NOT NULL,        -- exact casing required in target text
    sentence_start_upper BOOLEAN NOT NULL DEFAULT FALSE,
    UNIQUE (language, term)
);

CREATE TABLE batch (
    id                   BIGSERIAL PRIMARY KEY,
    idempotency_key      VARCHAR(64) NOT NULL UNIQUE,  -- sha256(fingerprint|sourceVersion|langs|productLine|vendor)
    source_version_id    BIGINT NOT NULL REFERENCES tm_version(id),
    source_lang          VARCHAR(10) NOT NULL,
    target_lang          VARCHAR(10) NOT NULL,
    product_line         VARCHAR(50) NOT NULL,
    vendor               VARCHAR(100) NOT NULL,
    tmx_fingerprint      VARCHAR(64) NOT NULL,
    term_mapping_rule_id BIGINT REFERENCES term_mapping_rule(id),
    mapping_rules_snapshot JSONB,                -- immutable copy of the rules bound to this batch
    expected_shards      INT NOT NULL DEFAULT 1,
    received_shards      INT NOT NULL DEFAULT 0,
    status               VARCHAR(20) NOT NULL,         -- RECEIVING, READY, FAILED
    error                TEXT,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (tmx_fingerprint, source_version_id, source_lang, target_lang, product_line, vendor)
);

CREATE TABLE batch_shard (
    batch_id          BIGINT NOT NULL REFERENCES batch(id),
    shard_index       INT NOT NULL,
    shard_fingerprint VARCHAR(64) NOT NULL,
    content           BYTEA NOT NULL,
    received_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (batch_id, shard_index)
);

-- Batch membership of a published version (batch manifest).
CREATE TABLE version_batch (
    version_id BIGINT NOT NULL REFERENCES tm_version(id),
    batch_id   BIGINT NOT NULL REFERENCES batch(id),
    PRIMARY KEY (version_id, batch_id)
);

CREATE TABLE conflict_group (
    id             BIGSERIAL PRIMARY KEY,
    identity_key   VARCHAR(64) NOT NULL UNIQUE,   -- one open chain per source/lang/product identity
    status         VARCHAR(20) NOT NULL,          -- OPEN, RESOLVED
    resolved_candidate_id BIGINT,
    resolution_note TEXT,
    resolved_by    VARCHAR(100),
    resolved_at    TIMESTAMPTZ
);

CREATE TABLE candidate (
    id              BIGSERIAL PRIMARY KEY,
    batch_id        BIGINT NOT NULL REFERENCES batch(id),
    conflict_group_id BIGINT REFERENCES conflict_group(id),
    source_lang     VARCHAR(10) NOT NULL,
    target_lang     VARCHAR(10) NOT NULL,
    product_line    VARCHAR(50) NOT NULL,
    vendor          VARCHAR(100) NOT NULL,
    source_text     TEXT NOT NULL,
    proposed_target TEXT NOT NULL,
    final_target    TEXT,                          -- reviewer-specified final wording
    identity_key    VARCHAR(64) NOT NULL,
    group_key       VARCHAR(64) NOT NULL,          -- identity scoped to the source version
    proposal_key    VARCHAR(64) NOT NULL UNIQUE,   -- sha256(group_key|proposed_target): dedupe across retries
    status          VARCHAR(20) NOT NULL,          -- PENDING, CONFLICT, ACCEPTED, REJECTED, BLOCKED
    committed       BOOLEAN NOT NULL DEFAULT FALSE,
    version         BIGINT NOT NULL DEFAULT 0,     -- optimistic lock for concurrent review
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_candidate_batch ON candidate(batch_id);
CREATE INDEX idx_candidate_identity ON candidate(identity_key);
CREATE INDEX idx_candidate_group_key ON candidate(group_key);

-- Append-only review history. Decisions are never updated or deleted.
CREATE TABLE review_decision (
    id              BIGSERIAL PRIMARY KEY,
    candidate_id    BIGINT NOT NULL REFERENCES candidate(id),
    reviewer        VARCHAR(100) NOT NULL,
    action          VARCHAR(20) NOT NULL,          -- ACCEPT, REJECT, SET_FINAL, AUTO_SUPERSEDE
    final_target    TEXT,
    rationale       TEXT,
    previous_status VARCHAR(20) NOT NULL,
    new_status      VARCHAR(20) NOT NULL,
    previous_final_target TEXT,
    decided_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_decision_candidate ON review_decision(candidate_id);

CREATE TABLE candidate_anomaly (
    id           BIGSERIAL PRIMARY KEY,
    candidate_id BIGINT NOT NULL REFERENCES candidate(id),
    type         VARCHAR(30) NOT NULL,             -- PLACEHOLDER_MISMATCH, CASING_VIOLATION, POLYSEMY
    detail       JSONB,
    resolved     BOOLEAN NOT NULL DEFAULT FALSE,
    resolved_by  VARCHAR(100),
    resolved_at  TIMESTAMPTZ,
    resolution   TEXT
);
CREATE INDEX idx_anomaly_candidate ON candidate_anomaly(candidate_id);

CREATE TABLE migration_task (
    id                BIGSERIAL PRIMARY KEY,
    name              VARCHAR(200) NOT NULL,
    batch_id          BIGINT NOT NULL REFERENCES batch(id),
    status            VARCHAR(20) NOT NULL,        -- PENDING, RUNNING, PAUSED, FAILED, INTERRUPTED, COMPLETED
    total             INT NOT NULL DEFAULT 0,
    committed_count   INT NOT NULL DEFAULT 0,
    last_candidate_id BIGINT NOT NULL DEFAULT 0,   -- checkpoint: resume strictly after this id
    error             TEXT,
    version           BIGINT NOT NULL DEFAULT 0,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- One row per committed candidate, ever. UNIQUE(candidate_id) makes re-commit impossible.
CREATE TABLE migration_commit (
    id           BIGSERIAL PRIMARY KEY,
    task_id      BIGINT NOT NULL REFERENCES migration_task(id),
    candidate_id BIGINT NOT NULL UNIQUE REFERENCES candidate(id),
    committed_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_commit_task ON migration_commit(task_id);

-- Conflict resolutions folded into a published release.
CREATE TABLE version_conflict_resolution (
    id               BIGSERIAL PRIMARY KEY,
    version_id       BIGINT NOT NULL REFERENCES tm_version(id),
    conflict_group_id BIGINT NOT NULL REFERENCES conflict_group(id),
    resolved_candidate_id BIGINT,
    note             TEXT,
    resolved_by      VARCHAR(100),
    resolved_at      TIMESTAMPTZ
);

CREATE TABLE export_task (
    id            BIGSERIAL PRIMARY KEY,
    version_id    BIGINT NOT NULL REFERENCES tm_version(id),
    status        VARCHAR(20) NOT NULL,            -- RUNNING, FAILED, INTERRUPTED, COMPLETED
    total         INT NOT NULL DEFAULT 0,
    exported      INT NOT NULL DEFAULT 0,
    last_entry_id BIGINT NOT NULL DEFAULT 0,       -- checkpoint
    checksum      VARCHAR(64),
    download_url  VARCHAR(300),
    error         TEXT,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Staged TMX body chunks let an interrupted export resume without re-emitting entries.
CREATE TABLE export_chunk (
    id            BIGSERIAL PRIMARY KEY,
    export_task_id BIGINT NOT NULL REFERENCES export_task(id),
    seq           INT NOT NULL,
    content       TEXT NOT NULL,
    UNIQUE (export_task_id, seq)
);

CREATE TABLE export_artifact (
    id          BIGSERIAL PRIMARY KEY,
    version_id  BIGINT NOT NULL REFERENCES tm_version(id),
    checksum    VARCHAR(64) NOT NULL,
    content     TEXT NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
