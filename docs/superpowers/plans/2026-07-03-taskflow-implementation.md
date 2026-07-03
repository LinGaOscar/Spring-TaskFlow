# Spring-TaskFlow 實作計畫

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 依 `docs/superpowers/specs/2026-07-03-taskflow-design.md`，建立與 Spring-WbsScaff 同架構、同畫面風格的 Kanban 任務管理系統。

**Architecture:** 自本機參考專案 `/Users/oscarlin/Documents/GitHub/Spring-WbsScaff`（下稱 `$WBS`）移植基礎設施（auth/user/common/department、Session、WebSocket 設定、CSS 版型），核心 board/task/template/collab/export 模組全新實作。SSR（Thymeleaf）出頁面骨架，Vue 3 CDN 負責 Kanban 互動，寫入操作走 STOMP WebSocket 並廣播同步。

**Tech Stack:** Java 21、Spring Boot 3.4.0、Spring Security 6 + Spring Session JDBC、Thymeleaf 3、Vue 3（離線 CDN）、SockJS/STOMP、SQL Server 2022（Docker）、POI 5.3.0、H2（測試）、Maven。

## Global Constraints

- 套件根：`com.taskflow`；artifactId：`spring-taskflow`；DB 名：`taskflow`；埠：8080
- 參考專案路徑變數：`WBS=/Users/oscarlin/Documents/GitHub/Spring-WbsScaff`（所有 `cp` 來源）
- 角色固定四種：`DIRECTOR` / `SECTION_CHIEF` / `PROJECT_LEADER` / `PROJECT_MEMBER`
- 任務狀態固定三種：`TODO` / `IN_PROGRESS` / `DONE`；優先級：`HIGH` / `MEDIUM` / `LOW`
- 密碼、token 一律走 `.env`（`MSSQL_SA_PASSWORD`），`.env` 不入版控、`.env.example` 入版控
- 程式註解一律繁體中文、說明業務理由（Why）；不留 TODO 註解
- UI 文案繁體中文；視覺沿用 WbsScaff：navbar `#2d3436`、背景 `#f5f6fa`、主色 `#0984e3`
- 測試走 H2 + `application-test.yml`（自 `$WBS` 複製），`@ActiveProfiles("test")`
- macOS sed 就地取代語法：`LC_ALL=C sed -i '' 's/.../.../g' <file>`
- 每個 Task 結尾 commit（訊息格式 `feat:`/`test:`/`docs:`），**不推送**（推送需使用者說「推送」）

---

### Task 1: 專案骨架與 Docker 資料庫

**Files:**
- Create: `pom.xml`、`.gitignore`、`.env.example`、`.env`（不入版控）、`docker-compose.yml`
- Create: `db/mssql/entrypoint.sh`、`db/mssql/01-schema.sql`、`db/mssql/02-seed.sql`
- Create: `src/main/java/com/taskflow/TaskFlowApplication.java`
- Create: `src/main/resources/application.yml`
- Test: `src/test/java/com/taskflow/TaskFlowApplicationTests.java`、`src/test/resources/application-test.yml`

**Interfaces:**
- Produces: 可編譯、可啟動的空 Spring Boot 專案；`docker compose up -d` 自動建 schema 與種子資料；後續所有 Task 依賴的資料表（departments/users/boards/board_members/tasks/task_templates）

- [ ] **Step 1: 複製並改造 Maven 與環境設定檔**

```bash
WBS=/Users/oscarlin/Documents/GitHub/Spring-WbsScaff
cp $WBS/pom.xml $WBS/.gitignore $WBS/.env.example $WBS/docker-compose.yml $WBS/Dockerfile .
LC_ALL=C sed -i '' 's/spring-wbsscaff/spring-taskflow/g; s/wbsscaff/taskflow/g; s/WbsScaff/TaskFlow/g; s/wbs-scaff/task-flow/g' pom.xml docker-compose.yml Dockerfile
```

`docker-compose.yml` 中 volume 掛載改為 `./db/mssql:/init`（若 sed 後已如此則不動）。確認 `.gitignore` 含 `.env`、`target/`。建立本機 `.env`：

```bash
cp .env.example .env   # 內容：MSSQL_SA_PASSWORD=<自訂強密碼，如 TaskFlow!2026Dev>
```

- [ ] **Step 2: 建立 Application 進入點與設定檔**

`src/main/java/com/taskflow/TaskFlowApplication.java`：

```java
package com.taskflow;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class TaskFlowApplication {
    public static void main(String[] args) {
        SpringApplication.run(TaskFlowApplication.class, args);
    }
}
```

```bash
cp $WBS/src/main/resources/application.yml src/main/resources/application.yml
LC_ALL=C sed -i '' 's/wbsscaff/taskflow/g' src/main/resources/application.yml
```

（結果：datasource url `databaseName=taskflow`、logging level key `com.taskflow`，其餘不變。）

- [ ] **Step 3: 撰寫 schema 與種子資料**

`db/mssql/entrypoint.sh`：複製後把資料庫名換掉：

```bash
mkdir -p db/mssql
cp $WBS/db/mssql/entrypoint.sh db/mssql/entrypoint.sh
LC_ALL=C sed -i '' 's/wbsscaff/taskflow/g' db/mssql/entrypoint.sh
```

`db/mssql/01-schema.sql`（全新撰寫）：

```sql
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
```

`db/mssql/02-seed.sql`（部門與帳號段直接沿用 WbsScaff，模板段全新）：

```bash
# 先複製取得部門/使用者 INSERT 段（含 BCrypt 雜湊），再手動改寫模板段
cp $WBS/db/mssql/02-seed.sql db/mssql/02-seed.sql
```

保留 `departments` 與 `users` 兩段 INSERT 原樣，**刪除** `wbs_templates` 以下所有內容，改為：

```sql
-- 系統任務模板：建立任務時可套用，帶入標題/描述/優先級預設值
SET IDENTITY_INSERT task_templates ON;
INSERT INTO task_templates (id, name, title, description, priority, is_system) VALUES
    (1, N'Bug 修復',  N'[Bug] ', N'【重現步驟】%0A【預期行為】%0A【實際行為】', N'HIGH',   1),
    (2, N'需求開發',  N'[需求] ', N'【需求說明】%0A【驗收條件】',               N'MEDIUM', 1),
    (3, N'例行維運',  N'[維運] ', N'【作業內容】%0A【影響範圍】',               N'LOW',    1);
SET IDENTITY_INSERT task_templates OFF;
DBCC CHECKIDENT('task_templates', RESEED, 3);
```

（注意：SQL 內換行以 CHAR(10) 不易維護，直接寫成多行 N'...' 字串亦可；描述文字內容照上，`%0A` 改為實際換行。）

- [ ] **Step 4: 複製測試設定並撰寫 context 測試**

```bash
mkdir -p src/test/resources src/test/java/com/taskflow
cp $WBS/src/test/resources/application-test.yml src/test/resources/application-test.yml
LC_ALL=C sed -i '' 's/wbsscaff/taskflow/g; s/com.wbsscaff/com.taskflow/g' src/test/resources/application-test.yml
```

`src/test/java/com/taskflow/TaskFlowApplicationTests.java`：

```java
package com.taskflow;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class TaskFlowApplicationTests {
    @Test
    void contextLoads() {
    }
}
```

- [ ] **Step 5: 驗證編譯與測試**

Run: `mvn test`
Expected: BUILD SUCCESS，`contextLoads` PASS（此時尚無任何 starter 依賴衝突）

- [ ] **Step 6: 驗證 Docker 資料庫初始化**

Run: `docker compose up -d && sleep 60 && docker compose logs db | tail -5`
Expected: log 出現 `>>> 初始化完成`

- [ ] **Step 7: Commit**

```bash
git add pom.xml .gitignore .env.example docker-compose.yml Dockerfile db src
git commit -m "feat: 專案骨架與 SQL Server Docker 初始化"
```

---

### Task 2: 移植 common / department / auth / user 模組

**Files:**
- Create: `src/main/java/com/taskflow/{common,department,auth,user}/*.java`（自 `$WBS` 複製改名）
- Test: `src/test/java/com/taskflow/{auth,user}/*.java`（自 `$WBS` 複製改名）

**Interfaces:**
- Produces（後續 Task 依賴，簽名與 WbsScaff 相同）:
  - `User`（entity；`User.Role` enum：DIRECTOR/SECTION_CHIEF/PROJECT_LEADER/PROJECT_MEMBER；`getDepartment()`、`getDisplayName()`、`getEmail()`）
  - `UserRepository.findByEmail(String): Optional<User>`
  - `Department`（`getId()`、`getParent()`）、`DepartmentRepository.findByParentId(Long): List<Department>`
  - `SecurityConfig`（`/login`、`/css/**`、`/js/**`、`/ws/**` permitAll；登入成功導 `/boards`）
  - `GlobalExceptionHandler`（`EntityNotFoundException`→404 頁、`SecurityException`→redirect `/boards`）
  - `ApiResponse`（REST 回應包裝）

- [ ] **Step 1: 複製四個模組並整批改名**

```bash
WBS=/Users/oscarlin/Documents/GitHub/Spring-WbsScaff
for m in common department auth user; do
  mkdir -p src/main/java/com/taskflow/$m
  cp $WBS/src/main/java/com/wbsscaff/$m/*.java src/main/java/com/taskflow/$m/
done
LC_ALL=C find src/main/java/com/taskflow -name '*.java' -exec sed -i '' \
  's/com\.wbsscaff/com.taskflow/g' {} +
```

- [ ] **Step 2: 修正導向路徑與文案**

auth/user/common 模組內所有 `/projects` 字串改 `/boards`、「專案」文案改「看板」：

```bash
LC_ALL=C find src/main/java/com/taskflow/{common,auth,user} -name '*.java' -exec sed -i '' \
  's|/projects|/boards|g; s/專案/看板/g; s/WBS Scaffold/TaskFlow/g; s/WbsScaff/TaskFlow/g' {} +
```

開啟 `SecurityConfig.java` 確認 `defaultSuccessUrl("/boards", true)`。若 auth/user 模組 import 了 `com.taskflow.project` 或 `com.taskflow.wbs`（不存在的套件），先以區塊註解暫時移除該引用與相關方法，並記錄於 Task 3 恢復（實際檢查 WbsScaff 原始碼：auth/user 不依賴 project/wbs，正常情況無需此步）。

- [ ] **Step 3: 複製對應測試**

```bash
for m in auth user; do
  mkdir -p src/test/java/com/taskflow/$m
  cp $WBS/src/test/java/com/wbsscaff/$m/*.java src/test/java/com/taskflow/$m/
done
LC_ALL=C find src/test/java/com/taskflow -name '*.java' -exec sed -i '' \
  's/com\.wbsscaff/com.taskflow/g; s|/projects|/boards|g; s/專案/看板/g' {} +
```

若測試引用尚未存在的模組（如 project），將該測試檔暫移至 `src/test/java/.disabled/`（Task 3 完成後移回）。

- [ ] **Step 4: 執行測試**

Run: `mvn test`
Expected: BUILD SUCCESS，auth/user 測試全 PASS

- [ ] **Step 5: Commit**

```bash
git add src
git commit -m "feat: 移植 common/department/auth/user 模組（含測試）"
```

---

### Task 3: board 模組（看板 CRUD、權限、歸檔）

