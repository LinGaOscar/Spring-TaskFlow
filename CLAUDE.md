# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 專案概述

企業後台 **Kanban 任務管理系統**，架構與畫面風格移植自姊妹專案 `Spring-WbsScaff`（本機路徑 `../Spring-WbsScaff`，是移植/對照的權威來源）。Java 21 + Spring Boot 3.4 + Thymeleaf SSR + Vue 3（離線 CDN）+ STOMP WebSocket 即時協作 + SQL Server 2022。

## 常用指令

```bash
cp .env.example .env          # 設定 MSSQL_SA_PASSWORD（需符合 SQL Server 複雜度）

# Docker 一鍵起整套（db + app，多階段建置，本機免裝 Maven）
docker compose up --build -d  # http://localhost:8050

# 或本機開發：只用 Docker 起 DB，app 跑本機（可熱重載/除錯）
docker compose up -d db       # 等 log 出現 ">>> 初始化完成"（約 60-90s）
mvn spring-boot:run           # spring-dotenv 自動讀 .env，連 localhost:1433

# 重置資料庫（schema/種子資料改動後必做，因為不用 JPA ddl-auto）
docker compose down -v && docker compose up --build -d

# 測試（走 H2 記憶體庫，不需 Docker）
mvn test                      # 全部
mvn test -Dtest=TaskServiceTest                       # 單一測試類別
mvn test -Dtest=TaskServiceTest#跨欄移動_改變狀態並插入指定位置   # 單一測試方法
```

部署與環境變數細節見 `docs/dev.md`。

測試帳號密碼皆為 `test1234`（見 `db/mssql/02-seed.sql`），關鍵帳號：`chief@infotech.com`（科長）、`director@company.com`（部長）、`member1@infotech.com`（成員）。

## 核心架構

### 權限模型是單一事實來源（最重要）

`BoardService.canReadBoard(boardId, user)` 與 `canWriteBoard(boardId, user)` 是全系統唯一的授權判斷點，被**四個入口**共用——修改權限邏輯時必須確認四處都受影響：

1. **頁面**：`BoardController` 各路由手動呼叫（無權限則 `redirect:/boards`）
2. **REST**：`TaskController` / `BoardController` 的 `@ResponseBody` 方法
3. **WebSocket**：`CollabController` 寫入前 `checkWrite`；訂閱由 `BoardSubscribeAuthInterceptor` 攔 SUBSCRIBE 幀驗證 `canReadBoard`
4. **匯出**：`TaskController.export` 用 `canReadBoard`（唯讀操作，歸檔看板也可匯出）

組織是兩層部門樹：**部（Division，`parent=null`）→ 科（Section，`parent=部`）**。四角色 `DIRECTOR / SECTION_CHIEF / PROJECT_LEADER / PROJECT_MEMBER`。部長看板掛「部」層級、成員來自下屬「科」，所以凡是比對「使用者部門 vs 看板部門」的地方都要走父子判斷（`department.id 相等 或 department.parent.id == 看板部門id`），不能只用相等——見 `BoardController.memberDeptCompatible` 與 `canReadBoard` 的 DIRECTOR 分支。歸檔看板對全員強制唯讀（`canWriteBoard` 頂端一刀切）。

`canRead/canWriteBoard` 標記 `@Transactional(readOnly = true)`：WebSocket 執行緒沒有 open-in-view，缺此標記則遍歷 LAZY 關聯（`department.parent`）會拋 `LazyInitializationException`。

### JSON 回應信封

所有 REST `@ResponseBody` 端點回傳 `ApiResponse<T>`（`{success, message, data}`），前端 `fetch` 一律解 `.success`／`.data`。**例外**：`TaskController.export` 回傳 `ResponseEntity<byte[]>` 檔案下載，不包信封。`GlobalExceptionHandler` 是 `@RestControllerAdvice`，只服務 REST：`EntityNotFoundException`→404、`SecurityException`→403 JSON；頁面路由的 404/重導由各 controller 自行處理（`/boards/{不存在}` 會回自訂 404 頁但 HTTP 狀態為 200，屬與 WbsScaff 一致的既有行為）。

