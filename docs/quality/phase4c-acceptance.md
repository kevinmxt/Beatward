# Phase 4C 验收证据

本文件记录 Phase 4C 各切片的验收证据；完整阶段收口（CI 链接、真实通知证据、恢复演练）在 #123 补齐。

## 4C-1 邀请注册与账号状态

- 已合并：PR #124，合并提交 `6411854`。
- 证据：`RegistrationApiIT`、`AccountStatusIT`、`AppUserRepositoryIT`；本地 `./mvnw verify` 245 个测试通过，前端 176 个测试与构建通过。
- 覆盖：邀请码并发不超卖、四类邀请码错误、用户名冲突回滚名额、重置码单次有效、禁用登录与会话失效、CSRF、限流、日志脱敏。

## 4C-2a 归属隔离 expand

- Flyway `V15__add_monitor_user_ownership.sql` 增加可空 `monitor.user_id`、外键与 `monitor(user_id, enabled)` 索引，并回填现有数据到最早创建的启用 `ADMIN`。
- 若存在 Monitor 但没有启用 `ADMIN`，V15 直接失败，避免静默留下 `NULL`。从 Phase 4A 直接升级的库必须先在 Phase 4B 完成引导管理员创建，再应用 V15；从 Phase 4B/5A 正常升级时管理员已存在。
- 新建 HTTP/Heartbeat 监控写入当前认证用户归属；业务 API 行为保持不变。
- 证据：`MonitorOwnershipMigrationIT` 覆盖启用管理员回填、无启用管理员时失败、多管理员取最早启用者，以及可空列、外键与索引；`MonitorApiIT` 覆盖 HTTP/Heartbeat 创建归属。本地 `./mvnw verify` 250 个测试通过，Monitor/Incident/Heartbeat/通知相关集成测试保持绿色。

### 迁移前备份与回退要求

- 升级到 V15 前按 [`deployment.md`](deployment.md) 的备份流程生成数据库备份，并确认备份可读取。
- V15 只增加可空列、索引与回填，不回写或删除现有业务字段；回退策略是恢复升级前备份，因为 Flyway 不提供 down migration。
- 回填目标是 `id` 最小的启用 `ADMIN`；多管理员时在升级记录中注明实际回填目标。若没有启用管理员，迁移失败并保持可重试状态：完成引导管理员创建后可重新执行 V15。
- 多用户备份恢复复验在 4D-2 执行，不在本切片重复。

## 4C-2b 归属隔离 migrate

- 业务 API（Monitor、Incident、心跳凭据）对任意已认证账号开放，并按当前用户 `monitor.user_id` 过滤；跨用户读取/修改/删除统一 404，管理员默认也不能读取他人业务数据。
- HTTP 探测、心跳扫描与通知投递解析归属账号状态；调度 due 查询与执行期复核跳过禁用账号，心跳上报拒绝，通知投递标记失败；重新启用后恢复。
- 证据：`OwnershipIsolationIT`（用户列表/详情/历史/心跳令牌/Incident 隔离，管理员边界）、`DisabledAccountSchedulingIT`（HTTP/心跳 due 查询跳过禁用账号、心跳上报拒绝）、`NotificationFlowIT`（禁用账号跳过投递并标记失败）。
- 本地 `./mvnw verify` 259 个测试通过，BUILD SUCCESS；最终 CI 链接在 #123 统一记录。

## 4C-2c 归属隔离 contract

- Flyway `V16__monitor_ownership_contract.sql` 将 `monitor.user_id` 收紧为 `NOT NULL`（如仍有空值则回填到最早创建的启用 `ADMIN`），并把 HTTP 探测间隔下限从 10 秒提高到 60 秒；已有低于 60 秒的 HTTP 配置在迁移中归一到 60 秒，HEARTBEAT 下限保持 30 秒。
- 每账号最多 20 个 Monitor（HTTP + HEARTBEAT 合计）；创建时锁定 `app_user` 行再计数，并发创建不会超卖；超限返回 409 `QUOTA_EXCEEDED`，消息含当前用量与上限，并提示删除既有 Monitor。
- 用户可见的 Incident 监控名称查询全部带归属条件；移除 `AccountActivity` 对空归属的兼容分支。
- 证据：`MonitorOwnershipMigrationIT`（NOT NULL、HTTP 间隔归一、迁移后拒绝空归属与低于 60 秒的 HTTP 间隔）、`MonitorQuotaIT`（混合类型计数、按用户隔离、30 并发创建仅 20 个成功）、`MonitorApiIT`（409 错误与可操作消息）、`MonitorConfigValidatorTest`（60 秒边界）、`AccountActivityTest`（空归属与禁用账号判定）；同步更新 `MonitorRepositoryIT`、探测/清理/重启等测试的归属数据准备。
- 本地 `./mvnw verify` 267 个测试通过，BUILD SUCCESS；前端 `npm test` 176 个测试通过，`npm run build` 成功。

## 4C-3 管理员控制台与用户管理

- 新增 `GET /api/v1/admin/invite-codes`、`POST /api/v1/admin/invite-codes/{id}/revoke`、`GET /api/v1/admin/users`、`PATCH /api/v1/admin/users/{id}/status`；沿用一次性重置码接口。
- 邀请码列表不含明文或哈希，撤销幂等；用户列表只返回账号元数据与监控数量，不含监控内容。
- 禁用账号立即失效其全部会话；锁定启用管理员后再修改状态，禁止禁用最后一个启用管理员，冲突返回 409 `LAST_ACTIVE_ADMIN`。
- 管理员操作写入 `dev.beatward.audit` 结构化日志，覆盖邀请码创建/撤销、账号状态变更与重置码生成，不记录明文码。
- 前端新增 `/admin/invites`、`/admin/users`，`ADMIN` 导航显示管理入口，`USER` 不显示且路由守卫重定向到 `/monitors`。
- 证据：`AdminConsoleApiIT` 12 个用例（列表字段脱敏、撤销幂等、监控数量、禁用即失效会话、未认证 401、`USER` 越权 403、多管理员与并发禁用保护、状态校验、审计日志）；`AdminInvitesView.test.ts`、`AdminUsersView.test.ts`、`router/index.test.ts`、`App.test.ts`（管理页渲染与操作、角色导航与路由拒绝）。
- 本地 `./mvnw verify` 279 个测试通过，BUILD SUCCESS；前端 `npm test` 189 个测试通过，`npm run build` 成功。

## 待完成

- 4C-4/#121、4C-5/#122。
- 最终 CI 链接、真实 PushPlus/息知/邮件告警证据与人工走查记录在 #123 补齐。