**Files:**
- Create: `src/main/java/com/taskflow/board/Board.java`、`BoardMember.java`、`BoardMemberId.java`、`BoardRepository.java`、`BoardMemberRepository.java`、`BoardService.java`、`BoardDto.java`、`BoardController.java`
- Test: `src/test/java/com/taskflow/board/BoardServiceTest.java`、`BoardRepositoryTest.java`、`BoardControllerTest.java`

**Interfaces:**
- Consumes: `User`、`UserRepository`、`Department`、`DepartmentRepository`（Task 2）
- Produces:
  - `BoardService.canWriteBoard(Long boardId, User user): boolean`（歸檔一律 false；DIRECTOR 僅自建；CHIEF 本科；LEADER/MEMBER 限成員）
  - `BoardService.canReadBoard(Long boardId, User user): boolean`
  - `BoardService.getById(Long): Board`（不存在拋 `EntityNotFoundException`）
  - `BoardService.listForUser(User, boolean archived): List<Board>`
  - `BoardService.searchHistory(User, String keyword, LocalDate from, LocalDate to): List<Board>`
  - `BoardService.archiveBoard/unarchiveBoard(Long, User)`、`addMember/removeMember/changeOwner`、`isMember(Long, Long)`
  - REST：`POST /api/boards`、`POST /api/boards/{id}/archive|unarchive`、`GET/POST/DELETE /api/boards/{id}/members`、`PUT /api/boards/{id}/owner`

- [ ] **Step 1: 以 WbsScaff project 模組為底複製改名**

```bash
WBS=/Users/oscarlin/Documents/GitHub/Spring-WbsScaff
mkdir -p src/main/java/com/taskflow/board
for f in Project ProjectMember ProjectMemberId ProjectRepository ProjectMemberRepository ProjectService ProjectDto ProjectController; do
  cp $WBS/src/main/java/com/wbsscaff/project/$f.java \
     src/main/java/com/taskflow/board/$(echo $f | sed 's/Project/Board/').java
done
LC_ALL=C find src/main/java/com/taskflow/board -name '*.java' -exec sed -i '' \
  's/com\.wbsscaff/com.taskflow/g; s/\.project\b/.board/g; s/package com.taskflow.project/package com.taskflow.board/; s/Project/Board/g; s/project/board/g; s/專案/看板/g' {} +
```

改名後逐檔開啟檢查：entity `@Table(name = "boards")`、`board_members`、外鍵欄位 `board_id`；Repository 查詢方法與 `@Query` 內 `p.department.id` 等別名一致；`BoardController` 的 view 名稱為 `board/list`、`board/detail`、`board/history`，redirect 為 `/boards`。

- [ ] **Step 2: 加入 archived_at 與歷史查詢**

`Board.java` 加欄位：

```java
    // 歸檔時間：供歷史區日期區間查詢；還原時清空
    @Column(name = "archived_at")
    private java.time.LocalDateTime archivedAt;
```

`BoardService.archiveBoard` 設定 `b.setArchivedAt(LocalDateTime.now())`；`unarchiveBoard` 設 `null`。新增：

```java
    // 歷史查詢全員可用，但可見範圍仍依部門隔離（沿用 listForUser 的角色規則）
    @Transactional(readOnly = true)
    public List<Board> searchHistory(User user, String keyword, LocalDate from, LocalDate to) {
        return listForUser(user, true).stream()
            .filter(b -> keyword == null || keyword.isBlank()
                || b.getName().toLowerCase().contains(keyword.toLowerCase()))
            .filter(b -> from == null || (b.getArchivedAt() != null
                && !b.getArchivedAt().toLocalDate().isBefore(from)))
            .filter(b -> to == null || (b.getArchivedAt() != null
                && !b.getArchivedAt().toLocalDate().isAfter(to)))
            .toList();
    }
```

`BoardController` 歷史頁路由改為接查詢參數：

```java
    // 歷史區：全員可查詢，可見範圍依角色部門隔離
    @GetMapping("/boards/history")
    public String history(@RequestParam(required = false) String keyword,
                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                          Model model, Principal principal) {
        User user = userRepository.findByEmail(principal.getName()).orElseThrow();
        model.addAttribute("boards", boardService.searchHistory(user, keyword, from, to));
        model.addAttribute("keyword", keyword);
        model.addAttribute("from", from);
        model.addAttribute("to", to);
        return "board/history";
    }
```

- [ ] **Step 3: 複製並改名測試，恢復 Task 2 暫停用的測試**

```bash
mkdir -p src/test/java/com/taskflow/board
for f in ProjectServiceTest ProjectRepositoryTest ProjectControllerTest; do
  cp $WBS/src/test/java/com/wbsscaff/project/$f.java \
     src/test/java/com/taskflow/board/$(echo $f | sed 's/Project/Board/').java
done
LC_ALL=C find src/test/java/com/taskflow/board -name '*.java' -exec sed -i '' \
  's/com\.wbsscaff/com.taskflow/g; s/\.project\b/.board/g; s/Project/Board/g; s/project/board/g; s/專案/看板/g' {} +
# 移回 Task 2 暫停用的測試（若有）
[ -d src/test/java/.disabled ] && git mv src/test/java/.disabled/* src/test/java/com/taskflow/ 2>/dev/null || true
```

測試中引用 wbs 節點的案例（若有）整段刪除——TaskFlow 無 WBS 節點。

- [ ] **Step 4: 新增歷史查詢測試**

`BoardServiceTest.java` 追加：

```java
    @Test
    void searchHistory_依關鍵字與日期區間過濾() {
        // 建兩個歸檔看板：名稱不同、歸檔日不同
        Board a = boardService.createBoard(createReq("2025 網站改版"), chief.getId());
        Board b = boardService.createBoard(createReq("內部工具"), chief.getId());
        boardService.archiveBoard(a.getId(), chief);
        boardService.archiveBoard(b.getId(), chief);

        List<Board> byKeyword = boardService.searchHistory(chief, "網站", null, null);
        assertThat(byKeyword).extracting(Board::getName).containsExactly("2025 網站改版");

        List<Board> byDate = boardService.searchHistory(chief, null,
            LocalDate.now().plusDays(1), null);   // 起日在未來 → 應查無資料
        assertThat(byDate).isEmpty();
    }
```

（`createReq`/`chief` 沿用複製來的測試 fixture；命名以檔內既有者為準。）

- [ ] **Step 5: 執行測試**

Run: `mvn test`
Expected: BUILD SUCCESS，board 模組測試全 PASS（含 `searchHistory_依關鍵字與日期區間過濾`）

- [ ] **Step 6: Commit**

```bash
git add src
git commit -m "feat: board 模組（權限矩陣、歸檔、歷史查詢）"
```

---

### Task 4: 靜態資源與基礎版型（登入頁、看板列表、歷史頁）

**Files:**
- Create: `src/main/resources/static/css/app.css`、`static/js/{vue.global.prod.min.js,sockjs.min.js,stomp.min.js,xlsx.min.js}`（自 `$WBS` 複製）
- Create: `src/main/resources/templates/fragments/{header,sidebar,footer}.html`、`templates/auth/login.html`、`templates/board/list.html`、`templates/board/history.html`、`templates/error/{403,404,5xx}.html`

**Interfaces:**
- Consumes: `BoardController` 的 view 名稱與 model 屬性（Task 3）
- Produces: 全站共用 fragments（`fragments/header :: header`、`fragments/sidebar :: sidebar`、`fragments/footer :: footer`）；sidebar 導覽連結：看板列表 `/boards`、歷史區 `/boards/history`、任務模板 `/templates`、成員管理 `/admin/members`

- [ ] **Step 1: 複製靜態資源與模板**

```bash
WBS=/Users/oscarlin/Documents/GitHub/Spring-WbsScaff
mkdir -p src/main/resources/static/{css,js} src/main/resources/templates/{fragments,auth,board,error}
cp $WBS/src/main/resources/static/css/app.css src/main/resources/static/css/
cp $WBS/src/main/resources/static/js/{vue.global.prod.min.js,sockjs.min.js,stomp.min.js,xlsx.min.js} src/main/resources/static/js/
cp $WBS/src/main/resources/templates/fragments/*.html src/main/resources/templates/fragments/
cp $WBS/src/main/resources/templates/auth/login.html src/main/resources/templates/auth/
cp $WBS/src/main/resources/templates/error/*.html src/main/resources/templates/error/
cp $WBS/src/main/resources/templates/project/list.html src/main/resources/templates/board/list.html
cp $WBS/src/main/resources/templates/project/history.html src/main/resources/templates/board/history.html
```

- [ ] **Step 2: 整批改文案與路徑**

```bash
LC_ALL=C find src/main/resources/templates -name '*.html' -exec sed -i '' \
  's|/projects|/boards|g; s/專案/看板/g; s/WBS Scaffold/TaskFlow/g; s/WbsScaff/TaskFlow/g; s/project/board/g; s/Project/Board/g' {} +
```

逐檔檢查：`sidebar.html` 導覽最終為四項——看板列表 `/boards`、歷史區 `/boards/history`、任務模板 `/templates`、成員管理 `/admin/members`（管理項以 `sec:authorize="hasAnyAuthority('SECTION_CHIEF','PROJECT_LEADER')"` 包住，比照 WbsScaff 原寫法）；刪除 WBS 特有導覽（快速子項等）。`login.html` 標題改「TaskFlow 任務管理系統」。

- [ ] **Step 3: 調整歷史頁加查詢列**

`board/history.html` 在列表上方加（樣式沿用 `app.css` 既有 class）：

```html
<form class="page-header" method="get" th:action="@{/boards/history}">
    <div style="display:flex; gap:0.5rem; align-items:center;">
        <input type="text" name="keyword" th:value="${keyword}" placeholder="搜尋看板名稱"
               style="padding:0.45rem 0.8rem; border:1px solid #b2bec3; border-radius:4px;">
        <input type="date" name="from" th:value="${from}"
               style="padding:0.4rem; border:1px solid #b2bec3; border-radius:4px;">
        <span>～</span>
        <input type="date" name="to" th:value="${to}"
               style="padding:0.4rem; border:1px solid #b2bec3; border-radius:4px;">
        <button type="submit" class="btn btn-primary btn-sm">查詢</button>
    </div>
</form>
```

- [ ] **Step 4: 啟動煙霧測試**

Run: `docker compose up -d && mvn spring-boot:run`（背景）→ 瀏覽 `http://localhost:8080/login`
Expected: 登入頁樣式與 WbsScaff 一致；以 `chief@infotech.com` / `test1234` 登入後導向 `/boards` 顯示空看板列表；`/boards/history` 顯示查詢列；停止應用

- [ ] **Step 5: Commit**

```bash
git add src
git commit -m "feat: 靜態資源與版型（登入/看板列表/歷史查詢頁）"
```

---

### Task 5: task 模組後端（任務 CRUD 與拖曳排序）

**Files:**
- Create: `src/main/java/com/taskflow/task/Task.java`、`TaskDto.java`、`TaskRepository.java`、`TaskService.java`、`TaskController.java`
- Test: `src/test/java/com/taskflow/task/TaskServiceTest.java`

