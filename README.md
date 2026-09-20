# TM Hub — 可重放的增量迁移批次与可回滚发布

在既有「TMX 导入 → 术语迁移 → 逐条审核 → 版本导出」流程之上，新增面向多供应商增量修订包的
**可重放增量迁移批次** 与 **可回滚发布** 能力。历史版本永不改写，所有决定可追溯。

```
backend/    Spring Boot 3 (Java 17) + PostgreSQL + Flyway
frontend/   Angular 17 双语对照审核页
```

## 核心概念

| 概念 | 说明 |
|---|---|
| **批次 (batch)** | 一次增量交付。绑定：源记忆库版本、语言对、产品线、供应商、TMX 指纹、术语映射规则快照。幂等键 = sha256(指纹\|源版本\|语言对\|产品线\|供应商)，重复导入/重试返回同一批次 |
| **分片 (batch_shard)** | 大文件分片上传。`(batch_id, shard_index)` 主键去重，乱序到达不影响；收齐后按序拼装、校验指纹、解析生成候选 |
| **候选 (candidate)** | 一条替换建议。`proposal_key` 全局唯一 → 重试/重复文件不产生重复候选；`identity_key` = sha256(语言对\|产品线\|原句) |
| **冲突 (conflict_group)** | 同一原句+语言对+产品线出现不同目标句时建组，候选进入 `CONFLICT`，链路可查 |
| **审核 (review_decision)** | 追加式审计表：操作者、时间、依据、前序状态/前序最终译文。已决定候选进入终态，后续重试不得覆盖 |
| **迁移任务 (migration_task)** | 按候选状态推进，只提交 `ACCEPTED` 且未提交的条目；检查点 = `last_candidate_id`，每块一个事务，支持暂停/失败/重启/断点续跑 |
| **发布 (tm_version)** | 不可变版本：父版本、批次清单、冲突处理记录、内容校验值（sha256）。生效指针单独移动并留痕 |
| **回滚** | 只生成带原因的反向版本（`ROLLBACK`）或移动生效指针（`POINTER`），绝不删除/改写已发布版本 |
| **导出 (export_task)** | 分块断点续传式 TMX 导出；条目按 id 排序、格式固定 → 校验值跨重启稳定；旧版本下载地址永久有效 |

## 数据模型（PostgreSQL, Flyway `V1__schema.sql`）

