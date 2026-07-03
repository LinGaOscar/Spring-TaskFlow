# Spring-TaskFlow 設計文件

日期：2026-07-03
狀態：已與使用者確認

## 目標

依照 Spring-WbsScaff 的架構與畫面風格，建立企業後台 Kanban 任務管理系統。基礎設施（登入、權限、部門隔離、即時協作、UI 版型）自 WbsScaff 移植改造（方案 A），核心功能由 WBS 樹狀編輯器改為看板拖曳。

## 技術架構

| 層次 | 技術 |
|---|---|
| 後端 | Java 21 + Spring Boot 3.4 + Spring MVC |
| 安全 | Spring Security 6 + Spring Session JDBC（HttpOnly Cookie，無 JWT） |
| 即時協作 | Spring WebSocket + STOMP + SockJS |
| 前端 | Thymeleaf 3 + Vue 3 CDN（離線靜態），HTML5 原生 Drag & Drop |
| 資料庫 | SQL Server 2022（Docker，entrypoint 腳本初始化 `db/mssql/`） |
| 匯出 | POI（XLSX）、後端組裝（JSON / CSV） |
| 部署 | Docker Compose，`.env` 管理 `MSSQL_SA_PASSWORD`（入 `.gitignore`，附 `.env.example`） |

## 套件結構（package-by-feature，`com.taskflow`）

```
com.taskflow
├── auth/      SecurityConfig, AuthController, CustomUserDetailsService（原樣移植）
├── user/      User, UserRepository/Service/Controller（原樣移植）
├── board/     Board, BoardRepository/Service/Controller（由 project 改造）
├── task/      Task, TaskRepository/Service/Controller, TaskExportService
├── template/  TaskTemplate, TemplateRepository/Service/Controller
├── collab/    CollabController/Service, WebSocketConfig（由 wbs 協作改造）
└── config/    WebSocketSecurityConfig
```

## 頁面

| 路徑 | 頁面 | 說明 |
|---|---|---|
| `/login` | 登入 | Email + 密碼，同 WbsScaff |
| `/boards` | 看板列表 | 依角色顯示可見看板卡片網格 |
| `/boards/{id}` | Kanban 看板 | Vue 拖曳 + WebSocket 即時協作 |
| `/boards/history` | 歷史區 | 已歸檔看板，含查詢功能（全員可用） |
| `/templates` | 任務模板管理 | 系統模板唯讀 + 本科自訂模板 |
| `/admin/members` | 成員管理 | 科長 / Leader 限定 |
| 403 / 404 / 5xx | 自訂錯誤頁 | 原樣移植 |

UI 風格：複製 WbsScaff `app.css`（`#2d3436` 深色 navbar、220px 白色 sidebar、`#f5f6fa` 背景、`#0984e3` 主色藍、卡片 / modal / data-table 樣式），新增 `board.css` 提供 Kanban 三欄與卡片樣式。

## 資料模型

沿用不變：`departments`（部 → 科，`parent_id` 自關聯）、`users`（四角色 CHECK）、`SPRING_SESSION`。

更名改造：`projects` → `boards`、`project_members` → `board_members`（欄位結構相同：name、department_id、owner_id、created_by、archived）。

新增：

```sql
tasks (
    id           BIGINT IDENTITY PRIMARY KEY,
    board_id     BIGINT NOT NULL REFERENCES boards(id),
    title        NVARCHAR(300) NOT NULL,
    description  NVARCHAR(MAX),                -- 詳細描述（modal 編輯）
    assignee_id  BIGINT REFERENCES users(id),  -- 負責人（限看板成員）
    due_date     DATE,                          -- 到期日，逾期未完成顯示紅色警示
    priority     NVARCHAR(10) NOT NULL DEFAULT 'MEDIUM'
                     CHECK (priority IN ('HIGH','MEDIUM','LOW')),
    status       NVARCHAR(20) NOT NULL DEFAULT 'TODO'
                     CHECK (status IN ('TODO','IN_PROGRESS','DONE')),
    sort_order   INT NOT NULL DEFAULT 0,        -- 欄內排序
    created_at   DATETIME2,
    updated_at   DATETIME2
)

task_templates (
    id          BIGINT IDENTITY PRIMARY KEY,
    name        NVARCHAR(200) NOT NULL,   -- 模板名稱（選單顯示用）
    title       NVARCHAR(300) NOT NULL,   -- 帶入的任務標題
    description NVARCHAR(MAX),
    priority    NVARCHAR(10) NOT NULL DEFAULT 'MEDIUM',
    is_system   BIT NOT NULL DEFAULT 0,   -- 系統模板全員可見、不可改不可刪
    section_id  BIGINT REFERENCES departments(id),  -- 自訂模板歸屬科
    created_at  DATETIME2
)
```

