# 核心数据模型

本文件定义核心业务语义。字段和索引可以随实现调整，但不能丢失以下语义。

## Monitor
表示一个监控项。

核心字段：
- `id`
- `user_id`：归属账号，外键到 `app_user`，`NOT NULL`；Phase 4C-2a 以可空列加入并回填，4C-2c 收紧为非空
- `name`
- `type`：`HTTP` 或 `HEARTBEAT`
- `target_url`：仅 HTTP 类型，心跳类型为 `NULL`
- `method`：仅 HTTP 类型
- `interval_seconds`：HTTP 为 60–86400 秒（托管 profile 下限 60 秒）；HEARTBEAT 为 30–2592000 秒
- `connect_timeout_ms`：仅 HTTP 类型
- `response_timeout_ms`：仅 HTTP 类型
- `expected_status_min`：仅 HTTP 类型
- `expected_status_max`：仅 HTTP 类型
- `heartbeat_grace_seconds`：仅 HEARTBEAT 类型，0–86400 秒
- `last_heartbeat_at`：最后一次有效上报时间；心跳截止时间按 `(last_heartbeat_at ?? created_at) + interval_seconds + heartbeat_grace_seconds` 计算
- `enabled`
- `current_status`：`UNKNOWN`、`UP`、`DOWN`；由连续失败/成功达到阈值的状态机决定，不由单次 Probe 结果决定。`UNKNOWN` 下首次成功直接变为 `UP`。
- `last_checked_at`
- `last_success_at`
- `next_check_at`：内部调度字段，不对外暴露
- `config_version`：HTTP 类型的内部并发控制字段。`target_url`、`method`、`interval_seconds`、`connect_timeout_ms`、`response_timeout_ms`、`expected_status_min`、`expected_status_max` 变化时递增；`name`、`enabled`、阈值不计入版本。心跳类型不使用此字段。
- `consecutive_failures`：连续失败 Probe 计数，成功时清零。
- `consecutive_successes`：连续成功 Probe 计数，失败时清零。
- `failure_threshold`：进入故障前所需的连续失败次数。
- `recovery_threshold`：恢复前所需的连续成功次数。
- `created_at`
- `updated_at`

`config_version` 用于探测结果写回时的条件校验；行为规范见 `specs/monitoring.md`。

`monitor.user_id` 是多用户归属根。CheckResult、Incident、HeartbeatCredential 通过 `monitor_id` 推导归属，NotificationDelivery 通过 `incident_id` 推导；不重复存储 `user_id`。新建 Monitor 必须写入当前认证用户；索引 `monitor(user_id, enabled)` 支撑按用户查询与调度过滤。

所有用户可见查询必须显式携带当前用户归属条件；跨用户读取、修改或删除统一返回 404，不泄露资源是否存在。管理员默认同样不能通过业务 API 读取他人数据。探测与心跳扫描只选择所属账号为 `ACTIVE` 的 Monitor；通知投递解析到禁用账号时跳过并标记失败。

Phase 2 的计数与阈值在探测写回事务内更新；探测语义配置变更、禁用和重新启用时计数清零。修改阈值不追溯已有计数。HTTP 类型必须配置完整的目标 URL、方法、超时与期望状态码，由数据库 CHECK 约束保证；心跳类型不参与这些字段。`next_check_at` 对 HTTP 表示下次探测时间，对心跳表示下次超时扫描时间，也是锁定后重新校验的截止时间；心跳重新启用时设为启用时间加间隔与宽限。各阶段迁移只创建该阶段需要的列。

对时间间隔、超时、URL 和状态字段实施服务端校验。连接超时与响应超时分别对应建连阶段和已建连后的响应阶段，见 ADR-0004。

## CheckResult
表示一次探测或检查结果。

核心字段：
- `id`
- `monitor_id`
- `started_at`
- `finished_at`
- `duration_ms`
- `success`
- `failure_type`
- `http_status_code`
- `error_summary`

按 `monitor_id` 和时间字段建立适当索引。错误摘要应截断，不能包含凭证。心跳有效上报写入 `success = true` 的 CheckResult，`duration_ms = 0`，失败相关字段为空；心跳超时只在创建 Incident 时写入一条 `HEARTBEAT_TIMEOUT` 失败结果。

## Incident
表示一次持续故障事件。

核心字段：
- `id`
- `monitor_id`
- `started_at`
- `resolved_at`
- `status`
- `failure_type`
- `summary`

同一监控项最多有一个未解决的当前 Incident，由部分唯一索引与探测写回的行锁保证。`status` 取 `OPEN` 或 `RESOLVED`；恢复时写入 `resolved_at`。禁用 Monitor 时静默关闭未解决 Incident，删除时级联删除。心跳超时的 `started_at` 使用截止时间，失败类型为 `HEARTBEAT_TIMEOUT`；有效上报在接收时间关闭 Incident。

## HeartbeatCredential
- `id`
- `monitor_id`
- `token_hash`：令牌的 SHA-256 十六进制摘要，全局唯一
- `created_at`
- `revoked_at`