- `tm_version`(label, kind, parent_id, checksum, reason…) + `version_pointer` + `version_pointer_history`
- `tm_entry`(version_id, identity_key, source/target, origin, origin_candidate_id)，`UNIQUE(version_id, identity_key)`
- `batch`(idempotency_key UNIQUE, tmx_fingerprint, source_version_id, 语言对, product_line, vendor, mapping_rules_snapshot, expected/received_shards, status)
- `batch_shard`(batch_id, shard_index, content) PK 去重
- `candidate`(proposal_key UNIQUE, identity_key, group_key, status, committed, `version` 乐观锁）
- `conflict_group`(identity_key UNIQUE, status, resolved_candidate_id, resolved_by/at, note)
- `review_decision`（追加式：reviewer, action, rationale, previous_status/final_target, decided_at）
- `candidate_anomaly`(type: PLACEHOLDER_MISMATCH / CASING_VIOLATION / POLYSEMY, resolved…)
- `migration_task`(status, total, committed_count, last_candidate_id 检查点， `version` 乐观锁）
- `migration_commit`(`candidate_id` UNIQUE → 任何情况下不会双重提交）
- `export_task` / `export_chunk` / `export_artifact`（断点续跑导出 + 永久下载内容）
- `term_mapping_rule`(JSONB 规则；多义词 = 一个源词多个目标词）, `language_case_rule`（语言大小写配置）

## 质量保证规则

1. **占位符/转义**：`{0}`、`{name}`、`{{name}}`、`%s`、`%1$s`、`<x/>`、`<ph/>` 及 `\n \t \r \" \\`
   转义形式在源句与目标句中必须数量、名称、顺序一致，否则隔离（`PLACEHOLDER_MISMATCH`）。
2. **大小写**：按 `language_case_rule` 语言配置执行（术语规定大小写形式、句首大写），违规即隔离。
3. **多义词**：映射规则中一对多的源词命中时**绝不自动猜测**，候选隔离（`POLYSEMY`），必须人工
   `SET_FINAL` 指定最终术语后才可继续。
4. 存在未解除隔离（`BLOCKED`）或未决冲突（`CONFLICT`）时，发布返回 **422 publish_blocked**；
   其余合法条目不受影响、正常迁移。
5. 人工给出的最终译文同样强制校验占位符，不合规直接 400 拒绝。

## 并发与重试语义

- 审核：候选行 `SELECT … FOR UPDATE` 悲观锁 + `@Version` 乐观锁 + 前端 `expectedVersion`。
  两人同时审核同一候选 → 后到者收到 **409 version_conflict**，UI 提示并刷新，绝不产生双重审核结果。
- 已决定（ACCEPTED/REJECTED）候选再次决定 → 409；重试导入不会重置任何已作出的决定。
- 冲突组内接受某一候选 → 其余未决候选自动 `REJECTED` 并写入 `AUTO_SUPERSEDE` 审计记录。
- 任务/导出每块一个事务，检查点与提交同事务落库；进程死亡后启动时 `RUNNING → INTERRUPTED`，
  `resume` 从检查点继续；`migration_commit.candidate_id UNIQUE` 兜底防重。

## REST API 摘要

```
POST   /api/batches                         幂等创建批次（绑定源版本/语言对/产品线/供应商/指纹/规则快照）
POST   /api/batches/{id}/shards/{i}         上传分片（乱序/重复安全），收齐自动解析生成候选
GET    /api/batches | /api/batches/{id}     批次查询
GET    /api/batches/{id}/candidates         批次候选列表
GET    /api/candidates/{id}                 候选详情（占位符差异、异常、审核历史、冲突链）
POST   /api/candidates/{id}/decisions       审核 {reviewer, action: ACCEPT|REJECT|SET_FINAL,
                                            finalTarget, rationale, expectedVersion} → 409 冲突提示
GET    /api/candidates/{id}/decisions       审核历史
GET    /api/conflicts/{groupId}[/candidates] 冲突组与冲突链
POST   /api/tasks                           创建迁移任务 {batchId, name}
POST   /api/tasks/{id}/start|pause|resume   启动 / 暂停 / 断点续跑
GET    /api/tasks | /api/tasks/{id}         任务与检查点查询
GET    /api/tasks/{id}/commits              已提交条目
POST   /api/versions/publish                发布 {label, batchIds, actor} → 新不可变版本
POST   /api/versions/{id}/rollback          回滚 {mode: REVERSE_VERSION|POINTER, reason, actor}
GET    /api/versions | /api/versions/{id}/lineage   版本与 lineage（父链、批次清单、冲突记录、生效指针）
POST   /api/versions/{id}/export            断点续跑导出 TMX
GET    /api/versions/{id}/export            下载 TMX（旧版本地址永久有效，X-Checksum 头）
POST   /api/legacy/import                   原有单供应商 TMX 导入（回归保持）
POST   /api/legacy/term-replace             原有普通术语替换（回归保持）
POST   /api/config/term-mapping-rules       术语映射规则（含多义词）
POST   /api/config/case-rules               语言大小写规则
```

## 运行

```bash
# 后端（需要 JDK 17+ 与本地 PostgreSQL，见 src/main/resources/application.yml）
cd backend && mvn spring-boot:run

# 无本地 PostgreSQL 时：内嵌 PostgreSQL 启动真实 API（:8080）
cd backend && mvn spring-boot:test-run

# 前端（:4200，代理 /api → :8080）
cd frontend && npm install && npm start

# 测试（内嵌 PostgreSQL，无需外部服务）
cd backend && mvn test
```

## 验收场景 ↔ 测试对照

| 验收场景 | 测试 |
|---|---|
| 两种语言、多产品线增量包；交错接受/拒绝；暂停恢复；只迁移已接受 | `IncrementalImportAcceptanceTest` |
| 重复上传、分片乱序、两人同时审同一候选 → 批次/审核/提交均只一份，冲突完整记录 | `IdempotencyConcurrencyAcceptanceTest` |
| 缺失占位符、大小写不合规、多义术语 → 隔离、阻断发布、人工解除，合法条目不丢失 | `AnomalyQuarantineAcceptanceTest` |
| 迁移/导出中服务重启 → 检查点续跑、不重复提交、校验值稳定、状态可追溯 | `RestartRecoveryAcceptanceTest` |
| 回归：单供应商 TMX 导入、旧版本下载、普通术语替换 | `LegacyRegressionTest` |
| REST 端到端（幂等批次、409、任务、发布） | `ApiSmokeTest` |

## Angular 双语对照页

- **批次列表/详情**（`/batches`, `/batches/:id`）：原句 vs 目标句对照、替换位置高亮（词级 diff）、
  占位符差异标记、候选来源（供应商/批次）、当前状态、异常标记；接受/拒绝/指定最终术语；
  409 时显示版本冲突横幅并自动刷新；展开行显示完整审核历史与冲突链。
- **任务监控**（`/tasks`）：状态、进度、检查点、错误；启动/暂停/恢复。
- **版本 lineage**（`/versions`）：父链、批次清单、冲突处理记录、校验值、生效指针；
  导出/下载（旧版本地址有效）、带原因的回滚。
