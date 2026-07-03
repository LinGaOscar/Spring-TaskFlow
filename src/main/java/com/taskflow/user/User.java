package com.taskflow.user;

import com.taskflow.department.Department;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@Getter @Setter
public class User {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false, length = 200)
    private String email;

    @Column(nullable = false)
    private String passwordHash;

    @Column(nullable = false, length = 100)
    private String displayName;

    // 部（parent_id IS NULL）或科（parent_id NOT NULL）都可作為 department
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department_id")
    private Department department;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role = Role.PROJECT_MEMBER;

    // 軟刪除旗標：false 代表停用，不會出現在成員選單與登入驗證
    private boolean enabled = true;

    @CreationTimestamp
    private LocalDateTime createdAt;

    // 部長、科長、Leader 都可建立看板
    public boolean canCreateBoard() {
        return role == Role.DIRECTOR || role == Role.SECTION_CHIEF || role == Role.PROJECT_LEADER;
    }

    // 科長與看板Leader才能管理本科的模板與快速子項
    public boolean canManageSection() {
        return role == Role.SECTION_CHIEF || role == Role.PROJECT_LEADER;
    }

    public enum Role {
        DIRECTOR,        // 部長：唯讀查看本部下所有科的看板
        SECTION_CHIEF,   // 科長：管理本科所有看板、成員、模板、快速子項
        PROJECT_LEADER,  // 看板Leader：建立看板、編輯WBS、管理自己的看板成員
        PROJECT_MEMBER   // 看板Member：只能查看/編輯被加入的看板
    }
}
