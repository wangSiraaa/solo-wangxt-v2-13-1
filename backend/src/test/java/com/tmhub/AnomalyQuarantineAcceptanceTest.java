package com.tmhub;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tmhub.domain.*;
import com.tmhub.repo.*;
import com.tmhub.service.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Scenario 3: missing placeholders, non-compliant casing and polysemous terms are quarantined,
 * never auto-guessed; they block the release until a human resolves them, while valid
 * entries are not discarded.
 */
class AnomalyQuarantineAcceptanceTest extends BaseAcceptanceTest {

    @Autowired PublishService publishService;
    @Autowired ReviewService reviewService;
    @Autowired MigrationEngine migrationEngine;
    @Autowired CandidateRepository candidateRepository;
    @Autowired CandidateAnomalyRepository anomalyRepository;
    @Autowired LanguageCaseRuleRepository caseRuleRepository;
    @Autowired TmEntryRepository entryRepository;

    @Test
    void anomaliesQuarantined_releaseBlockedUntilManualResolution_validEntriesUnaffected() {
        // Language casing configuration: German glossary term must keep its capitalized form.
        LanguageCaseRule rule = new LanguageCaseRule();
        rule.setLanguage("de");
        rule.setTerm("Benutzer");
        rule.setRequiredForm("Benutzer");
        caseRuleRepository.save(rule);

        // Term mapping rules: "record" is polysemous (two German targets) — never auto-guess.
        String mappingRules = """
                [{"sourceTerm":"record","targetTerms":["Aufzeichnung","Datensatz"]}]
                """;

        TmVersion baseline = publishService.createBaseline("v0", "tester");
        long batchId = importBatch(baseline.getId(), "en", "de", "mail", "vendor-a",
                tmx("en", "de", new String[][]{
                        {"Delete {0} files", "Dateien löschen"},                    // missing {0}
                        {"User settings", "benutzer-Einstellungen"},                // casing violation
                        {"Open the record", "Öffne die Aufzeichnung"},              // polysemy
                        {"Save {0} now", "{0} jetzt speichern"},                    // valid
                        {"Close window", "Fenster schließen"}}),                    // valid
                mappingRules);

        List<Candidate> candidates = batchService.candidatesOf(batchId);
        assertThat(candidates).hasSize(5);

        List<Candidate> blocked = candidates.stream()
                .filter(c -> c.getStatus() == CandidateStatus.BLOCKED).toList();
        List<Candidate> pending = candidates.stream()
                .filter(c -> c.getStatus() == CandidateStatus.PENDING).toList();
        assertThat(blocked).hasSize(3);
        assertThat(pending).hasSize(2); // valid entries are not discarded

        // Each quarantine reason is recorded and traceable.
        var anomalyTypes = blocked.stream()
                .flatMap(c -> anomalyRepository.findByCandidateId(c.getId()).stream())
                .map(CandidateAnomaly::getType).toList();
        assertThat(anomalyTypes).containsExactlyInAnyOrder(
                AnomalyType.PLACEHOLDER_MISMATCH, AnomalyType.CASING_VIOLATION, AnomalyType.POLYSEMY);

        // Quarantined anomalies block the release.
        reviewService.decide(pending.get(0).getId(),
                new ReviewService.DecisionCommand("alice", DecisionAction.ACCEPT, null, null, null));
        reviewService.decide(pending.get(1).getId(),
                new ReviewService.DecisionCommand("alice", DecisionAction.ACCEPT, null, null, null));
        MigrationTask task = migrationEngine.createTask(batchId, "de-mail");
        migrationEngine.start(task.getId());
        migrationEngine.run(task.getId());

        assertThatThrownBy(() -> publishService.publish("rel-1", List.of(batchId), "pm"))
                .isInstanceOf(PublishBlockedException.class)
                .hasMessageContaining("quarantined");

        // A final target that still breaks placeholders is refused even from a human.
        Candidate missingPlaceholder = blocked.stream()
                .filter(c -> c.getProposedTarget().equals("Dateien löschen")).findFirst().orElseThrow();
        assertThatThrownBy(() -> reviewService.decide(missingPlaceholder.getId(),
                new ReviewService.DecisionCommand("alice", DecisionAction.SET_FINAL,
                        "Dateien entfernen", "try again", null)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("placeholder");

        // Manual handling unblocks each quarantined candidate.
        reviewService.decide(missingPlaceholder.getId(),
                new ReviewService.DecisionCommand("alice", DecisionAction.SET_FINAL,
                        "{0} Dateien löschen", "placeholder restored", null));
        Candidate casing = blocked.stream()
                .filter(c -> c.getProposedTarget().equals("benutzer-Einstellungen")).findFirst().orElseThrow();
        reviewService.decide(casing.getId(),
                new ReviewService.DecisionCommand("bob", DecisionAction.SET_FINAL,
                        "Benutzer-Einstellungen", "glossary casing", null));
        Candidate polysemy = blocked.stream()
                .filter(c -> c.getProposedTarget().equals("Öffne die Aufzeichnung")).findFirst().orElseThrow();
        reviewService.decide(polysemy.getId(),
                new ReviewService.DecisionCommand("carol", DecisionAction.SET_FINAL,
                        "Öffne den Datensatz", "context: database row", null));

        // Anomalies are marked resolved with operator and resolution note.
        for (Candidate c : List.of(missingPlaceholder, casing, polysemy)) {
            var anomalies = anomalyRepository.findByCandidateId(c.getId());
            assertThat(anomalies).isNotEmpty();
            assertThat(anomalies).allSatisfy(a -> {
                assertThat(a.isResolved()).isTrue();
                assertThat(a.getResolvedBy()).isNotBlank();
            });
        }

        // Continue migration with a follow-up task: the newly accepted candidates are committed too.
        MigrationTask followUp = migrationEngine.createTask(batchId, "de-mail-follow-up");
        migrationEngine.start(followUp.getId());
        migrationEngine.run(followUp.getId());
        assertThat(migrationEngine.get(followUp.getId()).getStatus()).isEqualTo(TaskStatus.COMPLETED);
        assertThat(migrationEngine.get(followUp.getId()).getCommittedCount()).isEqualTo(3);

        TmVersion release = publishService.publish("rel-1", List.of(batchId), "pm");
        assertThat(release.getChecksum()).hasSize(64);

        List<TmEntry> entries = entryRepository.findByVersionIdOrderById(release.getId());
        assertThat(entries).hasSize(5);
        assertThat(entries.stream().map(TmEntry::getTargetText)).contains(
                "{0} Dateien löschen", "Benutzer-Einstellungen", "Öffne den Datensatz",
                "{0} jetzt speichern", "Fenster schließen");
    }
}
