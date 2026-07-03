package com.taskflow.task;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface TaskRepository extends JpaRepository<Task, Long> {
    List<Task> findByBoardIdOrderByStatusAscSortOrderAsc(Long boardId);
    List<Task> findByBoardIdAndStatusOrderBySortOrderAsc(Long boardId, Task.Status status);
}
