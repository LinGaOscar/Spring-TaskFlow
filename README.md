# Spring-TaskFlow

企業後台 Kanban 任務看板多人即時協作系統。

## 技術架構

| 層次 | 技術 |
|---|---|
| 後端 | Java 21 + Spring Boot 3.4 + Spring MVC |
| 安全 | Spring Security 6 + Spring Session JDBC（HttpOnly Cookie，無 JWT） |
| 即時協作 | Spring WebSocket + STOMP + SockJS |
| 前端 | Thymeleaf 3 + Vue 3 CDN（離線靜態），HTML5 原生 Drag & Drop |
| 資料庫 | SQL Server 2022（Docker） |
| 匯出 | Apache POI（XLSX）、後端組裝（JSON / CSV） |
| 部署 | Docker Compose |

### 架構流程

```
瀏覽器
  │  Thymeleaf 頁面（SSR）
  │  Vue 3（CSR 互動層 + 原生拖曳）
  │  SockJS / STOMP（即時協作）
  ▼
Spring MVC Controller
  │  Spring Security（Session Cookie 驗證）
  ▼
Service Layer
  │  JPA / Hibernate
  ▼
SQL Server 2022（資料 + Session 儲存）
```

### 角色與權限

組織架構：部（Division）→ 科（Section），部長屬於部、其餘角色屬於科。

| 角色 | 說明 | 建看板 | 拖曳/編輯任務 | 管理成員 | 管理模板 | 跨科查閱 | 歸檔/還原 |
|---|---|:---:|:---:|:---:|:---:|:---:|:---:|
| `DIRECTOR` | 部長 | ✅（掛在部） | 僅自建看板 | 僅自建看板 | ❌ | ✅（唯讀） | ❌ |
| `SECTION_CHIEF` | 科長 | ✅ | ✅（本科） | ✅（本科） | ✅ | ❌ | ✅（本科） |
| `PROJECT_LEADER` | 看板 Leader | ✅ | 限加入的看板 | 限自己的看板 | ✅ | ❌ | ✅（限自己負責的看板） |
| `PROJECT_MEMBER` | 看板 Member | ❌ | 限加入的看板 | ❌ | ❌ | ❌ | ❌ |

### 部門隔離

- 看板建立時自動繼承建立者所屬科（部長建立則掛在部層級）
- 科長、Leader 只能看本科看板；Member 只能看被加入的看板
- 部長可查閱下屬所有科的看板，但不得編輯（自建的除外）
- 歸檔的看板對全員強制唯讀

---

## 快速啟動

```bash
# 1. 複製 .env 並設定密碼
cp .env.example .env   # 編輯 MSSQL_SA_PASSWORD

# 2. 啟動 SQL Server（首次自動執行 db/mssql/ 建立 schema 與種子資料）
docker compose up -d

# 3. 編譯並啟動應用
mvn clean install -DskipTests
java -jar target/spring-taskflow-*.jar
```

預設埠：`http://localhost:8080`

> **重置資料庫**：`docker compose down -v && docker compose up -d`

---

## 功能說明

### 認證

| 功能 | 說明 |
|---|---|
| 登入 | 電子郵件 + 密碼，路徑 `/login` |
| 登出 | 清除 Session，重導至登入頁 |
| Session | 儲存於 SQL Server `SPRING_SESSION` 表（HttpOnly Cookie） |
| 未登入保護 | 存取任何頁面自動重導至 `/login` |

---

### 看板管理

| 功能 | 說明 | 可操作角色 |
|---|---|---|
| 看板列表 | 依角色顯示可見看板卡片（`/boards`） | 全員 |
| 新增看板 | 填寫名稱，建立後自動綁定所屬科 | DIRECTOR / SECTION_CHIEF / PROJECT_LEADER |
| 歸檔看板 | 完成後移至歷史，全員強制唯讀 | SECTION_CHIEF（本科）/ PROJECT_LEADER（限自己負責的看板） |
| 還原歸檔 | 將歷史看板重新移回進行中 | SECTION_CHIEF（本科）/ PROJECT_LEADER（限自己負責的看板） |
| 歷史看板查詢 | 依關鍵字（看板名稱）與歸檔日期區間查詢（`/boards/history`） | 全員 |
| 不存在看板 | 返回自訂 404 頁面，非 Whitelabel Error | — |

---

### Kanban 看板操作

路徑：`/boards/{id}`

