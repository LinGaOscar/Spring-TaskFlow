-- TaskFlow 種子資料 (SQL Server)
-- 組織架構：資訊部 > 資訊科 / 資訊科2

-- 部門
SET IDENTITY_INSERT departments ON;
INSERT INTO departments (id, name, parent_id) VALUES
    (1, N'資訊部',  NULL),
    (2, N'資訊科',  1),
    (3, N'資訊科2', 1);
SET IDENTITY_INSERT departments OFF;
DBCC CHECKIDENT('departments', RESEED, 3);

-- 使用者（密碼統一為 test1234，BCrypt $2b$10$）
SET IDENTITY_INSERT users ON;
INSERT INTO users (id, email, password_hash, display_name, department_id, role) VALUES
    (1, N'director@company.com', N'$2b$10$A6tAs/0xEks8XeKUpdZP9OER8ZDKYEv9Tt42cQxGYnqHklF/zjaHm', N'部長',       1, N'DIRECTOR'),
    (2, N'chief@infotech.com',   N'$2b$10$A6tAs/0xEks8XeKUpdZP9OER8ZDKYEv9Tt42cQxGYnqHklF/zjaHm', N'資訊科長',   2, N'SECTION_CHIEF'),
    (3, N'leader@infotech.com',  N'$2b$10$A6tAs/0xEks8XeKUpdZP9OER8ZDKYEv9Tt42cQxGYnqHklF/zjaHm', N'資訊Leader', 2, N'PROJECT_LEADER'),
    (4, N'member1@infotech.com', N'$2b$10$A6tAs/0xEks8XeKUpdZP9OER8ZDKYEv9Tt42cQxGYnqHklF/zjaHm', N'資訊成員一', 2, N'PROJECT_MEMBER'),
    (5, N'member2@infotech.com', N'$2b$10$A6tAs/0xEks8XeKUpdZP9OER8ZDKYEv9Tt42cQxGYnqHklF/zjaHm', N'資訊成員二', 2, N'PROJECT_MEMBER'),
    (6, N'chief2@infotech.com',  N'$2b$10$A6tAs/0xEks8XeKUpdZP9OER8ZDKYEv9Tt42cQxGYnqHklF/zjaHm', N'資訊科長2',  3, N'SECTION_CHIEF'),
    (7, N'leader2@infotech.com', N'$2b$10$A6tAs/0xEks8XeKUpdZP9OER8ZDKYEv9Tt42cQxGYnqHklF/zjaHm', N'資訊Leader2',3, N'PROJECT_LEADER'),
    (8, N'member3@infotech.com', N'$2b$10$A6tAs/0xEks8XeKUpdZP9OER8ZDKYEv9Tt42cQxGYnqHklF/zjaHm', N'資訊成員三', 3, N'PROJECT_MEMBER');
SET IDENTITY_INSERT users OFF;
DBCC CHECKIDENT('users', RESEED, 8);

-- 系統任務模板：建立任務時可套用，帶入標題/描述/優先級預設值
SET IDENTITY_INSERT task_templates ON;
INSERT INTO task_templates (id, name, title, description, priority, is_system) VALUES
    (1, N'Bug 修復',  N'[Bug] ', N'【重現步驟】
【預期行為】
【實際行為】', N'HIGH',   1),
    (2, N'需求開發',  N'[需求] ', N'【需求說明】
【驗收條件】',               N'MEDIUM', 1),
    (3, N'例行維運',  N'[維運] ', N'【作業內容】
【影響範圍】',               N'LOW',    1);
SET IDENTITY_INSERT task_templates OFF;
DBCC CHECKIDENT('task_templates', RESEED, 3);
