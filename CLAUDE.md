# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 專案

reMockable Backend — AI 模擬面試產品的 MVP API（生成 → 回答 → 診斷 → 重答的循環）。
Java 21 / Spring Boot 3.5.5 / MySQL 8.0（目標部署為 AWS RDS）。MVC 分層：Controller → Service → Repository。

行為的單一事實來源是 `docs/spec.md`（產品規格 + API 契約 + 資料模型，繁體中文）。
`docs/openapi.yaml` 是由它衍生的機器可讀產物。實作任何 endpoint 前，先讀 `docs/spec.md`
對應章節 —— DTO 形狀、錯誤碼、業務規則都定義在那裡，不是只看程式碼就能推出來。

## 指令

```bash
# 本機 MySQL（用 3307 埠，避免與本機既有的 Homebrew MySQL 3306 衝突）
docker-compose up -d

# 複製 env 樣板，填入 DB／provider 設定
cp .env.example .env

# 啟動服務（讀取環境變數；預設 8081 埠 —— 8080 已被 Docker Desktop 佔用）
./mvnw spring-boot:run

# build
./mvnw clean package

# 跑全部測試
./mvnw test

# 只跑單一 test class
./mvnw test -Dtest=RespErrCodeTest

# 只跑單一 test method
./mvnw test -Dtest=RespErrCodeTest#someMethod
```

服務啟動後：Swagger UI 在 `/swagger-ui.html`，OpenAPI JSON 在 `/v3/api-docs`。

## 架構重點

**回應包裝 —— 每個 controller 只回三種形狀之一**（`CommonResp<T>`、`CommonPageResp<T>`、
`ErrorResp`），不會直接回 entity，也不會把錯誤塞進 body 卻永遠回 200。HTTP status
照實回（不會永遠 200）。欄位命名一律 camelCase。

**錯誤只走一條路徑**：業務程式碼丟出 `RespErr`（在 `exception/` 底下，內含一個
`RespErrCode`）；只有 `GlobalExceptionHandler` 負責把例外轉成 `ErrorResp`。
`RespErrCode` enum 值同時就是回給前端的 `code` 字串（用 enum 名稱本身，不是數字碼 ——
原因見 `RespErrCode` 上的 Javadoc，數字碼在多分支平行開發時容易撞號）。
例外訊息（`RespErr.getMessage()`）只寫進伺服器 log，絕不能回給前端 —— 前端只拿得到
`code` + `messageKey` + `retryable`。後端不回中文文案；`messageKey` 是給前端查表用的索引鍵。

**Request id 的傳遞**：`RequestIdFilter` 產生／接收 `X-Request-Id`，存進 `RequestContext`
（一個 `ThreadLocal`）與 SLF4J `MDC`，並回寫在 response header 與 `ErrorResp.requestId`。
非同步工作（見下方）需要把這個 id 帶過執行緒邊界 —— 透過 `AsyncConfig` 的 task decorator
處理，不要為了記 log 而把 id 一路加進方法簽章傳遞。

**非同步 AI job**：任何會呼叫 LLM／STT provider 的操作都太慢，撐不住同步 HTTP 回應，
因此走 job 模式：`POST` 回 `202` 附上 `jobId`／`status`／`pollAfterMs`，前端輪詢
`GET /api/jobs/{jobId}` 直到 `READY`／`FAILED`。Phase 1 用單機 `ThreadPoolTaskExecutor`
實作（`AsyncConfig`，bean 名稱 `aiTaskExecutor`）；設計上之後要換成 SQS + 獨立 worker
時不必動 controller 或輪詢契約，所以送件邏輯要維持在這個抽象介面後面。

**冪等**：`POST` endpoint 應該遵守 `Idempotency-Key`，由 `idempotency_keys` 表支撐
（`UNIQUE(idempotency_key, endpoint)` + `request_hash`，保留 24 小時）。這是為了滿足
產品規格的硬性要求：不可重複建立同一個 Attempt、重新整理頁面不應重複生成或重複扣模型成本、
失敗狀態可重試但不能覆蓋已成功的結果。

**ID**：主鍵一律是帶前綴的 ULID（`ms_...`、`job_...` 等），由 `common/IdGenerator` 產生，
不用自增整數 —— ID 會出現在 URL 與前端 `localStorage`，自增整數容易被列舉；ULID 的
字典序仍等同建立時間序。

**Schema／entity 的界線**：Flyway（`src/main/resources/db/migration/`）是 schema 的唯一
事實來源；Hibernate 用 `ddl-auto: validate`，entity 必須與 migration 完全一致 —— 對不上
會在啟動時直接失敗，不會自動校正。V1–V8 一次建好全部 18 張表，即使 P1A/P1B 實際只會寫入
約 10 張，是為了避免之後在 RDS 上加欄位需要的停機窗口。`docs/spec.md` §5.4 說明了幾個
不直覺的 schema 決策（JD／履歷凍結快照、用獨立配額表而非 event 計數、Fit 分數反正規化成
實體欄位等）—— 改動清單上任何一張表之前，先讀那一節，因為那些理由正是 code review 時會被問到的部分。

**Numeric score 不能離開 service 層**：`analyses` 表存數字 Fit 分數供內部模型品質評估使用，
但 DTO 層只能對外暴露對應的 `*State` enum（`GREEN`／`YELLOW`／`RED`）—— 這是硬性產品要求
（spec D-025），不是該「修正」的疏漏。

**Auth 在 P2C 前是選填的**：P1A–P2B 階段忽略 `Authorization: Bearer`，P2C 起才必要。
所有表已經預先建好 nullable 的 `user_id`，不需要為此另外寫 migration，P2C 上線時直接開始寫入即可。

## 慣例

- Controller：路由、DTO 驗證、包裝 `CommonResp`／`CommonPageResp` —— 不寫業務規則、不碰 Repository。
- Service：業務規則、交易邊界、冪等、呼叫 AI provider —— 不知道 HTTP 的存在。
- Repository：只用 Spring Data JPA —— 不寫業務判斷。
- 全專案使用 Lombok（`@Data`、`@Getter` 等）—— accessor 是產生的，不是手寫的。