**Interfaces:**
- Consumes: `BoardService.canWriteBoard/canReadBoard/getById`（Task 3）、`User`/`UserRepository`（Task 2）
- Produces:
  - `Task` entity（getter：`getId/getTitle/getDescription/getAssignee/getDueDate/getPriority/getStatus/getSortOrder`）
  - `Task.Status` enum：TODO/IN_PROGRESS/DONE；`Task.Priority` enum：HIGH/MEDIUM/LOW
  - `TaskService.listByBoard(Long): List<Task>`、`createTask(Long boardId, TaskDto.SaveRequest, User): Task`、`updateTask(Long boardId, Long taskId, TaskDto.SaveRequest, User): Task`、`moveTask(Long boardId, Long taskId, TaskDto.MoveRequest, User): Task`、`deleteTask(Long boardId, Long taskId, User)`
  - `TaskDto.Response.from(Task): Response`（含 assigneeId/assigneeName）
  - REST：`GET /api/boards/{boardId}/tasks`（Task 8 的 WebSocket 負責寫入，REST 只提供初始載入）

- [ ] **Step 1: 撰寫 entity 與 DTO**

`src/main/java/com/taskflow/task/Task.java`：

```java
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
```

`src/main/java/com/taskflow/task/TaskDto.java`：

```java
package com.taskflow.task;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.time.LocalDate;

public class TaskDto {

    @Data
    public static class SaveRequest {
        @NotBlank(message = "標題不可為空")
        private String title;
        private String description;
        private Long assigneeId;
        private LocalDate dueDate;
        private Task.Priority priority = Task.Priority.MEDIUM;
    }

    @Data
    public static class MoveRequest {
        @NotNull
        private Task.Status status;   // 目標欄
        private int targetIndex;      // 目標欄內的插入位置（0-based）
    }

    @Data
    public static class Response {
        private Long id;
        private String title;
        private String description;
        private Long assigneeId;
        private String assigneeName;
        private LocalDate dueDate;
        private Task.Priority priority;
        private Task.Status status;
        private int sortOrder;

        public static Response from(Task t) {
            Response r = new Response();
            r.id = t.getId();
            r.title = t.getTitle();
            r.description = t.getDescription();
            if (t.getAssignee() != null) {
                r.assigneeId = t.getAssignee().getId();
                r.assigneeName = t.getAssignee().getDisplayName();
            }
            r.dueDate = t.getDueDate();
            r.priority = t.getPriority();
            r.status = t.getStatus();
            r.sortOrder = t.getSortOrder();
            return r;
        }
    }
}
```

- [ ] **Step 2: 撰寫 Repository**

`src/main/java/com/taskflow/task/TaskRepository.java`：

```java
package com.taskflow.task;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface TaskRepository extends JpaRepository<Task, Long> {
    List<Task> findByBoardIdOrderByStatusAscSortOrderAsc(Long boardId);
    List<Task> findByBoardIdAndStatusOrderBySortOrderAsc(Long boardId, Task.Status status);
}
```

- [ ] **Step 3: 撰寫失敗測試**

`src/test/java/com/taskflow/task/TaskServiceTest.java`（fixture 建立方式比照 `BoardServiceTest` 檔內既有寫法——直接以 repository 存 Department/User/Board）：

```java
package com.taskflow.task;

import com.taskflow.board.Board;
import com.taskflow.board.BoardService;
import com.taskflow.department.Department;
import com.taskflow.department.DepartmentRepository;
import com.taskflow.user.User;
import com.taskflow.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class TaskServiceTest {

    @Autowired TaskService taskService;
    @Autowired BoardService boardService;
    @Autowired UserRepository userRepository;
    @Autowired DepartmentRepository departmentRepository;

    User chief;
    User member;
    User outsider;   // 他科成員，無看板權限
    Board board;

    @BeforeEach
    void setUp() {
        Department div = new Department();
        div.setName("資訊部");
        departmentRepository.save(div);
        Department sec = new Department();
        sec.setName("資訊科");
        sec.setParent(div);
        departmentRepository.save(sec);
        Department sec2 = new Department();
        sec2.setName("資訊科2");
        sec2.setParent(div);
        departmentRepository.save(sec2);

        chief = newUser("chief@t.com", User.Role.SECTION_CHIEF, sec);
        member = newUser("member@t.com", User.Role.PROJECT_MEMBER, sec);
        outsider = newUser("out@t.com", User.Role.PROJECT_MEMBER, sec2);

        var req = new com.taskflow.board.BoardDto.CreateRequest();
        req.setName("測試看板");
        board = boardService.createBoard(req, chief.getId());
        boardService.addMember(board.getId(), member.getId(), chief.getId());
    }

    User newUser(String email, User.Role role, Department dept) {
        User u = new User();
        u.setEmail(email);
        u.setPasswordHash("x");
        u.setDisplayName(email);
        u.setRole(role);
        u.setDepartment(dept);
        return userRepository.save(u);
    }

    TaskDto.SaveRequest save(String title) {
        TaskDto.SaveRequest r = new TaskDto.SaveRequest();
        r.setTitle(title);
        return r;
    }

    @Test
    void 建立任務_預設進入TODO欄尾端() {
        Task t1 = taskService.createTask(board.getId(), save("任務一"), member);
        Task t2 = taskService.createTask(board.getId(), save("任務二"), member);
        assertThat(t1.getStatus()).isEqualTo(Task.Status.TODO);
        assertThat(t2.getSortOrder()).isGreaterThan(t1.getSortOrder());
    }

    @Test
    void 無權限者建立任務_被拒() {
        assertThatThrownBy(() -> taskService.createTask(board.getId(), save("偷渡"), outsider))
            .isInstanceOf(SecurityException.class);
    }

    @Test
    void 跨欄移動_改變狀態並插入指定位置() {
        Task a = taskService.createTask(board.getId(), save("A"), member);
        Task b = taskService.createTask(board.getId(), save("B"), member);
        taskService.createTask(board.getId(), save("C"), member);

        TaskDto.MoveRequest mv = new TaskDto.MoveRequest();
        mv.setStatus(Task.Status.IN_PROGRESS);
        mv.setTargetIndex(0);
        taskService.moveTask(board.getId(), a.getId(), mv, member);

        List<Task> inProgress = taskService.listByBoard(board.getId()).stream()
            .filter(t -> t.getStatus() == Task.Status.IN_PROGRESS).toList();
        assertThat(inProgress).extracting(Task::getTitle).containsExactly("A");

        // 欄內移動：B 移到 TODO 欄第 0 位（原順序 B,C → 移後仍 B,C；改移 C 到 0 → C,B）
        List<Task> todo = taskService.listByBoard(board.getId()).stream()
            .filter(t -> t.getStatus() == Task.Status.TODO).toList();
        TaskDto.MoveRequest mv2 = new TaskDto.MoveRequest();
        mv2.setStatus(Task.Status.TODO);
        mv2.setTargetIndex(0);
        taskService.moveTask(board.getId(), todo.get(1).getId(), mv2, member);
        List<Task> after = taskService.listByBoard(board.getId()).stream()
            .filter(t -> t.getStatus() == Task.Status.TODO).toList();
        assertThat(after).extracting(Task::getTitle).containsExactly("C", "B");
    }

    @Test
    void 更新任務_可指派看板成員為負責人() {
        Task t = taskService.createTask(board.getId(), save("指派"), chief);
        TaskDto.SaveRequest req = save("指派");
        req.setAssigneeId(member.getId());
        Task updated = taskService.updateTask(board.getId(), t.getId(), req, chief);
        assertThat(updated.getAssignee().getId()).isEqualTo(member.getId());
    }

    @Test
    void 指派非看板成員_被拒() {
        Task t = taskService.createTask(board.getId(), save("指派"), chief);
        TaskDto.SaveRequest req = save("指派");
        req.setAssigneeId(outsider.getId());
        assertThatThrownBy(() -> taskService.updateTask(board.getId(), t.getId(), req, chief))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 刪除任務() {
        Task t = taskService.createTask(board.getId(), save("刪我"), member);
        taskService.deleteTask(board.getId(), t.getId(), member);
        assertThat(taskService.listByBoard(board.getId())).isEmpty();
    }

    @Test
    void 歸檔看板_所有寫入被拒() {
        Task t = taskService.createTask(board.getId(), save("先建"), chief);
        boardService.archiveBoard(board.getId(), chief);
        assertThatThrownBy(() -> taskService.createTask(board.getId(), save("不行"), chief))
            .isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> taskService.deleteTask(board.getId(), t.getId(), chief))
            .isInstanceOf(SecurityException.class);
    }
}
```

（`BoardDto.CreateRequest` 命名以 Task 3 sed 改名後實際為準；若為 `BoardDto.CreateRequest` 以外的名稱，測試同步調整。）

- [ ] **Step 4: 執行測試確認失敗**

Run: `mvn test -Dtest=TaskServiceTest`
Expected: 編譯失敗（`TaskService` 不存在）

- [ ] **Step 5: 撰寫 Service 與 Controller**

`src/main/java/com/taskflow/task/TaskService.java`：

```java
package com.taskflow.task;

import com.taskflow.board.Board;
import com.taskflow.board.BoardService;
import com.taskflow.user.User;
import com.taskflow.user.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TaskService {

    private final TaskRepository taskRepository;
    private final BoardService boardService;
    private final UserRepository userRepository;

    // 所有寫入操作的統一安全閘：沿用看板權限（歸檔、跨科、非成員均被拒）
    private void checkWrite(Long boardId, User user) {
        if (!boardService.canWriteBoard(boardId, user)) {
            throw new SecurityException("無編輯此看板的權限");
        }
    }

    @Transactional(readOnly = true)
    public List<Task> listByBoard(Long boardId) {
        return taskRepository.findByBoardIdOrderByStatusAscSortOrderAsc(boardId);
    }

    @Transactional
    public Task createTask(Long boardId, TaskDto.SaveRequest req, User caller) {
        checkWrite(boardId, caller);
        Board board = boardService.getById(boardId);
        Task t = new Task();
        t.setBoard(board);
        applyFields(t, req, boardId);
        // 新任務固定進 TODO 欄尾端，符合「先建立再排程」的看板習慣
        t.setStatus(Task.Status.TODO);
        t.setSortOrder(taskRepository
            .findByBoardIdAndStatusOrderBySortOrderAsc(boardId, Task.Status.TODO).size());
        return taskRepository.save(t);
    }

    @Transactional
    public Task updateTask(Long boardId, Long taskId, TaskDto.SaveRequest req, User caller) {
        checkWrite(boardId, caller);
        Task t = getInBoard(boardId, taskId);
        applyFields(t, req, boardId);
        return taskRepository.save(t);
    }

    // 拖曳移動：目標欄整欄重排 sortOrder，確保順序連續無空洞
    @Transactional
    public Task moveTask(Long boardId, Long taskId, TaskDto.MoveRequest req, User caller) {
        checkWrite(boardId, caller);
        Task moving = getInBoard(boardId, taskId);
        Task.Status oldStatus = moving.getStatus();

        List<Task> target = new ArrayList<>(taskRepository
            .findByBoardIdAndStatusOrderBySortOrderAsc(boardId, req.getStatus()));
        target.removeIf(t -> t.getId().equals(taskId));
        int idx = Math.max(0, Math.min(req.getTargetIndex(), target.size()));
        moving.setStatus(req.getStatus());
        target.add(idx, moving);
        for (int i = 0; i < target.size(); i++) target.get(i).setSortOrder(i);
        taskRepository.saveAll(target);

        // 來源欄也重排，避免長期累積出重複的 sortOrder
        if (oldStatus != req.getStatus()) {
            List<Task> source = taskRepository
                .findByBoardIdAndStatusOrderBySortOrderAsc(boardId, oldStatus);
            for (int i = 0; i < source.size(); i++) source.get(i).setSortOrder(i);
            taskRepository.saveAll(source);
        }
        return moving;
    }

    @Transactional
    public void deleteTask(Long boardId, Long taskId, User caller) {
        checkWrite(boardId, caller);
        taskRepository.delete(getInBoard(boardId, taskId));
    }

    // 任務必須屬於指定看板，防止以其他看板 ID 繞過權限檢查（IDOR）
    private Task getInBoard(Long boardId, Long taskId) {
        Task t = taskRepository.findById(taskId)
            .orElseThrow(() -> new EntityNotFoundException("任務不存在"));
        if (!t.getBoard().getId().equals(boardId)) {
            throw new EntityNotFoundException("任務不存在");
        }
        return t;
    }

    private void applyFields(Task t, TaskDto.SaveRequest req, Long boardId) {
        t.setTitle(req.getTitle());
        t.setDescription(req.getDescription());
        t.setDueDate(req.getDueDate());
        t.setPriority(req.getPriority() != null ? req.getPriority() : Task.Priority.MEDIUM);
        if (req.getAssigneeId() != null) {
            // 負責人必須是看板成員或看板負責人，避免指派給無權限者造成幽靈任務
            User assignee = userRepository.findById(req.getAssigneeId())
                .orElseThrow(() -> new EntityNotFoundException("使用者不存在"));
            Board board = boardService.getById(boardId);
            boolean isOwner = board.getOwner() != null
                && board.getOwner().getId().equals(assignee.getId());
            if (!isOwner && !boardService.isMember(boardId, assignee.getId())) {
                throw new IllegalArgumentException("負責人必須是看板成員");
            }
            t.setAssignee(assignee);
        } else {
            t.setAssignee(null);
        }
    }
}
```

