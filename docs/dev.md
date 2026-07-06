# 開發與部署指南

本文件涵蓋 Spring-TaskFlow 的技術架構、本機開發、Docker 部署與測試。系統業務需求與功能說明見 [`../README.md`](../README.md)；給 AI 協作的架構指引見 [`../CLAUDE.md`](../CLAUDE.md)。

## 技術架構

| 層次 | 技術 |
|---|---|
| 後端 | Java 21 + Spring Boot 3.4 + Spring MVC |
| 安全 | Spring Security 6 + Spring Session JDBC（HttpOnly Cookie，無 JWT） |
| 即時協作 | Spring WebSocket + STOMP + SockJS |
| 前端 | Thymeleaf 3（SSR）+ Vue 3 CDN（離線靜態）、HTML5 原生 Drag & Drop、@stomp/stompjs |
| 資料庫 | SQL Server 2022（Docker） |
| 匯出 | Apache POI（XLSX）、後端組裝（JSON / CSV） |
| 部署 | Docker Compose（多階段建置） |

### 架構流程

```
瀏覽器
  │  Thymeleaf 頁面殼（SSR）+ 權限旗標
  │  Vue 3（CSR 互動層 + 原生拖曳）
  │  SockJS / STOMP（即時協作）
  ▼
Spring MVC Controller
  │  Spring Security（Session Cookie 驗證）
  ▼
Service Layer（BoardService 權限判斷為單一事實來源）
  │  JPA / Hibernate
  ▼
SQL Server 2022（業務資料 + Session 儲存）
```

## 環境變數

複製範本並設定機密，`.env` 已列入 `.gitignore` 不入版控：

```bash
cp .env.example .env
```

| 變數 | 說明 |
|---|---|
| `MSSQL_SA_PASSWORD` | SQL Server SA 密碼，需符合複雜度（大小寫 + 數字 + 符號、至少 8 碼） |
| `SPRING_DATASOURCE_URL` | （選用）覆寫 datasource 連線字串；容器內由 `docker-compose.yml` 自動設為 `db:1433` |

預設埠：**`http://localhost:8050`**（`application.yml` 的 `server.port`）。

## 方式一：Docker 一鍵部署（推薦）

應用與資料庫皆納入 compose，Dockerfile 為多階段建置，本機無需安裝 Maven／JDK：

```bash
cp .env.example .env            # 設定 MSSQL_SA_PASSWORD
docker compose up --build -d    # 建置 app 映像檔並啟動 db + app
```

啟動順序由 compose 控制：`db` 先啟動並執行 `db/mssql/entrypoint.sh` 建立 schema 與種子資料，healthcheck 確認 `users` 表就緒後才放行 `app` 啟動（避免建表前就連線）。

啟動後瀏覽 `http://localhost:8050`。查看日誌：`docker compose logs -f app`。

## 方式二：本機開發（IDE / 熱重載）

只用 Docker 起資料庫，應用在本機跑，方便除錯與熱重載：

```bash
cp .env.example .env
docker compose up -d db         # 只啟動資料庫
mvn spring-boot:run             # spring-dotenv 自動讀 .env；datasource 預設連 localhost:1433
```

或打包後執行：

```bash
mvn clean package -DskipTests
java -jar target/spring-taskflow-*.jar
```

## 測試

測試走 **H2 記憶體資料庫**（`src/test/resources/application-test.yml`，`@ActiveProfiles("test")`），不需啟動 Docker：

```bash
mvn test                                              # 全部
mvn test -Dtest=TaskServiceTest                       # 單一測試類別
mvn test -Dtest=TaskServiceTest#跨欄移動_改變狀態並插入指定位置   # 單一測試方法
```

## 資料庫

- Schema 手寫於 `db/mssql/01-schema.sql`（**不用** JPA `ddl-auto`，也不用 Flyway/Liquibase）
- 種子資料於 `db/mssql/02-seed.sql`，只建部門、帳號、系統模板，不建業務資料
- 首次啟動由 `entrypoint.sh` 自動執行，日誌出現 `>>> 初始化完成` 即就緒
- **改動 schema 或種子資料後必須重置**（因為不靠 JPA 自動建表）：

```bash
docker compose down -v && docker compose up --build -d
```

### 測試帳號

密碼皆為 `test1234`：

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

## 正式環境注意事項

- **機密注入**：`MSSQL_SA_PASSWORD` 與正式 `SPRING_DATASOURCE_URL` 應由部署平台的 secret 機制注入（環境變數 / K8s Secret），不要寫進映像檔或入版控的檔案。
- **Session 多實例**：Session 存於資料庫的 `SPRING_SESSION` 表（Spring Session JDBC），因此水平擴充多個 app 實例時 Session 自然共享，無需 sticky session。
- **WebSocket**：即時協作使用記憶體內的 simple broker，在線名單存於單一 app 實例記憶體。多實例部署時跨實例的在線狀態不會互通（任務資料仍經 DB 一致）；若需跨實例即時廣播，需改接外部 broker（如 RabbitMQ STOMP relay）。
- **HTTPS / 反向代理**：正式環境應置於反向代理（Nginx 等）之後終止 TLS，並轉發 WebSocket 升級標頭（`Upgrade` / `Connection`）；Session Cookie 建議設 `Secure` 屬性。
