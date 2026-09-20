# TM Platform — 可重放的增量迁移批次与可回滚发布

在既有单供应商 TMX 导入、术语替换、逐条审核、版本导出流程之上，新增多供应商
**增量迁移批次（replayable batches）** 与 **可回滚发布（rollback-capable releases）**。

- `backend/` — Spring Boot 3 (Java 17) + Spring Data JPA；PostgreSQL 数据模型见
  `backend/src/main/resources/schema.sql`（测试用 H2 PostgreSQL 兼容模式执行同一 DDL）。
- `frontend/` — Angular 18 双语对照审核页 + 版本 lineage 页。

## 核心机制

| 需求 | 实现 |
| --- | --- |
| 批次绑定 | `import_batch`：源版本、语言对、产品线、供应商、TMX 指纹(SHA-256)、术语映射规则快照 |
| 幂等导入 | 幂等键 `vendor\|langs\|line\|fingerprint` 唯一约束；重试/重复文件返回既有批次；候选 `(batch_id, source_hash)` 唯一 |
| 分片乱序 | `upload_session`/`upload_chunk`，`(session, index)` 唯一吸收重复分片，complete 时按序重组并校验指纹 |
| 冲突 | 同原句+语言对+产品线不同目标句 → `conflict_group` + `CONFLICT` 状态，链可查询 |
| 审核 | 乐观锁 `@Version` + `expectedVersion`；`review_decision.candidate_id` 唯一 → 不会双重审核；已决定状态拒绝重试覆盖；记录操作者/时间/依据/前序版本 |
| 迁移任务 | 检查点游标 `checkpoint_candidate_id`，每片独立事务；暂停/失败/恢复/断点续跑；只提交 `ACCEPTED` 且未提交条目；`migration_commit.candidate_id` 唯一防重复提交；`task_event` 全程可追溯 |
| 占位符 | 数量、名称、顺序、转义形式完全一致，否则 `PLACEHOLDER_MISMATCH` 隔离 |
| 大小写/多义词 | 按 `language_profile.case_rule` 校验；多义词必须人工选择批准项；异常一律阻断发布并引导人工处理 |
| 发布/回滚 | 新版本携带父版本、批次清单、冲突处理记录、内容校验值；回滚只生成带原因的反向版本并移动生效指针，历史版本与下载地址永不删除/改写 |

## 运行

```bash
# 后端（默认连 PostgreSQL：DB_URL/DB_USER/DB_PASSWORD 可覆盖）
cd backend && mvn spring-boot:run

# 无 PostgreSQL 时用 H2 演示
java -jar backend/target/tm-platform-1.0.0.jar \
  --spring.datasource.url='jdbc:h2:mem:tm;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1' \
  --spring.datasource.driver-class-name=org.h2.Driver

# 测试（5 个验收场景 + REST 冒烟）
cd backend && mvn test

# 前端
cd frontend && npm install && npm start   # 代理 /api -> localhost:8080
```

## API 一览

- `POST /api/batches` 幂等整包导入；`POST /api/batches/uploads[/...]` 分片上传（init/chunk/complete）
- `GET /api/batches/{id}/candidates`、`GET /api/batches/conflicts/{groupId}` 冲突链
- `POST /api/candidates/{id}/decisions` 审核（409=版本冲突/已决定，422=异常未解决）
- `POST /api/batches/{id}/tasks`、`POST /api/tasks/{id}/run|pause|resume`、`GET /api/tasks/{id}`（检查点+事件）
- `POST /api/versions/publish`、`POST /api/versions/{id}/rollback`、`GET /api/versions/{id}/lineage|tmx|checksum`
- `POST /api/legacy/import|replace` 既有单供应商流程（回归保障）

## 验收测试映射（`backend/src/test/java/com/acme/tm/AcceptanceTests.java`）

1. `scenario1` — 两种语言/多产品线导入，交错接受拒绝，暂停恢复，只迁移已接受条目
2. `scenario2` — 重复上传、分片乱序、双译审并发（含真实双线程竞争）→ 批次/审核/提交均唯一
3. `scenario3` — 缺失占位符、大小写违规、多义术语 → 隔离+人工解决后才放行，合法条目不丢失
4. `scenario4` — 迁移中崩溃 → 检查点续跑、提交不重复、校验值稳定、任务状态可追溯
5. `scenario5` — 既有单供应商导入、旧版本下载、普通术语替换回归