| 功能 | 說明 |
|---|---|
| 三欄檢視 | 待辦（TODO）/ 進行中（IN_PROGRESS）/ 完成（DONE），欄頂顯示任務數 |
| 拖曳任務 | 跨欄拖曳改變狀態、欄內拖曳調整排序，重整後狀態保留（HTML5 原生 Drag & Drop） |
| 卡片顯示 | 標題、負責人、到期日（逾期未完成紅色警示）、優先級色條（高紅 / 中橙 / 低綠） |
| 新增任務 | 填寫標題 / 描述 / 負責人（看板成員下拉）/ 到期日 / 優先級 |
| 套用模板 | 新增任務時選取模板，自動帶入標題 / 描述 / 優先級，可再修改 |
| 編輯任務 | 點卡片開 modal 編輯所有欄位 |
| 刪除任務 | modal 內刪除，需 `confirm()` 二次確認 |
| 匯出 | 工具列下拉匯出 JSON / CSV / XLSX |
| 歸檔鎖定 | 看板歸檔後工具列鎖定，拖曳與編輯皆停用 |

---

### 即時協作（WebSocket）

| 功能 | 說明 |
|---|---|
| 任務即時同步 | 任一方新增 / 修改 / 拖曳 / 刪除，所有在線成員畫面即時更新，無需重整 |
| 在線協作者 | 右上角顯示目前在線成員頭像 |
| 歸檔鎖定同步 | 歸檔後其他在線端立即轉為唯讀 |
| 權限保護 | 每次 WebSocket 操作皆驗證 `canWriteBoard()`；訂閱時驗證讀取權限 |

---

### 任務模板管理

路徑：`/templates`

| 功能 | 說明 | 可操作角色 |
|---|---|---|
| 查看系統模板 | 3 個系統預設模板（Bug 修復 / 需求開發 / 例行維運），唯讀 | 全員 |
| 查看自訂模板 | 本科建立的自訂模板 | 全員（本科） |
| 新增自訂模板 | 填寫名稱、任務標題、描述、優先級 | SECTION_CHIEF / PROJECT_LEADER |
| 編輯 / 刪除自訂模板 | 系統模板不可改不可刪 | SECTION_CHIEF / PROJECT_LEADER（本科） |

> 自訂模板以科（Section）為共用單位，跨科不可見。

---

### 歷史查詢

路徑：`/boards/history`

| 功能 | 說明 |
|---|---|
| 關鍵字查詢 | 依看板名稱模糊搜尋 |
| 日期區間查詢 | 依歸檔日期篩選 |
| 可見範圍 | 依部門隔離：Member 限被加入過的、科長 / Leader 限本科、部長全部（唯讀） |
| 還原 | 科長可還原本科任一看板；Leader 僅限自己負責的看板還原為進行中 |

---

### 匯出

| 格式 | 說明 |
|---|---|
| JSON | 看板任務清單完整欄位（標題、狀態、負責人、到期日、優先級、描述） |
| CSV | 含 BOM，中文正常顯示不亂碼 |
| XLSX | Apache POI 產生的 Excel 檔 |

> 匯出為唯讀操作，凡可檢視該看板者（含歸檔看板與部長跨科唯讀）皆可使用。

---

### 成員管理

路徑：`/admin/members`（SECTION_CHIEF / PROJECT_LEADER 限定）

| 功能 | 說明 |
|---|---|
| 查看成員 | 列出本科每個看板的成員清單 |
| 新增成員 | 將科內帳號加入指定看板 |
| 移除成員 | 將成員從看板中移除，立即失去編輯權限 |

---

### 錯誤處理

| 狀況 | 行為 |
|---|---|
| 存取不存在的看板 | 顯示自訂 404 頁面（含側欄導覽，可返回首頁） |
| 無存取權限 | 重導至 `/boards`，不顯示錯誤訊息 |
| 伺服器錯誤 | 顯示自訂 5xx 頁面 |
| 未登入 | 任何頁面自動重導至 `/login` |

---

## 測試帳號

密碼皆為 `test1234`

| Email | 角色 | 所屬 |
|---|---|---|
| `director@company.com` | DIRECTOR | 資訊部 |
| `chief@infotech.com` | SECTION_CHIEF | 資訊科 |
| `leader@infotech.com` | PROJECT_LEADER | 資訊科 |
| `member1@infotech.com` | PROJECT_MEMBER | 資訊科 |
| `member2@infotech.com` | PROJECT_MEMBER | 資訊科 |
| `chief2@infotech.com` | SECTION_CHIEF | 資訊科2 |
| `leader2@infotech.com` | PROJECT_LEADER | 資訊科2 |
| `member3@infotech.com` | PROJECT_MEMBER | 資訊科2 |

---

## 重置資料庫

```bash
docker compose down -v && docker compose up -d
```

首次啟動（或重置後）需等待 `db/mssql/entrypoint.sh` 自動執行 schema 建立與種子資料匯入，日誌出現 `>>> 初始化完成` 即代表就緒。