### 模組結構（package-by-feature，`com.taskflow`）

`auth`（Security + Session）、`user`、`department`、`board`（看板 CRUD/歸檔/成員）、`task`（Kanban 任務 + 拖曳排序 + 匯出）、`template`（任務模板）、`collab`（WebSocket 即時同步）、`common`（`ApiResponse`/`GlobalExceptionHandler`）、`config`（`WebSocketSecurityConfig`）。

### 前端

Thymeleaf 出「頁面殼 + 權限旗標」，Vue 3 掛 `#board-app` 負責互動（初始資料走 REST），寫入操作走 STOMP publish 後由訂閱回饋更新畫面（不做樂觀更新）。**打包的是 `@stomp/stompjs` UMD 版**（全域 `StompJs.Client`，非舊版 `Stomp.over`）。拖曳用 HTML5 原生 DnD，無額外函式庫。CSS 沿用 `app.css`（navbar `#2d3436`、背景 `#f5f6fa`、主色 `#0984e3`），看板專用樣式在 `board.css`。前端 JS 中的看板卡片 class 是 `board-*`（非 `project-*`）。

### 資料庫

正式環境 **SQL Server**，schema 手寫在 `db/mssql/01-schema.sql`（**不用** JPA `ddl-auto`，也不用 Flyway/Liquibase），改 schema 後要 `docker compose down -v` 重置。測試用 **H2 記憶體庫**（`application-test.yml`，`@ActiveProfiles("test")`，`ddl-auto: create-drop`）。種子資料只建部門/帳號/系統模板，不建業務資料。

## 跨功能不變式與已知陷阱

改動涉及看板、任務、成員、歸檔任一子系統前，先確認這些跨功能的一致性點：

- **「歸檔＝全員唯讀」貫穿任務與成員兩條路**：任務編輯靠 `canWriteBoard`（歸檔一律 false）；成員/負責人變更靠 `BoardController.checkCanManageMembers`（含 `board.isArchived()` 守衛，歸檔則丟 `SecurityException`→403）。動到成員/歸檔時，這兩處都要維持歸檔凍結，別只擋一邊。
- **移除成員會連帶清掉其在該看板的任務指派**：`BoardController.removeMember` 呼叫 `boardService.removeMember` 後再 `taskService.unassignFromBoard`，避免任務停在無效指派（`applyFields` 會擋「assignee 必須是成員」）。新增「移除成員」相關邏輯時要保留這個連帶清理。
- **`board.getOwner()` / `user.getDepartment()` 的 null 防禦**：schema 允許 `owner_id`/`department_id` 為 null。權限判斷處（`checkCanManageMembers`、`canReadBoard`/`listForUser` 的 DIRECTOR 分支）都先 null 檢查再比對；新增比對邏輯時沿用此防禦寫法。
- **WebSocket 廣播路徑的 DTO 轉換必須在交易內**：`onTaskMove` 用 `taskService.listResponsesByBoard`（`@Transactional`）而非在 controller 裡 `.map(from)`，否則 WS 執行緒無 OSIV，存取 LAZY 的 `assignee` 會拋 `LazyInitializationException`（僅「有指派負責人」的任務觸發，易被未指派任務的測試漏掉——見 `TaskSnapshotTest`）。`onTaskCreate`/`onTaskUpdate` 因 `applyFields` 會把 assignee 換成已載入實體或 null 而安全。
- **歸檔/還原是冪等的**：`archiveBoard`/`unarchiveBoard` 對已在目標狀態者直接 return，不重刷 `archivedAt`（避免擾亂歷史日期查詢）。

## 慣例

- 註解一律**繁體中文**，說明業務「Why」而非「What」；不留 `TODO`
- WebSocket 寫入路徑不經 Bean Validation，服務層（如 `TaskService.applyFields`）需自行做輸入防護
- 跨看板存取一律驗證資源歸屬（IDOR 防護），見 `TaskService.getInBoard`
- Git：commit 後需使用者明確說「推送」才能 push（本機 hook 攔截，見全域規範）