`src/main/java/com/taskflow/task/TaskController.java`：

```java
package com.taskflow.task;

import com.taskflow.board.BoardService;
import com.taskflow.user.User;
import com.taskflow.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api/boards/{boardId}/tasks")
@RequiredArgsConstructor
public class TaskController {

    private final TaskService taskService;
    private final BoardService boardService;
    private final UserRepository userRepository;

    // 初始載入用；後續增刪改走 WebSocket（見 collab 模組）確保多人同步
    @GetMapping
    public List<TaskDto.Response> list(@PathVariable Long boardId, Principal principal) {
        User user = userRepository.findByEmail(principal.getName()).orElseThrow();
        if (!boardService.canReadBoard(boardId, user)) {
            throw new SecurityException("無檢視此看板的權限");
        }
        return taskService.listByBoard(boardId).stream().map(TaskDto.Response::from).toList();
    }
}
```

- [ ] **Step 6: 執行測試確認通過**

Run: `mvn test`
Expected: BUILD SUCCESS，`TaskServiceTest` 7 案例全 PASS

- [ ] **Step 7: Commit**

```bash
git add src
git commit -m "feat: task 模組（CRUD、拖曳排序、IDOR 防護）"
```

---

### Task 6: template 模組（任務模板）

**Files:**
- Create: `src/main/java/com/taskflow/template/TaskTemplate.java`、`TemplateDto.java`、`TemplateRepository.java`、`TemplateService.java`、`TemplateController.java`
- Create: `src/main/resources/templates/template/list.html`
- Test: `src/test/java/com/taskflow/template/TemplateServiceTest.java`

**Interfaces:**
- Consumes: `User`、`Department`（Task 2）
- Produces:
  - `TemplateService.listVisible(User): List<TaskTemplate>`（系統模板 + 本科自訂）
  - `TemplateService.create/update/delete(…, User)`（僅 CHIEF/LEADER 可寫本科自訂；系統模板不可改刪）
  - REST：`GET /api/templates`、`POST /api/templates`、`PUT /api/templates/{id}`、`DELETE /api/templates/{id}`
  - 頁面：`GET /templates`（view `template/list`）

- [ ] **Step 1: 撰寫失敗測試**

`src/test/java/com/taskflow/template/TemplateServiceTest.java`（fixture 同 `TaskServiceTest` 的 `newUser` 寫法，建 chief（資訊科）、leader（資訊科）、member（資訊科）、chief2（資訊科2））：

```java
package com.taskflow.template;

import com.taskflow.department.Department;
import com.taskflow.department.DepartmentRepository;
import com.taskflow.user.User;
import com.taskflow.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class TemplateServiceTest {

    @Autowired TemplateService templateService;
    @Autowired TemplateRepository templateRepository;
    @Autowired UserRepository userRepository;
    @Autowired DepartmentRepository departmentRepository;

    User chief;
    User member;
    User chief2;
    TaskTemplate system;

    @BeforeEach
    void setUp() {
        Department div = new Department(); div.setName("資訊部"); departmentRepository.save(div);
        Department sec = new Department(); sec.setName("資訊科"); sec.setParent(div); departmentRepository.save(sec);
        Department sec2 = new Department(); sec2.setName("資訊科2"); sec2.setParent(div); departmentRepository.save(sec2);
        chief  = newUser("chief@t.com",  User.Role.SECTION_CHIEF,  sec);
        member = newUser("member@t.com", User.Role.PROJECT_MEMBER, sec);
        chief2 = newUser("chief2@t.com", User.Role.SECTION_CHIEF,  sec2);

        system = new TaskTemplate();
        system.setName("Bug 修復"); system.setTitle("[Bug] "); system.setSystem(true);
        templateRepository.save(system);
    }

    User newUser(String email, User.Role role, Department dept) {
        User u = new User();
        u.setEmail(email); u.setPasswordHash("x"); u.setDisplayName(email);
        u.setRole(role); u.setDepartment(dept);
        return userRepository.save(u);
    }

    TemplateDto.SaveRequest req(String name) {
        TemplateDto.SaveRequest r = new TemplateDto.SaveRequest();
        r.setName(name); r.setTitle(name + "標題");
        return r;
    }

    @Test
    void 可見範圍_系統模板加本科自訂() {
        templateService.create(req("本科模板"), chief);
        templateService.create(req("他科模板"), chief2);
        assertThat(templateService.listVisible(member))
            .extracting(TaskTemplate::getName)
            .containsExactlyInAnyOrder("Bug 修復", "本科模板");
    }

    @Test
    void member建立模板_被拒() {
        assertThatThrownBy(() -> templateService.create(req("偷建"), member))
            .isInstanceOf(SecurityException.class);
    }

    @Test
    void 系統模板不可修改刪除() {
        assertThatThrownBy(() -> templateService.update(system.getId(), req("改名"), chief))
            .isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> templateService.delete(system.getId(), chief))
            .isInstanceOf(SecurityException.class);
    }

    @Test
    void 跨科修改自訂模板_被拒() {
        TaskTemplate mine = templateService.create(req("本科模板"), chief);
        assertThatThrownBy(() -> templateService.update(mine.getId(), req("他科亂改"), chief2))
            .isInstanceOf(SecurityException.class);
    }
}
```

- [ ] **Step 2: 執行測試確認失敗**

Run: `mvn test -Dtest=TemplateServiceTest`
Expected: 編譯失敗（`TaskTemplate` 不存在）

- [ ] **Step 3: 撰寫 entity / DTO / Repository / Service / Controller**

`TaskTemplate.java`：

```java
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
```

`TemplateDto.java`：

```java
package com.taskflow.template;

import com.taskflow.task.Task;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

public class TemplateDto {

    @Data
    public static class SaveRequest {
        @NotBlank(message = "模板名稱不可為空")
        private String name;
        @NotBlank(message = "任務標題不可為空")
        private String title;
        private String description;
        private Task.Priority priority = Task.Priority.MEDIUM;
    }

    @Data
    public static class Response {
        private Long id;
        private String name;
        private String title;
        private String description;
        private Task.Priority priority;
        private boolean system;

        public static Response from(TaskTemplate t) {
            Response r = new Response();
            r.id = t.getId(); r.name = t.getName(); r.title = t.getTitle();
            r.description = t.getDescription(); r.priority = t.getPriority();
            r.system = t.isSystem();
            return r;
        }
    }
}
```

`TemplateRepository.java`：

```java
package com.taskflow.template;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface TemplateRepository extends JpaRepository<TaskTemplate, Long> {
    List<TaskTemplate> findBySystemTrueOrSectionIdOrderByIdAsc(Long sectionId);
}
```

`TemplateService.java`：

```java
package com.taskflow.template;

import com.taskflow.user.User;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TemplateService {

    private final TemplateRepository templateRepository;

    // 系統模板全員可見；自訂模板以科為共用單位，同科成員共享
    @Transactional(readOnly = true)
    public List<TaskTemplate> listVisible(User user) {
        Long sectionId = user.getDepartment() != null ? user.getDepartment().getId() : null;
        return templateRepository.findBySystemTrueOrSectionIdOrderByIdAsc(sectionId);
    }

    @Transactional
    public TaskTemplate create(TemplateDto.SaveRequest req, User caller) {
        checkManageRole(caller);
        TaskTemplate t = new TaskTemplate();
        apply(t, req);
        t.setSection(caller.getDepartment());
        return templateRepository.save(t);
    }

    @Transactional
    public TaskTemplate update(Long id, TemplateDto.SaveRequest req, User caller) {
        TaskTemplate t = getEditable(id, caller);
        apply(t, req);
        return templateRepository.save(t);
    }

    @Transactional
    public void delete(Long id, User caller) {
        templateRepository.delete(getEditable(id, caller));
    }

    // 模板管理限科長/Leader：Member 只消費模板，不維護
    private void checkManageRole(User caller) {
        if (caller.getRole() != User.Role.SECTION_CHIEF
                && caller.getRole() != User.Role.PROJECT_LEADER) {
            throw new SecurityException("僅科長或 Leader 可管理模板");
        }
    }

    // 系統模板不可改刪；自訂模板僅限同科的科長/Leader
    private TaskTemplate getEditable(Long id, User caller) {
        checkManageRole(caller);
        TaskTemplate t = templateRepository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("模板不存在"));
        if (t.isSystem()) throw new SecurityException("系統模板不可修改或刪除");
        if (t.getSection() == null || caller.getDepartment() == null
                || !t.getSection().getId().equals(caller.getDepartment().getId())) {
            throw new SecurityException("僅可管理本科模板");
        }
        return t;
    }

    private void apply(TaskTemplate t, TemplateDto.SaveRequest req) {
        t.setName(req.getName());
        t.setTitle(req.getTitle());
        t.setDescription(req.getDescription());
        t.setPriority(req.getPriority());
    }
}
```

`TemplateController.java`：

```java
package com.taskflow.template;

import com.taskflow.user.User;
import com.taskflow.user.UserRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import java.security.Principal;
import java.util.List;

@Controller
@RequiredArgsConstructor
public class TemplateController {

    private final TemplateService templateService;
    private final UserRepository userRepository;

    private User currentUser(Principal principal) {
        return userRepository.findByEmail(principal.getName()).orElseThrow();
    }

    @GetMapping("/templates")
    public String page(Model model, Principal principal) {
        model.addAttribute("templates",
            templateService.listVisible(currentUser(principal)).stream()
                .map(TemplateDto.Response::from).toList());
        return "template/list";
    }

    @GetMapping("/api/templates")
    @ResponseBody
    public List<TemplateDto.Response> list(Principal principal) {
        return templateService.listVisible(currentUser(principal)).stream()
            .map(TemplateDto.Response::from).toList();
    }

    @PostMapping("/api/templates")
    @ResponseBody
    public TemplateDto.Response create(@Valid @RequestBody TemplateDto.SaveRequest req,
                                       Principal principal) {
        return TemplateDto.Response.from(templateService.create(req, currentUser(principal)));
    }

    @PutMapping("/api/templates/{id}")
    @ResponseBody
    public TemplateDto.Response update(@PathVariable Long id,
                                       @Valid @RequestBody TemplateDto.SaveRequest req,
                                       Principal principal) {
        return TemplateDto.Response.from(templateService.update(id, req, currentUser(principal)));
    }

    @DeleteMapping("/api/templates/{id}")
    @ResponseBody
    public void delete(@PathVariable Long id, Principal principal) {
        templateService.delete(id, currentUser(principal));
    }
}
```

