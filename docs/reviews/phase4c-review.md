# Beatward Phase 4C 代码审阅报告

## 1. 审阅对象与范围

- 审阅对象：用户上传的 `Beatward-master.zip`（项目已由 PulseOps 更名为 Beatward）。
- 审阅重点：邀请码注册、账号状态与会话、多租户归属隔离、配额并发控制、管理员控制台、用户通知渠道、凭据加密、后台探测/心跳/通知调度，以及 Phase 4C 验收证据。
- 方法：源码静态审阅、Flyway 迁移与 Repository 查询核对、测试用例/验收文档映射；未把文档中的历史测试结果当成本次重新执行结果。

## 2. 总体结论

<badge>阶段判断：可收口</badge>

**Phase 4C 的主要目标已在源码层面落地，未发现明确的跨用户数据越权读取/修改路径或可直接利用的高危认证绕过。** 代码采用 `monitor.user_id` 作为业务归属根，用户可见查询使用 `userId` 条件；历史结果、Incident、Heartbeat 凭据与投递记录通过 Monitor/Incident 关联推导归属。管理员默认也不能读取其他用户的监控业务数据，这与设计决策一致。

邀请码使用哈希存储与条件更新消费名额；Monitor 配额和启用通知渠道配额通过锁定用户行串行化并发创建；通知凭据采用 AES-256-GCM 密文存储；禁用账号会使会话失效，并在调度查询和执行路径中检查账号状态。测试覆盖面较完整。

因此，**按当前阶段范围，Phase 4C 可以判定完成，建议进入 Phase 4D**。但有两项运行时边界建议在 Phase 4D 或公网试运行前明确处理，另有一项文档/审计完善项。

## 3. 重点实现核对

### 3.1 邀请码注册与账号状态

- `RegistrationService.register` 在事务中校验邀请码、用户名，并通过 `InviteCodeService.consume` 调用条件更新消费名额；注册失败抛出运行时异常，事务回滚可避免名额被错误消耗。
- `InviteCodeService.requireUsable` 校验撤销、过期与剩余名额；邀请码仅保存摘要，列表响应不返回明文或摘要。
- 注册限流拆分地址键与邀请码摘要键；密码重置码采用摘要存储、有效期和单次消费。
- `AuthSessionManager` 登录时使旧 Session 失效后创建新 Session；`AdminSessionRegistry` 支持按账号使所有本机 Session 失效。
- `AdminUserService` 对最后一个启用管理员做锁保护，避免并发禁用导致系统失去管理员。

### 3.2 数据隔离与配额

- `V15` 为 `monitor.user_id` 增加外键与索引，并将历史 Monitor 回填到最早创建的启用 ADMIN；`V16` 将该列收紧为 NOT NULL，并调整 HTTP 间隔下限。
- `MonitorService` 的用户可见读取、修改、删除走 `findByIdAndUserId...`；列表、历史时长和 Incident 查询均传入当前用户 ID。
- Heartbeat token rotate/revoke 先按 `monitorId + userId` 锁定 Monitor；Heartbeat 上报由令牌摘要定位凭据后再检查账号状态。
- Monitor 创建时锁定 `app_user` 行后统计用户 Monitor 数量；通知渠道启用配额也在用户行锁下计数。该方式适合当前单数据库、单实例规模。
- 管理员用户列表只展示账号元数据和 Monitor 数量，不提供跨用户业务数据读取入口。

### 3.3 通知渠道与凭据保护

- `V17` 创建 `notification_channel`，凭据以 AES-256-GCM 加密；启动校验在存在渠道数据但缺少密钥时拒绝启动。
- 渠道 API 通过 `findByIdAndUserId` 进行归属校验，响应使用掩码；内置 PushPlus/息知使用固定端点，邮件使用全局 SMTP。
- Incident 投递通过 outbox 展开用户启用渠道；单渠道失败不会阻断其他渠道，删除渠道后待投递记录不会继续使用已删除凭据。
- 真实通知验收记录覆盖 PushPlus、息知、邮件测试通知；Incident 告警的真实全链路证据为 PushPlus，息知/邮件复用同一 dispatcher 路由并由自动化测试覆盖。报告不将后两者描述为真实 Incident 投递已验证。

## 4. 问题与建议

### P2-1：通知限频器的键数量不是严格上限

**位置：** `backend/src/main/java/dev/beatward/notification/NotificationRateLimiter.java`

`tryAcquire` 仅在 `windows.size() > MAX_KEYS` 时尝试清理空窗口；清理后仍可能超过上限，随后仍会无条件 `computeIfAbsent` 新键。若大量用户/渠道产生近期活动窗口，清理无法回收这些键，Map 可持续增长。渠道删除后对应的限频键也没有主动清理机制。

**影响：** 这是长期运行下的内存容量风险，不是跨用户数据泄露；当前 3 个启用渠道/用户限制降低了单用户影响，但用户总数和历史渠道 ID 没有在此处形成硬上限。

