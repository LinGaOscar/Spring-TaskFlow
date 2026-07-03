package com.taskflow.department;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface DepartmentRepository extends JpaRepository<Department, Long> {
    // 查詢某部下所有科（部長查專案清單使用）
    List<Department> findByParentId(Long parentId);
    // 查詢所有頂層部（parent == null）
    List<Department> findByParentIsNull();
}
