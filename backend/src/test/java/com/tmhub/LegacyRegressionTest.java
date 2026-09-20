package com.tmhub;

import static org.assertj.core.api.Assertions.assertThat;

import com.tmhub.domain.*;
import com.tmhub.repo.*;
import com.tmhub.service.*;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Scenario 5: regression of the original flows — single-vendor TMX import, old-version download,
 * plain term replacement — with the new batch mechanism active alongside them.
 */
class LegacyRegressionTest extends BaseAcceptanceTest {

    @Autowired LegacyService legacyService;
    @Autowired PublishService publishService;
    @Autowired ReviewService reviewService;
    @Autowired MigrationEngine migrationEngine;
    @Autowired ExportService exportService;
    @Autowired VersionQueryService versionQueryService;
    @Autowired TmEntryRepository entryRepository;
    @Autowired TmVersionRepository versionRepository;

    @Test
    void legacyImport_termReplace_oldVersionDownload_stillWork_withBatchesAlongside() {
        // Original single-vendor import.
        TmVersion legacy = legacyService.importTmx(
                tmx("en", "de", new String[][]{
                        {"Hello", "Hallo"},
                        {"File menu", "Datei-Menü"}}).getBytes(StandardCharsets.UTF_8),
                "mail", "legacy-1", "importer");
        assertThat(legacy.getKind()).isEqualTo(VersionKind.LEGACY_IMPORT);
        assertThat(legacy.getChecksum()).hasSize(64);
        assertThat(entryRepository.findByVersionIdOrderById(legacy.getId())).hasSize(2);
        assertThat(publishService.currentPointerVersion().getId()).isEqualTo(legacy.getId());

        // Original plain term replacement.
        TmVersion replaced = legacyService.replaceTerm(legacy.getId(), "Datei", "Akte", "terminologist");
        assertThat(replaced.getParentId()).isEqualTo(legacy.getId());
        List<TmEntry> replacedEntries = entryRepository.findByVersionIdOrderById(replaced.getId());
        assertThat(replacedEntries.stream().map(TmEntry::getTargetText))
                .contains("Hallo", "Akte-Menü");
        assertThat(publishService.currentPointerVersion().getId()).isEqualTo(replaced.getId());

        // New batch mechanism on top of the legacy version: nothing breaks.
        long batchId = importBatch(replaced.getId(), "en", "de", "mail", "vendor-a",
                tmx("en", "de", new String[][]{{"Print", "Drucken"}}), null);
        Candidate candidate = batchService.candidatesOf(batchId).get(0);
        reviewService.decide(candidate.getId(),
                new ReviewService.DecisionCommand("alice", DecisionAction.ACCEPT, null, null, null));
        MigrationTask task = migrationEngine.createTask(batchId, "on-top-of-legacy");
        migrationEngine.start(task.getId());
        migrationEngine.run(task.getId());
        TmVersion release = publishService.publish("rel-legacy-plus", List.of(batchId), "pm");

        List<TmEntry> entries = entryRepository.findByVersionIdOrderById(release.getId());
        assertThat(entries).hasSize(3); // 2 legacy entries carried over + 1 new
        assertThat(entries.stream().map(TmEntry::getTargetText))
                .contains("Hallo", "Akte-Menü", "Drucken");

        // Old versions keep their download URLs: export and download the ORIGINAL legacy version.
        ExportTask export = exportService.startExport(legacy.getId());
        exportService.run(export.getId());
        ExportTask done = exportService.getTask(export.getId());
        assertThat(done.getDownloadUrl()).isEqualTo("/api/versions/" + legacy.getId() + "/export");
        ExportArtifact artifact = exportService.latestArtifact(legacy.getId());
        assertThat(artifact.getContent()).contains("Hallo", "Datei-Menü"); // pre-replacement content

        // Every published version is still present — history was not rewritten.
        assertThat(versionRepository.findById(legacy.getId())).isPresent();
        assertThat(versionRepository.findById(replaced.getId())).isPresent();
        assertThat(versionRepository.findById(release.getId())).isPresent();

        // Rollback of the release produces a reverse version with a reason and restores content.
        TmVersion rolledBack = publishService.rollback(release.getId(),
                PublishService.RollbackMode.REVERSE_VERSION, "regression found in field", "pm");
        assertThat(rolledBack.getKind()).isEqualTo(VersionKind.ROLLBACK);
        assertThat(rolledBack.getReason()).isEqualTo("regression found in field");
        assertThat(rolledBack.getParentId()).isEqualTo(release.getId());
        assertThat(entryRepository.findByVersionIdOrderById(rolledBack.getId()))
                .extracting(TmEntry::getTargetText)
                .containsExactlyInAnyOrder("Hallo", "Akte-Menü");
        assertThat(publishService.currentPointerVersion().getId()).isEqualTo(rolledBack.getId());
        // The rolled-back release itself is untouched.
        assertThat(versionRepository.findById(release.getId()).orElseThrow().getChecksum())
                .isEqualTo(release.getChecksum());

        // Lineage walks the whole chain: rollback -> release -> term-replace -> legacy import.
        var lineage = versionQueryService.lineage(rolledBack.getId());
        assertThat(lineage.effectiveVersionId()).isEqualTo(rolledBack.getId());
        assertThat(lineage.chain()).extracting(n -> n.version().getId())
                .containsExactly(rolledBack.getId(), release.getId(), replaced.getId(), legacy.getId());
        assertThat(lineage.chain().get(1).batchIds()).containsExactly(batchId);
    }
}