**建议：**
1. 将键容量变成真正的 admission limit：达到上限且无法回收时拒绝新键或采用有界缓存策略，而不是继续扩容。
2. 清理策略应在 `>= MAX_KEYS` 时触发，并定义仍未过期窗口的处理原则；不要无声驱逐活跃窗口造成限频绕过。
3. 增加容量边界测试：大量活跃键、过期键回收、删除渠道后键回收、满容量时新键行为。

### P2-2：账号禁用与已开始的后台任务存在竞态窗口

**位置：** `ProbeService.persist`、`HeartbeatTimeoutService.checkTimeout`、`NotificationDispatcher.dispatch`

代码在任务开始或持久化前会检查账号状态，调度查询也只选择 ACTIVE 账号；但状态检查与后续写入/外部发送并未与 `AdminUserService.changeStatus` 的用户行锁形成共同的串行化边界。例如，探测在读取到 ACTIVE 后，管理员可能并发禁用账号，探测仍可能继续写入结果/Incident；通知 dispatcher 在检查 ACTIVE 后也可能与禁用操作并发，完成一次已开始的外部发送。

**影响：** 正常情况下，禁用后后续 due 查询会停止新任务；风险主要限于禁用瞬间已经进入执行中的任务。现有验收描述“禁用后不再产生探测/告警”时，需明确是否包含正在执行的任务。

**建议：**
1. 先明确产品语义：若允许已开始的任务完成，应在规格和验收中写明“禁用阻止新任务，已开始任务可能完成一次”。
2. 若要求严格即时停止，应为探测/心跳状态提交设计与账号禁用互斥的锁/版本协议；通知发送需承认外部 HTTP 已发出后无法撤回，并至少在真正发送前再次检查状态。
3. 增加并发测试：探测执行中禁用、心跳超时扫描中禁用、通知发送前禁用，断言与选定语义一致。

### P3-1：管理员审计日志覆盖范围尚未达到 Phase 4D 计划中的完整清单

**位置：** `AdminAuditLog.java`、`NotificationChannelService.java`、登录/注册相关 Controller

当前结构化审计记录覆盖邀请码创建/撤销、账号状态变更、重置码生成；登录失败主要是普通认证 WARN 日志，用户通知渠道创建/修改/启停/删除也未见统一审计事件。Phase 4D 路线图已明确计划补充“渠道变更、登录失败”等事件，因此这不作为 Phase 4C 阻断项。

**建议：** 在 Phase 4D 统一事件字段（actor/userId、event、target、result、traceId、timestamp），避免把凭据、邀请码、重置码或渠道密钥写入日志；同时确认日志轮转和保留策略。

### P3-2：审阅索引仍保留旧项目名

`docs/reviews/README.md` 的历史标题使用 PulseOps，属于历史审阅资料；但建议在索引顶部补充“PulseOps 已更名为 Beatward，历史阶段报告保留原名”的说明，避免新旧项目身份混淆。本次按增量约定不改写历史标题，只在末尾追加 Phase 4C 索引。

## 5. 验收证据与本次验证边界

仓库 `docs/quality/phase4c-acceptance.md` 记录：

- 后端最终 `./mvnw verify`：302 个测试，0 失败，4 跳过（真实 PushPlus/息知/邮件发送需显式 opt-in）。
- 前端 `npm test`：203 个测试通过；`npm run build` 成功。
- 记录了邀请码并发、跨用户访问、配额并发、禁用账号调度、通知多渠道与凭据加密测试。
- 记录了 2026-11-05 三类渠道的真实测试通知；真实 Incident 通知全链路只明确记录 PushPlus。
- 限制：人工浏览器走查未执行；最终 CI 链接记载于 GitHub Issue #123，但当前快照文档未内嵌可独立核验的具体运行 URL。

本次审阅环境：JDK 21 可用；Docker daemon 不可用；Node.js 为 22.16.0，与 `frontend/.nvmrc` 固定的 24.15.0 不一致。尝试通过 `bash backend/mvnw test` 启动 Maven Wrapper，但 Wrapper 下载 Maven 3.9.14 时网络失败。因此，本次没有独立运行 Maven 测试、Testcontainers 集成测试、前端测试/构建或 Docker 验收。ZIP 元数据记录 `backend/mvnw` 为 Unix 100755；当前解压工具未恢复执行位，不能据此判断 Git 仓库执行位有误。

## 6. 阶段判断与后续建议

- **Phase 4C：可收口。** 核心功能与仓库验收记录相互对应，静态审阅未发现明确的跨租户越权路径。
- **进入 Phase 4D：建议。** 将 P2-1 限频器容量边界和 P2-2 禁用竞态语义纳入上线前收尾；P3 审计范围按路线图完成。
- **暂不建议直接公网开放。** 仍需完成 Phase 4D 多用户备份恢复复验、渠道密钥离线备份、容量预算与审计补齐，再进入 Phase 5B 真实 VPS 验收。

本报告是源码快照审阅，不替代目标 VPS 上的安全测试、压力测试、真实备份恢复和公网端到端验证。
