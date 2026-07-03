package com.taskflow.task;

import com.taskflow.board.Board;
import com.taskflow.user.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "tasks")
@Getter @Setter
public class Task {
    public enum Status { TODO, IN_PROGRESS, DONE }
    public enum Priority { HIGH, MEDIUM, LOW }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "board_id", nullable = false)
    private Board board;

    @Column(nullable = false, length = 300)
    private String title;

    @Column(columnDefinition = "NVARCHAR(MAX)")
    private String description;

    // 負責人限看板成員，於 Service 層驗證（DB 不設此約束，成員被移除時任務仍保留原負責人紀錄）
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assignee_id")
    private User assignee;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Priority priority = Priority.MEDIUM;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.TODO;

    // 欄內排序：同一 status 欄位內由小到大排列，拖曳時整欄重排
    @Column(name = "sort_order", nullable = false)
    private int sortOrder = 0;

    @CreationTimestamp
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