数据库只保存安全摘要，不保存明文令牌。令牌必须足够随机并可撤销。每个 Monitor 同时最多一个未撤销凭证，由部分唯一索引保证；删除 Monitor 时级联删除凭证。

## AppUser
账号的唯一事实来源。

核心字段：
- `id`
- `username`：唯一、区分大小写，1–64 字符，匹配 `[A-Za-z0-9._-]`；输入前后空白在入库前裁剪
- `password_hash`：`{bcrypt}$2...`，BCrypt 成本 12；不保存明文或可逆密文
- `role`：`ADMIN` 或 `USER`，由 CHECK 约束保证
- `status`：`ACTIVE` 或 `DISABLED`，默认 `ACTIVE`；禁用时记录 `disabled_at`
- `last_login_at`：最近一次成功登录时间，可空
- `created_at`
- `updated_at`

首次启动时如果表为空，应用使用 `BEATWARD_AUTH_BOOTSTRAP_USERNAME` 与 `BEATWARD_AUTH_BOOTSTRAP_PASSWORD_HASH` 创建唯一管理员；变量缺失、用户名或哈希非法时拒绝启动。表非空时忽略引导变量并记录 WARN，不覆盖已有账号。自助注册只能创建 `USER`。认证与启动校验行为见 [`specs/auth.md`](../specs/auth.md)。

管理员用户列表只读取账号元数据，并按 `monitor.user_id` 聚合监控数量；同一查询不读取 Monitor 名称、目标 URL、历史或通知内容。禁用账号写入 `status`/`disabled_at`，并立即使该账号全部会话失效。

## InviteCode

- `id`
- `code_hash`：邀请码的 SHA-256 十六进制摘要，唯一；数据库不保存明文
- `label`：管理员备注，可空
- `max_uses`：1–100
- `used_count`：0 到 `max_uses`
- `expires_at`
- `revoked_at`：可空
- `created_by`：创建管理员 `app_user.id`
- `created_at`

名额占用使用条件 UPDATE 原子完成，并发注册不超卖；明文只在创建响应中返回一次。撤销通过写入 `revoked_at` 完成，重复撤销幂等；管理端列表只返回元数据，不返回明文或哈希。

## PasswordResetToken

- `id`
- `token_hash`：重置码的 SHA-256 十六进制摘要，唯一；数据库不保存明文
- `user_id`：目标账号
- `expires_at`：默认创建后 24 小时
- `used_at`：单次使用，成功后写入
- `created_by`：生成该码的管理员
- `created_at`

明文只在生成响应中返回一次。密码更新与 `used_at` 标记在同一事务内完成，成功后使目标账号全部会话失效。

## NotificationDelivery
- `id`
- `incident_id`
- `channel`：`TELEGRAM`
- `event_type`：`INCIDENT_OPENED` 或 `INCIDENT_RESOLVED`
- `status`：`PENDING`、`SENT`、`FAILED`
- `attempt_count`
- `next_attempt_at`
- `last_attempt_at`
- `sent_at`
- `last_error_summary`

同一 `incident_id`、`event_type` 与 `channel` 唯一。投递行与 Incident 变更在同一事务内写入；发送在独立事务与调度线程中完成。

用于追踪通知发送和重试，不得把通知失败与故障状态回滚绑定。投递采用 at-least-once 语义，见 [ADR-0005](../adr/0005-transactional-outbox-notifications.md)。

## 历史数据保留与清理

- `CheckResult` 按 `started_at` 保留 30 天（`beatward.cleanup.check-result-retention-days`，1–3650 天）。
- 已解决 Incident 按 `resolved_at` 保留 90 天（`beatward.cleanup.resolved-incident-retention-days`，1–3650 天），其 NotificationDelivery 由外键级联删除。
- `OPEN` Incident 及其投递永不按时间清理；Monitor 删除仍由既有级联处理。
- 到期仍滞留的 `PENDING` 投递随 Incident 删除并记录数量；通知投递器遇到已不存在的投递行按无操作处理。
- 独立 `cleanup-tick-` 单线程调度器默认每小时执行（`tick-interval`，1m–24h）；`enabled` 控制是否执行清理，`scheduling-enabled` 控制是否注册调度。单批 1000 行（`batch-size`，1–10000），每批独立短事务，批间暂停 50ms，单 tick 最多 50 批（`max-batches-per-tick`，1–1000）；该批次预算由 CheckResult 与已解决 Incident 共享，同一 tick 依次处理两类数据。
- 索引：`check_result(started_at)` 与 `incident(resolved_at) WHERE status = 'RESOLVED'`。
- 删除数量以 INFO 日志与 `cleanup.removed` 计数器（`type` 标签）记录。决策背景见 [ADR-0009](../adr/0009-data-retention-policy.md)。

## 数据库规范
- 所有正式结构变更使用 Flyway。
- 明确外键、唯一约束、非空约束和索引。
- 时间统一使用 UTC。
- 历史清理采用分批处理。
- 删除或修改核心字段前检查数据兼容性。
- 并发相关不变量必须有测试。
