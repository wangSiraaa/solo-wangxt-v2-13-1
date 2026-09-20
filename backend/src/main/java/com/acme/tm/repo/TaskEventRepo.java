package com.acme.tm.repo;

import com.acme.tm.model.TaskEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TaskEventRepo extends JpaRepository<TaskEvent, Long> {
    List<TaskEvent> findByTaskIdOrderByIdAsc(Long taskId);
}