- [ ] **Step 4: 執行測試確認通過**

Run: `mvn test -Dtest=TemplateServiceTest`
Expected: 4 案例全 PASS

- [ ] **Step 5: 撰寫管理頁**

`src/main/resources/templates/template/list.html`：以 `$WBS` 的 `template/list.html` 為版型基準複製後改造——表格欄位改為「模板名稱 / 任務標題 / 優先級 / 類型 / 操作」，新增/編輯用 modal（欄位：名稱、標題、描述 textarea、優先級 select），呼叫上述 `/api/templates` CRUD；系統模板列不顯示編輯/刪除鈕（`th:if="${!tpl.system}"`）；Vue 3 寫法與 fetch + CSRF header 比照該檔既有模式。頁面需含：

```html
<meta name="_csrf" th:content="${_csrf.token}">
<meta name="_csrf_header" th:content="${_csrf.headerName}">
```

fetch 寫入時帶 `headers: { 'Content-Type': 'application/json', [csrfHeader]: csrfToken }`。

- [ ] **Step 6: 煙霧測試與 Commit**

Run: `mvn spring-boot:run` → 以 chief 登入瀏覽 `/templates`
Expected: 見 3 筆系統模板（唯讀）；可新增自訂模板並編輯刪除

```bash
git add src
git commit -m "feat: template 模組（系統/本科自訂任務模板）"
```

---

### Task 7: Kanban 看板頁前端

**Files:**
- Create: `src/main/resources/templates/board/detail.html`（覆蓋 Task 3 sed 產物，全新撰寫）
- Create: `src/main/resources/static/css/board.css`
- Create: `src/main/resources/static/js/board.js`
- Modify: `src/main/java/com/taskflow/board/BoardController.java`（detail 路由補 model 屬性）

**Interfaces:**
- Consumes: `GET /api/boards/{id}/tasks`（Task 5）、`GET /api/templates`（Task 6）、`GET /api/boards/{id}/members`（Task 3）
- Produces: `window.boardPage` Vue app；本 Task 先以 REST 顯示唯讀資料 + 前端互動骨架，寫入操作於 Task 8 接上 WebSocket

- [ ] **Step 1: BoardController.detail 補頁面所需 model**

```java
    // 看板頁：SSR 只出骨架與權限旗標，任務資料由前端 REST/WebSocket 載入
    @GetMapping("/boards/{id}")
    public String detail(@PathVariable Long id, Model model, Principal principal) {
        User user = userRepository.findByEmail(principal.getName()).orElseThrow();
        Board board = boardService.getById(id);
        if (!boardService.canReadBoard(id, user)) {
            return "redirect:/boards";
        }
        model.addAttribute("board", board);
        model.addAttribute("canWrite", boardService.canWriteBoard(id, user));
        model.addAttribute("currentUserId", user.getId());
        return "board/detail";
    }
```

- [ ] **Step 2: 撰寫 board.css**

`src/main/resources/static/css/board.css`：

```css
/* Kanban 三欄版面：延續 app.css 的卡片視覺語言（圓角 8px、淡陰影、#f5f6fa 底） */
.kanban { display: grid; grid-template-columns: repeat(3, 1fr); gap: 1rem; align-items: start; }
.kanban-col { background: #ececf1; border-radius: 8px; padding: 0.75rem; min-height: 320px; }
.kanban-col-header { display: flex; justify-content: space-between; align-items: center;
    font-weight: 700; font-size: 0.9rem; color: #636e72; padding: 0.25rem 0.5rem 0.75rem; }
.kanban-col-count { background: #b2bec3; color: #fff; border-radius: 10px;
    padding: 0 0.55rem; font-size: 0.75rem; }
.kanban-col.drag-over { outline: 2px dashed #0984e3; outline-offset: -4px; }

/* 任務卡片：左側色條表優先級（高紅/中橙/低綠） */
.task-card { background: #fff; border-radius: 6px; padding: 0.75rem 0.9rem; margin-bottom: 0.6rem;
    box-shadow: 0 1px 3px rgba(0,0,0,0.08); cursor: grab; border-left: 4px solid #b2bec3; }
.task-card:active { cursor: grabbing; }
.task-card.priority-HIGH   { border-left-color: #d63031; }
.task-card.priority-MEDIUM { border-left-color: #e17055; }
.task-card.priority-LOW    { border-left-color: #00b894; }
.task-card-title { font-size: 0.95rem; font-weight: 600; margin-bottom: 0.4rem; }
.task-card-meta { display: flex; justify-content: space-between; align-items: center;
    font-size: 0.8rem; color: #636e72; }
/* 逾期未完成以紅字警示，提醒到期風險 */
.task-due.overdue { color: #d63031; font-weight: 700; }
.task-card.dragging { opacity: 0.4; }

.board-toolbar { display: flex; gap: 0.75rem; align-items: center; }
.archived-banner { background: #ffeaa7; color: #6c5ce7; padding: 0.6rem 1rem;
    border-radius: 6px; margin-bottom: 1rem; font-size: 0.9rem; }
```

- [ ] **Step 3: 撰寫 board/detail.html**

全新撰寫（fragments 沿用；重點結構如下，完整補齊 head/body）：

```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org">
<head>
    <meta charset="UTF-8">
    <title th:text="${board.name} + ' - TaskFlow'">看板</title>
    <link rel="stylesheet" th:href="@{/css/app.css}">
    <link rel="stylesheet" th:href="@{/css/board.css}">
    <meta name="_csrf" th:content="${_csrf.token}">
    <meta name="_csrf_header" th:content="${_csrf.headerName}">
</head>
<body>
<div th:replace="~{fragments/header :: header}"></div>
<div class="layout">
    <div th:replace="~{fragments/sidebar :: sidebar}"></div>
    <main class="main-content" id="board-app"
          th:attr="data-board-id=${board.id},data-can-write=${canWrite},data-board-name=${board.name},data-archived=${board.archived}">
        <div v-if="archived" class="archived-banner">此看板已歸檔，全部內容唯讀。</div>
        <div class="page-header">
            <h2>{{ boardName }}</h2>
            <div class="board-toolbar">
                <div class="presence-bar">
                    <span v-for="u in onlineUsers" :key="u.userId" class="presence-avatar"
                          :style="{background: u.color}" :title="u.displayName">{{ u.displayName[0] }}</span>
                </div>
                <button v-if="canWrite" class="btn btn-primary" @click="openCreate">＋ 新增任務</button>
                <div class="export-dropdown">
                    <button class="btn">匯出 ▾</button>
                    <div class="dropdown-menu">
                        <a class="dropdown-item" :href="exportUrl('json')">JSON</a>
                        <a class="dropdown-item" :href="exportUrl('csv')">CSV</a>
                        <a class="dropdown-item" :href="exportUrl('xlsx')">XLSX</a>
                    </div>
                </div>
                <button v-if="canWrite" class="btn btn-danger" @click="archiveBoard">歸檔</button>
            </div>
        </div>
        <div class="kanban">
            <div v-for="col in columns" :key="col.status" class="kanban-col"
                 :class="{'drag-over': dragOverCol === col.status}"
                 @dragover.prevent="dragOverCol = col.status"
                 @dragleave="dragOverCol = null"
                 @drop="onDrop(col.status, $event)">
                <div class="kanban-col-header">
                    <span>{{ col.label }}</span>
                    <span class="kanban-col-count">{{ tasksIn(col.status).length }}</span>
                </div>
                <div v-for="(t, idx) in tasksIn(col.status)" :key="t.id"
                     class="task-card" :class="'priority-' + t.priority"
                     :draggable="canWrite" @dragstart="onDragStart(t, $event)"
                     @dragover.prevent="dragIndex = idx" @click="openEdit(t)">
                    <div class="task-card-title">{{ t.title }}</div>
                    <div class="task-card-meta">
                        <span>{{ t.assigneeName || '未指派' }}</span>
                        <span class="task-due" :class="{overdue: isOverdue(t)}"
                              v-if="t.dueDate">{{ t.dueDate }}</span>
                    </div>
                </div>
            </div>
        </div>
        <!-- 任務編輯/新增 modal：欄位 標題/描述/負責人/到期日/優先級，新增時多「套用模板」下拉 -->
        <div v-if="modal.open" class="modal-overlay" @click.self="modal.open = false">
            <div class="modal">
                <h3>{{ modal.taskId ? '編輯任務' : '新增任務' }}</h3>
                <div class="form-group" v-if="!modal.taskId">
                    <label>套用模板</label>
                    <select v-model="modal.templateId" @change="applyTemplate">
                        <option :value="null">— 不套用 —</option>
                        <option v-for="tpl in templates" :key="tpl.id" :value="tpl.id">{{ tpl.name }}</option>
                    </select>
                </div>
                <div class="form-group"><label>標題</label><input v-model="modal.form.title"></div>
                <div class="form-group"><label>描述</label>
                    <textarea v-model="modal.form.description" rows="4"
                              style="width:100%; padding:0.6rem 0.8rem; border:1px solid #b2bec3; border-radius:4px;"></textarea></div>
                <div class="form-group"><label>負責人</label>
                    <select v-model="modal.form.assigneeId">
                        <option :value="null">未指派</option>
                        <option v-for="m in members" :key="m.id" :value="m.id">{{ m.displayName }}</option>
                    </select></div>
                <div class="form-group"><label>到期日</label><input type="date" v-model="modal.form.dueDate"></div>
                <div class="form-group"><label>優先級</label>
                    <select v-model="modal.form.priority">
                        <option value="HIGH">高</option><option value="MEDIUM">中</option><option value="LOW">低</option>
                    </select></div>
                <div class="modal-actions">
                    <button class="btn btn-primary" @click="saveTask" :disabled="!canWrite">儲存</button>
                    <button v-if="modal.taskId && canWrite" class="btn btn-danger" @click="deleteTask">刪除</button>
                    <button class="btn" @click="modal.open = false">取消</button>
                </div>
            </div>
        </div>
    </main>
</div>
<div th:replace="~{fragments/footer :: footer}"></div>
<script th:src="@{/js/vue.global.prod.min.js}"></script>
<script th:src="@{/js/sockjs.min.js}"></script>
<script th:src="@{/js/stomp.min.js}"></script>
<script th:src="@{/js/board.js}"></script>
</body>
</html>
```

- [ ] **Step 4: 撰寫 board.js（本 Task 先接 REST 讀取，寫入函式留待 Task 8 換成 STOMP）**

`src/main/resources/static/js/board.js`：