不帶入：`wbs_nodes`、`wbs_templates`、`wbs_template_nodes`、`wbs_quick_items`。

### 種子資料（`db/mssql/02-seed.sql`）

- 與 WbsScaff 相同的部 / 科組織與測試帳號（密碼 `test1234`）
- 3 個系統任務模板：「Bug 修復」「需求開發」「例行維運」
- 不建看板與任務（業務資料由系統功能產生）

## 權限矩陣

組織：部（Division）→ 科（Section），部長屬於部、其餘屬於科。

| 角色 | 建看板 | 拖曳/編輯任務 | 管理成員 | 管理模板 | 跨科查閱 | 歸檔/還原 |
|---|:---:|:---:|:---:|:---:|:---:|:---:|
| DIRECTOR | ✅（掛部） | 僅自建看板 | 僅自建看板 | ❌ | ✅（唯讀） | ❌ |
| SECTION_CHIEF | ✅ | ✅（本科） | ✅（本科） | ✅ | ❌ | ✅（本科） |
| PROJECT_LEADER | ✅ | 限加入的看板 | 限自己的看板 | ✅ | ❌ | ✅（本科） |
| PROJECT_MEMBER | ❌ | 限加入的看板 | ❌ | ❌ | ❌ | ❌ |

- 看板建立時自動繼承建立者所屬科（部長建立掛部層級）
- 歸檔看板全員強制唯讀（工具列鎖定）
- 無權限存取 → 重導 `/boards`；不存在看板 → 自訂 404；未登入 → 重導 `/login`

## Kanban 互動（`/boards/{id}`）

- 固定三欄：待辦（TODO）/ 進行中（IN_PROGRESS）/ 完成（DONE），欄頂顯示任務數
- 卡片顯示：標題、負責人姓名、到期日（逾期未完成紅色警示）、優先級色條（高紅 / 中橙 / 低綠）
- 拖曳：跨欄 = 改狀態；欄內 = 調整 `sort_order`（HTML5 原生 Drag & Drop，不引入額外函式庫）
- 點卡片開 modal：編輯標題 / 描述 / 負責人（下拉選看板成員）/ 到期日 / 優先級；刪除需 `confirm()` 確認
- 「＋新增任務」modal 含「套用模板」下拉：選取後自動帶入標題 / 描述 / 優先級，可修改後儲存
- 工具列：新增任務、匯出下拉（JSON / CSV / XLSX）、歸檔按鈕（有權限者）

## 即時協作（WebSocket）

- STOMP topic：`/topic/board/{id}`，新增 / 修改 / 移動 / 刪除即時廣播給在線成員
- 右上角在線協作者頭像（presence-bar 樣式，同 WbsScaff）
- 每次 WebSocket 操作 server 端驗證 `canWriteBoard()`
- 歸檔後 WebSocket 寫入操作一律拒絕

## 任務模板管理（`/templates`）

| 功能 | 可操作角色 |
|---|---|
| 查看系統模板（唯讀） | 全員 |
| 查看本科自訂模板 | 全員（本科） |
| 新增 / 編輯 / 刪除自訂模板 | SECTION_CHIEF / PROJECT_LEADER（本科） |

模板欄位：名稱、任務標題、描述、優先級（扁平結構，無節點樹）。

## 歷史區（`/boards/history`）

- 全員可用查詢列：關鍵字（看板名稱模糊搜尋）+ 歸檔日期區間篩選
- 可見範圍依部門隔離：Member 限被加入過的、科長 / Leader 限本科、部長全部（唯讀）
- 點擊進入唯讀 Kanban 檢視；科長 / Leader 可還原本科看板

## 匯出

- 工具列下拉：JSON / CSV（後端組裝）、XLSX（POI）
- 內容：看板任務清單（標題、狀態、負責人、到期日、優先級、描述）
- 匯出為唯讀操作，凡可檢視該看板者（含歸檔看板與部長跨科唯讀）皆可使用

## 錯誤處理

| 狀況 | 行為 |
|---|---|
| 不存在的看板 | 自訂 404 頁（含側欄可返回） |
| 無存取權限 | 重導 `/boards` |
| 伺服器錯誤 | 自訂 5xx 頁 |
| 未登入 | 重導 `/login` |

## 測試策略

- H2 + `application-test.yml`（比照 WbsScaff）
- Repository / Service / Controller 三層測試，套件：auth、user、board、task、template、collab
- 重點案例：登入成敗、部門隔離（跨科 IDOR 拒絕）、角色權限矩陣、拖曳改狀態、模板套用、歸檔唯讀鎖定、歷史查詢篩選
