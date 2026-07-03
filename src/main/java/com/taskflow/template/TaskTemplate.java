package com.taskflow.template;

import com.taskflow.department.Department;
import com.taskflow.task.Task;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDateTime;

@Entity
@Table(name = "task_templates")
@Getter @Setter
public class TaskTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String name;      // 模板名稱（下拉選單顯示）

    @Column(nullable = false, length = 300)
    private String title;     // 套用時帶入的任務標題

    @Column(columnDefinition = "NVARCHAR(MAX)")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Task.Priority priority = Task.Priority.MEDIUM;

    // 系統模板全員可見且不可改刪；自訂模板以科為共用單位
    @Column(name = "is_system", nullable = false)
    private boolean system = false;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "section_id")
    private Department section;

    @CreationTimestamp
    @Column(name = "created_at")
    private LocalDateTime createdAt;
}
