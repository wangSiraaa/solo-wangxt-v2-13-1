package com.tmhub.service;

import com.tmhub.domain.EntryOrigin;
import com.tmhub.domain.TmEntry;
import com.tmhub.domain.TmVersion;
import com.tmhub.domain.VersionKind;
import com.tmhub.domain.VersionStatus;
import com.tmhub.repo.TmEntryRepository;
import com.tmhub.repo.TmVersionRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The original pre-batch flows, kept intact for regression:
 * single-vendor TMX import straight into a new version, and plain term replacement.
 */
@Service
public class LegacyService {

    private final TmVersionRepository versionRepository;
    private final TmEntryRepository entryRepository;
    private final TmxParser tmxParser;
    private final PublishService publishService;

    public LegacyService(TmVersionRepository versionRepository, TmEntryRepository entryRepository,
                         TmxParser tmxParser, PublishService publishService) {
        this.versionRepository = versionRepository;
        this.entryRepository = entryRepository;
        this.tmxParser = tmxParser;
        this.publishService = publishService;
    }

    /** Original single-vendor import: parse TMX and publish a new version directly. */
    @Transactional
    public TmVersion importTmx(byte[] tmxContent, String productLine, String label, String actor) {
        List<TmxParser.Tu> tus = tmxParser.parse(tmxContent);
        TmVersion parent = publishService.currentPointerVersion();

        TmVersion version = new TmVersion();
        version.setLabel(label);
        version.setKind(VersionKind.LEGACY_IMPORT);
        version.setParentId(parent != null ? parent.getId() : null);
        version.setStatus(VersionStatus.PUBLISHED);
        version.setCreatedBy(actor);
        version = versionRepository.saveAndFlush(version);

        java.util.Map<String, TmEntry> entries = new java.util.LinkedHashMap<>();
        if (parent != null) {
            for (TmEntry entry : entryRepository.findByVersionIdOrderById(parent.getId())) {
                entries.put(entry.getIdentityKey(), PublishService.copyOf(entry));
            }
        }
        for (TmxParser.Tu tu : tus) {
            String identityKey = Hashes.identityKey(tu.sourceLang(), tu.targetLang(), productLine, tu.source());
            TmEntry entry = new TmEntry();
            entry.setSourceLang(tu.sourceLang());
            entry.setTargetLang(tu.targetLang());
            entry.setProductLine(productLine);
            entry.setSourceText(tu.source());
            entry.setTargetText(tu.target());
            entry.setIdentityKey(identityKey);
            entry.setOrigin(EntryOrigin.LEGACY_IMPORT);
            entries.put(identityKey, entry);
        }
        persist(version.getId(), entries.values());
        version.setChecksum(publishService.computeChecksum(version.getId()));
        versionRepository.save(version);
        publishService.publishPointerOnly(version, actor, "legacy import " + label);
        return version;
    }

    /** Original plain term replacement: literal find-and-replace across a version's targets. */
    @Transactional
    public TmVersion replaceTerm(long versionId, String sourceTerm, String targetTerm, String actor) {
        if (sourceTerm == null || sourceTerm.isBlank()) {
            throw new BadRequestException("sourceTerm is required");
        }
        TmVersion base = versionRepository.findById(versionId)
                .orElseThrow(() -> new NotFoundException("version " + versionId));

        TmVersion version = new TmVersion();
        version.setLabel(base.getLabel() + "-term-" + targetTerm);
        version.setKind(VersionKind.LEGACY_IMPORT);
        version.setParentId(base.getId());
        version.setStatus(VersionStatus.PUBLISHED);
        version.setCreatedBy(actor);
        version = versionRepository.saveAndFlush(version);

        java.util.List<TmEntry> entries = new java.util.ArrayList<>();
        for (TmEntry entry : entryRepository.findByVersionIdOrderById(base.getId())) {
            TmEntry copy = PublishService.copyOf(entry);
            copy.setTargetText(copy.getTargetText().replace(sourceTerm, targetTerm));
            entries.add(copy);
        }
        persist(version.getId(), entries);
        version.setChecksum(publishService.computeChecksum(version.getId()));
        versionRepository.save(version);
        publishService.publishPointerOnly(version, actor, "term replace " + sourceTerm + " -> " + targetTerm);
        return version;
    }

    private void persist(Long versionId, Iterable<TmEntry> entries) {
        for (TmEntry entry : entries) {
            entry.setVersionId(versionId);
            entryRepository.save(entry);
        }
    }
}