```javascript
// Kanban 看板前端：初始資料走 REST，寫入操作走 STOMP（Task 8 接上）確保多人即時同步
const el = document.getElementById('board-app');
const boardId = el.dataset.boardId;
const csrfToken = document.querySelector('meta[name="_csrf"]').content;
const csrfHeader = document.querySelector('meta[name="_csrf_header"]').content;

const { createApp } = Vue;

window.boardPage = createApp({
    data() {
        return {
            boardName: el.dataset.boardName,
            canWrite: el.dataset.canWrite === 'true' && el.dataset.archived !== 'true',
            archived: el.dataset.archived === 'true',
            columns: [
                { status: 'TODO',        label: '待辦' },
                { status: 'IN_PROGRESS', label: '進行中' },
                { status: 'DONE',        label: '完成' },
            ],
            tasks: [],
            members: [],
            templates: [],
            onlineUsers: [],
            dragging: null,     // 拖曳中的任務
            dragOverCol: null,  // 目前懸停的欄
            dragIndex: 0,       // 目前懸停的欄內位置
            modal: { open: false, taskId: null, templateId: null,
                     form: { title: '', description: '', assigneeId: null, dueDate: null, priority: 'MEDIUM' } },
        };
    },
    methods: {
        tasksIn(status) {
            return this.tasks.filter(t => t.status === status)
                .sort((a, b) => a.sortOrder - b.sortOrder);
        },
        isOverdue(t) {
            // 已完成的任務不再警示逾期，避免歷史卡片一片紅
            return t.dueDate && t.status !== 'DONE'
                && t.dueDate < new Date().toISOString().slice(0, 10);
        },
        exportUrl(fmt) { return `/api/boards/${boardId}/export?format=${fmt}`; },
        async loadAll() {
            const [tasks, members, templates] = await Promise.all([
                fetch(`/api/boards/${boardId}/tasks`).then(r => r.json()),
                fetch(`/api/boards/${boardId}/members`).then(r => r.json()),
                fetch(`/api/templates`).then(r => r.json()),
            ]);
            this.tasks = tasks; this.members = members; this.templates = templates;
        },
        openCreate() {
            this.modal = { open: true, taskId: null, templateId: null,
                form: { title: '', description: '', assigneeId: null, dueDate: null, priority: 'MEDIUM' } };
        },
        openEdit(t) {
            this.modal = { open: true, taskId: t.id, templateId: null,
                form: { title: t.title, description: t.description,
                        assigneeId: t.assigneeId, dueDate: t.dueDate, priority: t.priority } };
        },
        applyTemplate() {
            const tpl = this.templates.find(t => t.id === this.modal.templateId);
            if (!tpl) return;
            // 模板只帶入預設值，使用者仍可修改後再儲存
            this.modal.form.title = tpl.title;
            this.modal.form.description = tpl.description;
            this.modal.form.priority = tpl.priority;
        },
        onDragStart(t, ev) {
            this.dragging = t;
            ev.dataTransfer.effectAllowed = 'move';
        },
        onDrop(status) {
            if (!this.dragging || !this.canWrite) return;
            this.sendMove(this.dragging.id, status, this.dragIndex);
            this.dragging = null; this.dragOverCol = null;
        },
        saveTask() {
            if (!this.modal.form.title || !this.modal.form.title.trim()) {
                alert('標題不可為空'); return;
            }
            if (this.modal.taskId) this.sendUpdate(this.modal.taskId, this.modal.form);
            else this.sendCreate(this.modal.form);
            this.modal.open = false;
        },
        deleteTask() {
            if (!confirm('確定刪除此任務？')) return;
            this.sendDelete(this.modal.taskId);
            this.modal.open = false;
        },
        async archiveBoard() {
            if (!confirm('歸檔後看板將變為唯讀，確定歸檔？')) return;
            await fetch(`/api/boards/${boardId}/archive`,
                { method: 'POST', headers: { [csrfHeader]: csrfToken } });
            location.reload();
        },
        // ---- 寫入操作：Task 8 將以 STOMP publish 取代下列 REST 版本 ----
        sendCreate(form) { /* Task 8 實作 */ },
        sendUpdate(taskId, form) { /* Task 8 實作 */ },
        sendMove(taskId, status, targetIndex) { /* Task 8 實作 */ },
        sendDelete(taskId) { /* Task 8 實作 */ },
    },
    mounted() { this.loadAll(); },
}).mount('#board-app');
```

（注：`send*` 四個空函式是 Task 8 的既定接點，非遺漏——Task 8 未完成前看板為唯讀展示。）

- [ ] **Step 5: 煙霧測試**

Run: `mvn spring-boot:run` → chief 登入 → `/boards` 建看板 → 進入看板頁
Expected: 三欄看板渲染、工具列與 modal 開合正常、無 console 錯誤（寫入尚未生效屬預期）

- [ ] **Step 6: Commit**

```bash
git add src
git commit -m "feat: Kanban 看板頁（三欄、卡片、modal、匯出入口）"
```

---

### Task 8: collab 模組（WebSocket 即時協作）

**Files:**
- Create: `src/main/java/com/taskflow/collab/{WebSocketConfig,CollabService,PresenceMessage}.java`（自 `$WBS` 複製改名）
- Create: `src/main/java/com/taskflow/collab/TaskChangeMessage.java`、`CollabController.java`（全新）
- Create: `src/main/java/com/taskflow/config/WebSocketSecurityConfig.java`（複製改名）
- Modify: `src/main/resources/static/js/board.js`（接上 STOMP）
- Test: `src/test/java/com/taskflow/collab/{WebSocketConfigTest,CollabServiceTest}.java`（複製改名）、`CollabControllerMoveTest.java`（全新）

**Interfaces:**
- Consumes: `TaskService.createTask/updateTask/moveTask/deleteTask`、`TaskDto`（Task 5）、`BoardService.canWriteBoard`（Task 3）
- Produces:
  - STOMP endpoint `/ws`（SockJS）；app prefix `/app`、broker `/topic`
  - 訂閱：`/topic/board/{id}/tasks`（TaskChangeMessage）、`/topic/board/{id}/presence`（PresenceMessage）
  - 發送：`/app/board/{id}/task/create|update|move|delete`、`/app/board/{id}/join|leave`

- [ ] **Step 1: 複製基礎設施並改名**

```bash
WBS=/Users/oscarlin/Documents/GitHub/Spring-WbsScaff
mkdir -p src/main/java/com/taskflow/collab src/main/java/com/taskflow/config
cp $WBS/src/main/java/com/wbsscaff/collab/{WebSocketConfig,CollabService,PresenceMessage}.java src/main/java/com/taskflow/collab/
cp $WBS/src/main/java/com/wbsscaff/config/WebSocketSecurityConfig.java src/main/java/com/taskflow/config/
LC_ALL=C find src/main/java/com/taskflow/{collab,config} -name '*.java' -exec sed -i '' \
  's/com\.wbsscaff/com.taskflow/g; s/projectId/boardId/g; s|/topic/project/|/topic/board/|g; s/專案/看板/g' {} +
```

檢查 `WebSocketSecurityConfig`：destination 權限規則中 `/app/project/**`、`/topic/project/**` 改為 `/app/board/**`、`/topic/board/**`。

- [ ] **Step 2: 撰寫 TaskChangeMessage**

```java
package com.taskflow.collab;

import lombok.Data;
import java.time.Instant;

@Data
public class TaskChangeMessage {
    public enum Type { TASK_CREATE, TASK_UPDATE, TASK_MOVE, TASK_DELETE }

    private Type type;
    private Long taskId;
    private Object payload;      // TaskDto.Response；MOVE 時另含全板任務重排結果
    private UserInfo operator;
    // 使用 Instant 確保 UTC 一致，避免各端時區差異造成排序錯亂
    private Instant timestamp = Instant.now();

    @Data
    public static class UserInfo {
        private Long userId;
        private String displayName;
        private String color;
    }
}
```

- [ ] **Step 3: 撰寫失敗測試（move 廣播）**

`src/test/java/com/taskflow/collab/CollabControllerMoveTest.java`（fixture 同 `TaskServiceTest`；以 mock Principal 直呼 controller 方法 + mock `SimpMessagingTemplate` 驗證廣播）：

```java
package com.taskflow.collab;

import com.taskflow.board.Board;
import com.taskflow.board.BoardService;
import com.taskflow.department.Department;
import com.taskflow.department.DepartmentRepository;
import com.taskflow.task.Task;
import com.taskflow.task.TaskDto;
import com.taskflow.task.TaskService;
import com.taskflow.user.User;
import com.taskflow.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import java.security.Principal;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CollabControllerMoveTest {

    @Autowired CollabController collabController;
    @Autowired TaskService taskService;
    @Autowired BoardService boardService;
    @Autowired UserRepository userRepository;
    @Autowired DepartmentRepository departmentRepository;
    @MockBean SimpMessagingTemplate broker;

    User chief;
    Board board;
    Task task;

    @BeforeEach
    void setUp() {
        Department div = new Department(); div.setName("資訊部"); departmentRepository.save(div);
        Department sec = new Department(); sec.setName("資訊科"); sec.setParent(div); departmentRepository.save(sec);
        chief = new User();
        chief.setEmail("chief@t.com"); chief.setPasswordHash("x");
        chief.setDisplayName("科長"); chief.setRole(User.Role.SECTION_CHIEF); chief.setDepartment(sec);
        userRepository.save(chief);
        var req = new com.taskflow.board.BoardDto.CreateRequest();
        req.setName("協作看板");
        board = boardService.createBoard(req, chief.getId());
        TaskDto.SaveRequest sr = new TaskDto.SaveRequest();
        sr.setTitle("要移動的任務");
        task = taskService.createTask(board.getId(), sr, chief);
    }

    Principal principalOf(User u) { return u::getEmail; }

    @Test
    void 移動任務_持久化並廣播TASK_MOVE() {
        TaskDto.MoveRequest mv = new TaskDto.MoveRequest();
        mv.setStatus(Task.Status.DONE);
        mv.setTargetIndex(0);

        collabController.onTaskMove(board.getId(), task.getId(), mv, principalOf(chief));

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(broker).convertAndSend(eq("/topic/board/" + board.getId() + "/tasks"), captor.capture());
        TaskChangeMessage msg = (TaskChangeMessage) captor.getValue();
        assertThat(msg.getType()).isEqualTo(TaskChangeMessage.Type.TASK_MOVE);
        assertThat(taskService.listByBoard(board.getId()).get(0).getStatus())
            .isEqualTo(Task.Status.DONE);
    }

    @Test
    void 無權限者移動_被拒且不廣播() {
        Department sec2 = new Department(); sec2.setName("資訊科2"); departmentRepository.save(sec2);
        User outsider = new User();
        outsider.setEmail("out@t.com"); outsider.setPasswordHash("x");
        outsider.setDisplayName("外人"); outsider.setRole(User.Role.PROJECT_MEMBER); outsider.setDepartment(sec2);
        userRepository.save(outsider);

        TaskDto.MoveRequest mv = new TaskDto.MoveRequest();
        mv.setStatus(Task.Status.DONE);
        mv.setTargetIndex(0);
        assertThatThrownBy(() ->
            collabController.onTaskMove(board.getId(), task.getId(), mv, principalOf(outsider)))
            .isInstanceOf(SecurityException.class);
    }
}
```

- [ ] **Step 4: 執行測試確認失敗**

Run: `mvn test -Dtest=CollabControllerMoveTest`
Expected: 編譯失敗（`CollabController` 不存在）

- [ ] **Step 5: 撰寫 CollabController**

