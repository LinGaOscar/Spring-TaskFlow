package com.taskflow.template;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface TemplateRepository extends JpaRepository<TaskTemplate, Long> {
    List<TaskTemplate> findBySystemTrueOrSectionIdOrderByIdAsc(Long sectionId);
}
