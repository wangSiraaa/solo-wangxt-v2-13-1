package com.tmhub.service;

import com.tmhub.domain.ExportArtifact;
import com.tmhub.domain.ExportChunk;
import com.tmhub.domain.ExportStatus;
import com.tmhub.domain.ExportTask;
import com.tmhub.domain.TmEntry;
import com.tmhub.domain.TmVersion;
import com.tmhub.repo.ExportArtifactRepository;
import com.tmhub.repo.ExportChunkRepository;
import com.tmhub.repo.ExportTaskRepository;
import com.tmhub.repo.TmEntryRepository;
import com.tmhub.repo.TmVersionRepository;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Resumable TMX export. Entries are streamed into staged chunks (one transaction per chunk,
 * checkpoint = last exported entry id). After a crash the export resumes from the checkpoint and
 * appends only the remaining entries; because entries are ordered by id and formatting is fixed,
 * the resulting document — and therefore its checksum — is identical across restarts.
 */
@Service
public class ExportService {

    private final ExportTaskRepository taskRepository;
    private final ExportChunkRepository chunkRepository;
    private final ExportArtifactRepository artifactRepository;
    private final TmEntryRepository entryRepository;
    private final TmVersionRepository versionRepository;
    private final int chunkSize;
    private final java.util.function.Supplier<ExportService> self;

    public ExportService(ExportTaskRepository taskRepository, ExportChunkRepository chunkRepository,
                         ExportArtifactRepository artifactRepository, TmEntryRepository entryRepository,
                         TmVersionRepository versionRepository,
                         @Value("${tmhub.export.chunk-size:50}") int chunkSize,
                         @Lazy ExportService self) {
        this.taskRepository = taskRepository;
        this.chunkRepository = chunkRepository;
        this.artifactRepository = artifactRepository;
        this.entryRepository = entryRepository;
        this.versionRepository = versionRepository;
        this.chunkSize = chunkSize;
        this.self = () -> self;
    }

    @Transactional
    public ExportTask startExport(long versionId) {
        TmVersion version = versionRepository.findById(versionId)
                .orElseThrow(() -> new NotFoundException("version " + versionId));
        ExportTask task = new ExportTask();
        task.setVersionId(version.getId());
        task.setStatus(ExportStatus.RUNNING);
        task.setTotal((int) entryRepository.countByVersionId(versionId));
        return taskRepository.save(task);
    }

    public ExportTask run(long taskId) {
        while (true) {
            ExportTask task;
            try {
                task = self.get().processChunk(taskId);
            } catch (RuntimeException e) {
                return self.get().markFailed(taskId, e.getMessage());
            }
            if (task.getStatus() != ExportStatus.RUNNING) {
                return task;
            }
        }
    }

    public ExportTask resume(long taskId) {
        ExportTask task = taskRepository.findById(taskId)
                .orElseThrow(() -> new NotFoundException("export task " + taskId));
        if (task.getStatus() == ExportStatus.INTERRUPTED || task.getStatus() == ExportStatus.FAILED) {
            self.get().markRunning(taskId);
        }
        return run(taskId);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ExportTask markRunning(long taskId) {
        ExportTask task = taskRepository.findById(taskId).orElseThrow();
        task.setStatus(ExportStatus.RUNNING);
        task.setUpdatedAt(OffsetDateTime.now());
        return taskRepository.save(task);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ExportTask markFailed(long taskId, String error) {
        ExportTask task = taskRepository.findById(taskId).orElseThrow();
        task.setStatus(ExportStatus.FAILED);
        task.setError(error);
        task.setUpdatedAt(OffsetDateTime.now());
        return taskRepository.save(task);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ExportTask processChunk(long taskId) {
        ExportTask task = taskRepository.findById(taskId)
                .orElseThrow(() -> new NotFoundException("export task " + taskId));
        if (task.getStatus() != ExportStatus.RUNNING) {
            return task;
        }
        List<TmEntry> entries = entryRepository.findByVersionIdAndIdGreaterThanOrderById(
                task.getVersionId(), task.getLastEntryId(), PageRequest.of(0, chunkSize));
        if (entries.isEmpty()) {
            return finalizeExport(task);
        }
        StringBuilder chunkContent = new StringBuilder();
        long maxId = task.getLastEntryId();
        for (TmEntry entry : entries) {
            chunkContent.append(TmxWriter.tu(entry));
            maxId = Math.max(maxId, entry.getId());
        }
        ExportChunk chunk = new ExportChunk();
        chunk.setExportTaskId(taskId);
        chunk.setSeq(chunkRepository.findByExportTaskIdOrderBySeq(taskId).size());
        chunk.setContent(chunkContent.toString());
        chunkRepository.save(chunk);

        task.setLastEntryId(maxId);
        task.setExported(task.getExported() + entries.size());
        task.setUpdatedAt(OffsetDateTime.now());
        return taskRepository.save(task);
    }

    private ExportTask finalizeExport(ExportTask task) {
        List<ExportChunk> chunks = chunkRepository.findByExportTaskIdOrderBySeq(task.getId());
        StringBuilder body = new StringBuilder();
        for (ExportChunk chunk : chunks) {
            body.append(chunk.getContent());
        }
        String document = TmxWriter.document(body.toString());
        String checksum = Hashes.sha256(document);

        ExportArtifact artifact = new ExportArtifact();
        artifact.setVersionId(task.getVersionId());
        artifact.setContent(document);
        artifact.setChecksum(checksum);
        artifactRepository.save(artifact);

        task.setStatus(ExportStatus.COMPLETED);
        task.setChecksum(checksum);
        task.setDownloadUrl("/api/versions/" + task.getVersionId() + "/export");
        task.setUpdatedAt(OffsetDateTime.now());
        return taskRepository.save(task);
    }

    /** Recovery after process restart: RUNNING exports become INTERRUPTED, checkpoint intact. */
    @Transactional
    public int recoverInterruptedExports() {
        int recovered = 0;
        for (ExportTask task : taskRepository.findByStatus(ExportStatus.RUNNING)) {
            task.setStatus(ExportStatus.INTERRUPTED);
            task.setError("process interrupted; checkpoint at entry " + task.getLastEntryId());
            task.setUpdatedAt(OffsetDateTime.now());
            taskRepository.save(task);
            recovered++;
        }
        return recovered;
    }

    @Transactional(readOnly = true)
    public ExportArtifact latestArtifact(long versionId) {
        return artifactRepository.findByVersionIdOrderByIdDesc(versionId).stream()
                .findFirst()
                .orElseThrow(() -> new NotFoundException("no export artifact for version " + versionId));
    }

    @Transactional(readOnly = true)
    public List<ExportTask> tasksOf(long versionId) {
        return taskRepository.findByVersionIdOrderByIdDesc(versionId);
    }

    @Transactional(readOnly = true)
    public ExportTask getTask(long taskId) {
        return taskRepository.findById(taskId)
                .orElseThrow(() -> new NotFoundException("export task " + taskId));
    }
}