```java
package com.taskflow.collab;

import com.taskflow.board.BoardService;
import com.taskflow.task.TaskDto;
import com.taskflow.task.TaskService;
import com.taskflow.user.User;
import com.taskflow.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.*;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
import java.security.Principal;

@Controller
@RequiredArgsConstructor
public class CollabController {

    private final SimpMessagingTemplate broker;
    private final CollabService collabService;
    private final TaskService taskService;
    private final UserRepository userRepository;
    private final BoardService boardService;

    private User resolve(Principal principal) {
        return userRepository.findByEmail(principal.getName()).orElseThrow();
    }

    // 寫入操作統一走 canWriteBoard 安全閘（部長唯讀、歸檔唯讀、非成員拒絕）
    private void checkWrite(Long boardId, User user) {
        if (!boardService.canWriteBoard(boardId, user)) {
            throw new SecurityException("無編輯此看板的權限");
        }
    }

    private TaskChangeMessage.UserInfo operatorOf(User user) {
        TaskChangeMessage.UserInfo ui = new TaskChangeMessage.UserInfo();
        ui.setUserId(user.getId());
        ui.setDisplayName(user.getDisplayName());
        ui.setColor(collabService.userColor(user.getId()));
        return ui;
    }

    private void broadcast(Long boardId, TaskChangeMessage.Type type, Long taskId,
                           Object payload, User operator) {
        TaskChangeMessage msg = new TaskChangeMessage();
        msg.setType(type);
        msg.setTaskId(taskId);
        msg.setPayload(payload);
        msg.setOperator(operatorOf(operator));
        broker.convertAndSend("/topic/board/" + boardId + "/tasks", msg);
    }

    @MessageMapping("/board/{boardId}/task/create")
    public void onTaskCreate(@DestinationVariable Long boardId,
                             @Payload TaskDto.SaveRequest req, Principal principal) {
        User user = resolve(principal);
        checkWrite(boardId, user);
        var created = TaskDto.Response.from(taskService.createTask(boardId, req, user));
        broadcast(boardId, TaskChangeMessage.Type.TASK_CREATE, created.getId(), created, user);
    }

    @MessageMapping("/board/{boardId}/task/update")
    public void onTaskUpdate(@DestinationVariable Long boardId,
                             @Payload TaskDto.SaveRequest req,
                             @Header("taskId") Long taskId, Principal principal) {
        User user = resolve(principal);
        checkWrite(boardId, user);
        var updated = TaskDto.Response.from(taskService.updateTask(boardId, taskId, req, user));
        broadcast(boardId, TaskChangeMessage.Type.TASK_UPDATE, taskId, updated, user);
    }

    // MOVE 廣播全板任務快照：欄內重排影響多筆 sortOrder，整批同步最不易漂移
    @MessageMapping("/board/{boardId}/task/{taskId}/move")
    public void onTaskMove(@DestinationVariable Long boardId,
                           @DestinationVariable Long taskId,
                           @Payload TaskDto.MoveRequest req, Principal principal) {
        User user = resolve(principal);
        checkWrite(boardId, user);
        taskService.moveTask(boardId, taskId, req, user);
        var snapshot = taskService.listByBoard(boardId).stream()
            .map(TaskDto.Response::from).toList();
        broadcast(boardId, TaskChangeMessage.Type.TASK_MOVE, taskId, snapshot, user);
    }

    @MessageMapping("/board/{boardId}/task/{taskId}/delete")
    public void onTaskDelete(@DestinationVariable Long boardId,
                             @DestinationVariable Long taskId, Principal principal) {
        User user = resolve(principal);
        checkWrite(boardId, user);
        taskService.deleteTask(boardId, taskId, user);
        broadcast(boardId, TaskChangeMessage.Type.TASK_DELETE, taskId, null, user);
    }

    // SockJS 無法攔截 onConnect，前端連線後主動 publish join 以補足 presence 廣播
    @MessageMapping("/board/{boardId}/join")
    public void handleJoin(@DestinationVariable Long boardId, Principal principal) {
        User user = resolve(principal);
        collabService.join(boardId, user.getId(), user.getDisplayName());
        PresenceMessage msg = new PresenceMessage();
        msg.setType(PresenceMessage.Type.JOIN);
        msg.setUserId(user.getId());
        msg.setDisplayName(user.getDisplayName());
        msg.setColor(collabService.userColor(user.getId()));
        broker.convertAndSend("/topic/board/" + boardId + "/presence", msg);
        // 回送目前在線清單給剛加入者（透過同一 topic 廣播全量清單，前端以 userId 去重）
        collabService.getOnlineUsers(boardId).forEach(p ->
            broker.convertAndSend("/topic/board/" + boardId + "/presence", p));
    }

    @MessageMapping("/board/{boardId}/leave")
    public void handleLeave(@DestinationVariable Long boardId, Principal principal) {
        User user = resolve(principal);
        collabService.leave(boardId, user.getId());
        PresenceMessage msg = new PresenceMessage();
        msg.setType(PresenceMessage.Type.LEAVE);
        msg.setUserId(user.getId());
        msg.setDisplayName(user.getDisplayName());
        msg.setColor(collabService.userColor(user.getId()));
        broker.convertAndSend("/topic/board/" + boardId + "/presence", msg);
    }
}
```

（`CollabService` 複製版方法名以實際為準：`join/leave/userColor/getUserColor/getOnlineUsers/sessions`——若 sed 後為 `boardId` 泛型 key 直接可用。`WebSocketConfig` 中 `SessionDisconnectListener` 的 topic 已由 sed 改為 `/topic/board/`。）

- [ ] **Step 6: 複製其餘 collab 測試並執行**

```bash
mkdir -p src/test/java/com/taskflow/collab
cp $WBS/src/test/java/com/wbsscaff/collab/{WebSocketConfigTest,CollabServiceTest}.java src/test/java/com/taskflow/collab/
LC_ALL=C find src/test/java/com/taskflow/collab -name '*.java' -exec sed -i '' \
  's/com\.wbsscaff/com.taskflow/g; s/projectId/boardId/g; s|/topic/project/|/topic/board/|g; s/專案/看板/g' {} +
```

Run: `mvn test`
Expected: BUILD SUCCESS，collab 三個測試檔全 PASS

- [ ] **Step 7: board.js 接上 STOMP**

將 Task 7 預留的四個 `send*` 函式替換，並於 `mounted` 建立連線：

```javascript
    methods: {
        // ...（既有方法保留）...
        connectWs() {
            const sock = new SockJS('/ws');
            this.stomp = Stomp.over(sock);
            this.stomp.debug = null;   // 關閉冗長 log
            this.stomp.connect({}, () => {
                this.stomp.subscribe(`/topic/board/${boardId}/tasks`, f => this.onTaskMessage(JSON.parse(f.body)));
                this.stomp.subscribe(`/topic/board/${boardId}/presence`, f => this.onPresence(JSON.parse(f.body)));
                this.stomp.send(`/app/board/${boardId}/join`, {}, '');
            });
            window.addEventListener('beforeunload', () =>
                this.stomp.send(`/app/board/${boardId}/leave`, {}, ''));
        },
        onTaskMessage(msg) {
            // MOVE 帶全板快照直接覆蓋；其餘按類型增量更新，確保多人畫面一致
            if (msg.type === 'TASK_MOVE') { this.tasks = msg.payload; return; }
            if (msg.type === 'TASK_CREATE') { this.tasks.push(msg.payload); return; }
            if (msg.type === 'TASK_UPDATE') {
                const i = this.tasks.findIndex(t => t.id === msg.taskId);
                if (i >= 0) this.tasks[i] = msg.payload;
                return;
            }
            if (msg.type === 'TASK_DELETE') {
                this.tasks = this.tasks.filter(t => t.id !== msg.taskId);
            }
        },
        onPresence(msg) {
            if (msg.type === 'LEAVE') {
                this.onlineUsers = this.onlineUsers.filter(u => u.userId !== msg.userId);
            } else if (!this.onlineUsers.some(u => u.userId === msg.userId)) {
                this.onlineUsers.push(msg);
            }
        },
        sendCreate(form) {
            this.stomp.send(`/app/board/${boardId}/task/create`, {}, JSON.stringify(form));
        },
        sendUpdate(taskId, form) {
            this.stomp.send(`/app/board/${boardId}/task/update`,
                { taskId: taskId }, JSON.stringify(form));
        },
        sendMove(taskId, status, targetIndex) {
            this.stomp.send(`/app/board/${boardId}/task/${taskId}/move`, {},
                JSON.stringify({ status, targetIndex }));
        },
        sendDelete(taskId) {
            this.stomp.send(`/app/board/${boardId}/task/${taskId}/delete`, {}, '');
        },
    },
    mounted() { this.loadAll(); this.connectWs(); },
```

- [ ] **Step 8: 雙視窗煙霧測試**

Run: `mvn spring-boot:run` → 兩個瀏覽器視窗分別以 chief 與 member1 登入同一看板
Expected: 一方新增/拖曳/刪除任務，另一方即時更新；右上角出現兩個在線頭像；關閉一方視窗後頭像消失

- [ ] **Step 9: Commit**

```bash
git add src
git commit -m "feat: collab 模組（STOMP 即時同步與在線協作者）"
```

---

### Task 9: 匯出功能（JSON / CSV / XLSX）

**Files:**
- Create: `src/main/java/com/taskflow/task/TaskExportService.java`
- Modify: `src/main/java/com/taskflow/task/TaskController.java`（加 export endpoint）
- Test: `src/test/java/com/taskflow/task/TaskExportServiceTest.java`

**Interfaces:**
- Consumes: `TaskService.listByBoard`、`TaskDto.Response`（Task 5）、`BoardService.canReadBoard`（Task 3）
- Produces: `GET /api/boards/{id}/export?format=json|csv|xlsx`（可讀看板者皆可用，含歸檔看板）

- [ ] **Step 1: 撰寫失敗測試**

```java
package com.taskflow.task;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class TaskExportServiceTest {

    @Autowired TaskExportService exportService;

    private TaskDto.Response sample() {
        TaskDto.Response r = new TaskDto.Response();
        r.setId(1L); r.setTitle("含,逗號\"引號"); r.setStatus(Task.Status.TODO);
        r.setPriority(Task.Priority.HIGH); r.setDueDate(LocalDate.of(2026, 7, 31));
        r.setAssigneeName("科長");
        return r;
    }

    @Test
    void csv_含BOM且特殊字元正確跳脫() {
        String csv = exportService.toCsv(List.of(sample()));
        assertThat(csv).startsWith("﻿");
        assertThat(csv).contains("\"含,逗號\"\"引號\"");
    }

    @Test
    void json_可序列化任務清單() {
        String json = exportService.toJson(List.of(sample()));
        assertThat(json).contains("\"title\"");
    }

    @Test
    void xlsx_產出可開啟的活頁簿() throws Exception {
        byte[] bytes = exportService.toXlsx(List.of(sample()));
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            assertThat(wb.getSheetAt(0).getRow(1).getCell(0).getStringCellValue())
                .isEqualTo("含,逗號\"引號");
        }
    }
}
```

- [ ] **Step 2: 執行測試確認失敗**

Run: `mvn test -Dtest=TaskExportServiceTest`
Expected: 編譯失敗（`TaskExportService` 不存在）

- [ ] **Step 3: 撰寫 TaskExportService**

