package com.tmhub.config;

import com.tmhub.service.ExportService;
import com.tmhub.service.MigrationEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * On startup, anything still marked RUNNING belongs to a dead process: mark it INTERRUPTED so the
 * operator (or an auto-resume policy) can continue it from its checkpoint.
 */
@Component
public class StartupRecovery {

    private static final Logger log = LoggerFactory.getLogger(StartupRecovery.class);

    private final MigrationEngine migrationEngine;
    private final ExportService exportService;

    public StartupRecovery(MigrationEngine migrationEngine, ExportService exportService) {
        this.migrationEngine = migrationEngine;
        this.exportService = exportService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void recover() {
        int tasks = migrationEngine.recoverInterruptedTasks();
        int exports = exportService.recoverInterruptedExports();
        if (tasks + exports > 0) {
            log.info("recovered {} migration task(s) and {} export(s) from checkpoints", tasks, exports);
        }
    }
}
