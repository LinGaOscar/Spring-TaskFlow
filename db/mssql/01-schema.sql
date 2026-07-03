-- TaskFlow 資料庫 Schema (SQL Server)
-- departments / users 結構沿用 WbsScaff，boards/tasks/task_templates 為本系統核心

IF OBJECT_ID('dbo.departments', 'U') IS NULL
CREATE TABLE departments (
    id          BIGINT IDENTITY(1,1) PRIMARY KEY,
    name        NVARCHAR(100) NOT NULL,
    parent_id   BIGINT REFERENCES departments(id),
    created_at  DATETIME2
);

IF OBJECT_ID('dbo.users', 'U') IS NULL
CREATE TABLE users (
    id              BIGINT IDENTITY(1,1) PRIMARY KEY,
    email           NVARCHAR(200) NOT NULL UNIQUE,
    password_hash   NVARCHAR(MAX) NOT NULL,
    display_name    NVARCHAR(100) NOT NULL,
    department_id   BIGINT REFERENCES departments(id),
    role            NVARCHAR(20) NOT NULL DEFAULT 'PROJECT_MEMBER'
                        CHECK (role IN ('DIRECTOR','SECTION_CHIEF','PROJECT_LEADER','PROJECT_MEMBER')),
    enabled         BIT NOT NULL DEFAULT 1,
    created_at      DATETIME2
);

IF OBJECT_ID('dbo.boards', 'U') IS NULL
CREATE TABLE boards (
    id              BIGINT IDENTITY(1,1) PRIMARY KEY,
    name            NVARCHAR(200) NOT NULL,
    department_id   BIGINT REFERENCES departments(id),
    owner_id        BIGINT REFERENCES users(id),
    created_by      BIGINT REFERENCES users(id),
    archived        BIT NOT NULL DEFAULT 0,
    archived_at     DATETIME2,   -- 歸檔時間，供歷史區日期區間查詢
    created_at      DATETIME2,
    updated_at      DATETIME2
);

IF OBJECT_ID('dbo.board_members', 'U') IS NULL
CREATE TABLE board_members (
    board_id        BIGINT NOT NULL REFERENCES boards(id),
    user_id         BIGINT NOT NULL REFERENCES users(id),
    assigned_by     BIGINT REFERENCES users(id),
    joined_at       DATETIME2,
    PRIMARY KEY (board_id, user_id)
);

IF OBJECT_ID('dbo.tasks', 'U') IS NULL
CREATE TABLE tasks (
    id           BIGINT IDENTITY(1,1) PRIMARY KEY,
    board_id     BIGINT NOT NULL REFERENCES boards(id),
    title        NVARCHAR(300) NOT NULL,
    description  NVARCHAR(MAX),
    assignee_id  BIGINT REFERENCES users(id),
    due_date     DATE,
    priority     NVARCHAR(10) NOT NULL DEFAULT 'MEDIUM'
                     CHECK (priority IN ('HIGH','MEDIUM','LOW')),
    status       NVARCHAR(20) NOT NULL DEFAULT 'TODO'
                     CHECK (status IN ('TODO','IN_PROGRESS','DONE')),
    sort_order   INT NOT NULL DEFAULT 0,
    created_at   DATETIME2,
    updated_at   DATETIME2
);

IF OBJECT_ID('dbo.task_templates', 'U') IS NULL
CREATE TABLE task_templates (
    id          BIGINT IDENTITY(1,1) PRIMARY KEY,
    name        NVARCHAR(200) NOT NULL,
    title       NVARCHAR(300) NOT NULL,
    description NVARCHAR(MAX),
    priority    NVARCHAR(10) NOT NULL DEFAULT 'MEDIUM'
                    CHECK (priority IN ('HIGH','MEDIUM','LOW')),
    is_system   BIT NOT NULL DEFAULT 0,
    section_id  BIGINT REFERENCES departments(id),
    created_at  DATETIME2
);