```java
package com.taskflow.task;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TaskExportService {

    private final ObjectMapper objectMapper;

    private static final String[] HEADERS =
        { "標題", "狀態", "負責人", "到期日", "優先級", "描述" };

    public String toJson(List<TaskDto.Response> tasks) {
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(tasks);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    // 開頭加 UTF-8 BOM，讓 Excel 直接開啟 CSV 時正確辨識繁中編碼
    public String toCsv(List<TaskDto.Response> tasks) {
        StringBuilder sb = new StringBuilder("﻿");
        sb.append(String.join(",", HEADERS)).append('\n');
        for (TaskDto.Response t : tasks) {
            sb.append(csvField(t.getTitle())).append(',')
              .append(csvField(String.valueOf(t.getStatus()))).append(',')
              .append(csvField(t.getAssigneeName())).append(',')
              .append(csvField(t.getDueDate() != null ? t.getDueDate().toString() : "")).append(',')
              .append(csvField(String.valueOf(t.getPriority()))).append(',')
              .append(csvField(t.getDescription())).append('\n');
        }
        return sb.toString();
    }

    // RFC 4180：含逗號/引號/換行的欄位以雙引號包裹，內部引號翻倍
    private String csvField(String v) {
        if (v == null) return "";
        if (v.contains(",") || v.contains("\"") || v.contains("\n")) {
            return '"' + v.replace("\"", "\"\"") + '"';
        }
        return v;
    }

    public byte[] toXlsx(List<TaskDto.Response> tasks) {
        try (XSSFWorkbook wb = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("任務清單");
            Row header = sheet.createRow(0);
            for (int i = 0; i < HEADERS.length; i++) {
                header.createCell(i).setCellValue(HEADERS[i]);
            }
            int rowIdx = 1;
            for (TaskDto.Response t : tasks) {
                Row row = sheet.createRow(rowIdx++);
                row.createCell(0).setCellValue(t.getTitle());
                row.createCell(1).setCellValue(String.valueOf(t.getStatus()));
                row.createCell(2).setCellValue(t.getAssigneeName() != null ? t.getAssigneeName() : "");
                row.createCell(3).setCellValue(t.getDueDate() != null ? t.getDueDate().toString() : "");
                row.createCell(4).setCellValue(String.valueOf(t.getPriority()));
                row.createCell(5).setCellValue(t.getDescription() != null ? t.getDescription() : "");
            }
            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
```

- [ ] **Step 4: TaskController 加 export endpoint**

```java
    // 匯出是唯讀操作：凡可檢視看板者（含歸檔、部長跨科唯讀）皆可使用
    @GetMapping("/export")
    public ResponseEntity<byte[]> export(@PathVariable Long boardId,
                                         @RequestParam(defaultValue = "json") String format,
                                         Principal principal) {
        User user = userRepository.findByEmail(principal.getName()).orElseThrow();
        if (!boardService.canReadBoard(boardId, user)) {
            throw new SecurityException("無檢視此看板的權限");
        }
        List<TaskDto.Response> tasks = taskService.listByBoard(boardId).stream()
            .map(TaskDto.Response::from).toList();
        String base = "board-" + boardId + "-tasks";
        return switch (format) {
            case "csv" -> download(exportService.toCsv(tasks).getBytes(StandardCharsets.UTF_8),
                base + ".csv", "text/csv; charset=UTF-8");
            case "xlsx" -> download(exportService.toXlsx(tasks), base + ".xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            default -> download(exportService.toJson(tasks).getBytes(StandardCharsets.UTF_8),
                base + ".json", "application/json");
        };
    }

    private ResponseEntity<byte[]> download(byte[] body, String filename, String contentType) {
        return ResponseEntity.ok()
            .header("Content-Disposition", "attachment; filename=\"" + filename + "\"")
            .header("Content-Type", contentType)
            .body(body);
    }
```

（`TaskController` 建構子注入補 `TaskExportService exportService`；import 補 `org.springframework.http.ResponseEntity`、`java.nio.charset.StandardCharsets`。）

- [ ] **Step 5: 執行測試**

Run: `mvn test`
Expected: BUILD SUCCESS，`TaskExportServiceTest` 3 案例 PASS

- [ ] **Step 6: Commit**

```bash
git add src
git commit -m "feat: 匯出 JSON/CSV/XLSX（可讀者皆可用）"
```

---

### Task 10: 成員管理頁與錯誤頁收尾

**Files:**
- Create: `src/main/resources/templates/admin/members.html`（自 `$WBS` 複製改造）
- Modify: `src/main/java/com/taskflow/board/BoardController.java`（確認/補齊成員管理端點）
- Test: `src/test/java/com/taskflow/board/BoardControllerMembersTest.java`

**Interfaces:**
- Consumes: `BoardService.addMember/removeMember/changeOwner/listForUser`（Task 3）、user 模組的科內使用者查詢（Task 2 複製版既有端點）
- Produces: 頁面 `GET /admin/members`（限 SECTION_CHIEF / PROJECT_LEADER）；REST `GET/POST /api/boards/{id}/members`、`DELETE /api/boards/{id}/members/{userId}`、`PUT /api/boards/{id}/owner`

- [ ] **Step 1: 複製成員管理頁並對齊端點**

```bash
WBS=/Users/oscarlin/Documents/GitHub/Spring-WbsScaff
mkdir -p src/main/resources/templates/admin
cp $WBS/src/main/resources/templates/admin/members.html src/main/resources/templates/admin/
LC_ALL=C sed -i '' 's|/api/projects|/api/boards|g; s|/projects|/boards|g; s/專案/看板/g; s/project/board/g; s/Project/Board/g' src/main/resources/templates/admin/members.html
```

開啟 `members.html`，列出其呼叫的所有 API 路徑；`BoardController`（Task 3 sed 產物）需提供等價端點，canonical 清單：
`GET /api/boards/{id}/members`、`POST /api/boards/{id}/members`（body `{userId}`）、`DELETE /api/boards/{id}/members/{userId}`、`PUT /api/boards/{id}/owner`（body `{ownerId}`）、view `GET /admin/members`。頁面呼叫與 controller 不一致者，以 controller 為準修改頁面 JS。

- [ ] **Step 2: 確認 /admin/members 路由與角色限制**

`BoardController`（或 sed 產物中原本的 admin 路由）需有：

```java
    // 成員管理：限科長/Leader；只列出自己可管理的看板（科長本科全部、Leader 自己負責的）
    @GetMapping("/admin/members")
    public String membersPage(Model model, Principal principal) {
        User user = userRepository.findByEmail(principal.getName()).orElseThrow();
        if (user.getRole() != User.Role.SECTION_CHIEF
                && user.getRole() != User.Role.PROJECT_LEADER) {
            return "redirect:/boards";
        }
        List<Board> boards = boardService.listForUser(user, false).stream()
            .filter(b -> user.getRole() == User.Role.SECTION_CHIEF
                || (b.getOwner() != null && b.getOwner().getId().equals(user.getId())))
            .toList();
        model.addAttribute("boards", boards);
        return "admin/members";
    }
```

（若 sed 產物已有等價方法則校對即可，重複者刪除。）

- [ ] **Step 3: 撰寫失敗測試**

`BoardControllerMembersTest.java`（MockMvc，寫法比照複製來的 `BoardControllerTest` 既有模式）：

```java
package com.taskflow.board;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BoardControllerMembersTest {

    @Autowired MockMvc mockMvc;

    // 測試帳號建置比照 BoardControllerTest 既有 @BeforeEach fixture（member1 為 PROJECT_MEMBER）

    @Test
    @WithMockUser(username = "member1@t.com")
    void member角色進成員管理頁_被導回看板列表() throws Exception {
        mockMvc.perform(get("/admin/members"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/boards"));
    }

    @Test
    @WithMockUser(username = "chief@t.com")
    void 科長可進成員管理頁() throws Exception {
        mockMvc.perform(get("/admin/members"))
            .andExpect(status().isOk())
            .andExpect(view().name("admin/members"));
    }
}
```

（fixture 使用者建立段從同目錄 `BoardControllerTest` 抄用；email 以該檔實際 seed 為準。）

- [ ] **Step 4: 執行測試（先失敗後通過）**

Run: `mvn test -Dtest=BoardControllerMembersTest`
Expected: 若 Step 2 未完成先 FAIL；補上後 PASS

- [ ] **Step 5: 錯誤頁最終檢查**

確認 Task 4 複製的 `error/403.html`、`404.html`、`5xx.html` 內文與導覽連結均指向 `/boards`；`GlobalExceptionHandler`（Task 2 複製版）的 `EntityNotFoundException` 對應 404 view、`SecurityException` redirect `/boards`。瀏覽 `http://localhost:8080/boards/99999` 應見自訂 404。

- [ ] **Step 6: 全量測試與 Commit**

Run: `mvn test`
Expected: BUILD SUCCESS，全部測試 PASS

```bash
git add src
git commit -m "feat: 成員管理頁與自訂錯誤頁收尾"
```

---

### Task 11: README 與端到端驗證

**Files:**
- Modify: `README.md`

**Interfaces:**
- Consumes: 全部前置 Task
- Produces: 完整專案文件與驗證通過的系統

- [ ] **Step 1: 撰寫 README**

以 `$WBS/README.md` 為版型，改寫為 TaskFlow 內容，必含章節：技術架構表、架構流程圖（同格式）、角色權限矩陣（spec 的表）、快速啟動（`cp .env.example .env` → `docker compose up -d` → `mvn clean install -DskipTests` → `java -jar target/spring-taskflow-*.jar`）、功能說明（看板/Kanban 操作/即時協作/任務模板/歷史查詢/匯出/成員管理/錯誤處理）、測試帳號表（沿用 seed 的 8 組帳號、密碼 `test1234`）、重置資料庫指令。

- [ ] **Step 2: 全量自動化驗證**

Run: `mvn clean test`
Expected: BUILD SUCCESS，0 failures

- [ ] **Step 3: 端到端手動驗證清單**

`docker compose down -v && docker compose up -d`（等初始化完成）→ `mvn spring-boot:run`，依序驗證並逐項記錄結果：

1. 未登入訪問 `/boards` → 重導 `/login`
2. `chief@infotech.com` 登入 → `/boards`；建立看板「驗證看板」
3. 進看板：套用「Bug 修復」模板新增任務 → 標題/描述/優先級帶入
4. 拖曳任務到「進行中」→ 重整後狀態保留
5. 第二視窗以 `member1@infotech.com` 登入 → 看不到看板（未加入）
6. `/admin/members` 把 member1 加入 → member1 可見並可拖曳；雙視窗即時同步、在線頭像成對出現
7. 到期日設昨天 → 卡片紅色逾期警示
8. 匯出 JSON/CSV/XLSX 三檔可下載開啟（CSV 中文不亂碼）
9. 歸檔看板 → 工具列鎖定、member1 端同步唯讀；`/boards/history` 關鍵字與日期查詢命中；還原成功
10. `director@company.com` 登入 → 可見該看板（唯讀，無編輯按鈕）；`chief2@infotech.com` 登入 → 不可見（跨科隔離），直接輸入網址 → 重導 `/boards`
11. `/boards/99999` → 自訂 404
12. `/templates`：chief 新增自訂模板、chief2 看不到該模板

Expected: 12 項全數通過；任一失敗即修復後重跑該項

- [ ] **Step 4: Commit**

```bash
git add README.md
git commit -m "docs: README 與端到端驗證"
```

---

## 完成後

全部 Task 完成且驗證通過後：使用 superpowers:verification-before-completion 確認證據，再向使用者回報並詢問是否「推送」。
