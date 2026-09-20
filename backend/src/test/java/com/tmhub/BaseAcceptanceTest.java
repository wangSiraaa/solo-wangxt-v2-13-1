package com.tmhub;

import com.tmhub.service.BatchService;
import com.tmhub.service.Hashes;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest
@ActiveProfiles("test")
public abstract class BaseAcceptanceTest {

    /** One shared PostgreSQL for the whole suite (Spring caches the context across classes). */
    protected static final EmbeddedPostgres postgres = startShared();

    private static EmbeddedPostgres startShared() {
        try {
            return EmbeddedPostgres.start();
        } catch (IOException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    @DynamicPropertySource
    static void datasourceProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> postgres.getJdbcUrl("postgres", "postgres"));
        registry.add("spring.datasource.username", () -> "postgres");
        registry.add("spring.datasource.password", () -> "postgres");
    }

    @Autowired
    protected JdbcTemplate jdbc;

    @Autowired
    protected BatchService batchService;

    @BeforeEach
    void cleanDatabase() {
        jdbc.execute("""
                TRUNCATE version_pointer_history, version_pointer, version_conflict_resolution,
                         version_batch, export_chunk, export_artifact, export_task,
                         migration_commit, migration_task, review_decision, candidate_anomaly,
                         candidate, conflict_group, batch_shard, batch, tm_entry, tm_version,
                         term_mapping_rule, language_case_rule
                RESTART IDENTITY CASCADE
                """);
    }

    /** Build a minimal TMX document for the given segment pairs. */
    protected static String tmx(String sourceLang, String targetLang, String[][] pairs) {
        StringBuilder sb = new StringBuilder(
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?><tmx version=\"1.4\"><body>");
        for (String[] pair : pairs) {
            sb.append("<tu><tuv xml:lang=\"").append(sourceLang).append("\"><seg>")
                    .append(pair[0]).append("</seg></tuv><tuv xml:lang=\"").append(targetLang)
                    .append("\"><seg>").append(pair[1]).append("</seg></tuv></tu>");
        }
        return sb.append("</body></tmx>").toString();
    }

    /** Initiate a batch and deliver it as a single shard; returns the batch id. */
    protected long importBatch(long sourceVersionId, String sourceLang, String targetLang,
                               String productLine, String vendor, String tmxContent,
                               String mappingRulesSnapshot) {
        String fingerprint = Hashes.sha256(tmxContent);
        var batch = batchService.initiate(new BatchService.InitiateCommand(
                sourceVersionId, sourceLang, targetLang, productLine, vendor, fingerprint,
                null, mappingRulesSnapshot, 1));
        batchService.uploadShard(batch.getId(), 0, tmxContent.getBytes(StandardCharsets.UTF_8));
        return batch.getId();
    }
}
