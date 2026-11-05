# 故障事件、恢复与通知规范

## 核心概念
- `CheckResult`：单次探测结果。
- `Incident`：由连续失败或心跳超时形成的一次持续故障。
- `NotificationDelivery`：针对故障或恢复事件的一次通知发送记录。

三者不能混为一谈。

## HTTP 默认状态规则
- 连续失败达到 3 次后进入故障状态。
- 连续成功达到 2 次后恢复。
- `UNKNOWN` 状态下的首次成功直接判定为 `UP`；恢复阈值只用于 `DOWN` 回到 `UP`。
- 阈值应可配置，并有测试覆盖。
- 发生故障时创建 Incident。
- 故障持续期间只更新当前 Incident，不重复创建。
- 恢复时关闭 Incident 并记录恢复时间。

阈值是 MVP 默认值，不代表永远固定。配置更新时必须定义何时生效。

## Incident 生命周期
- 创建条件：失败计数达到 Failure Threshold，且该 Monitor 当前没有未解决 Incident；不依赖 `UP → DOWN` 的状态迁移，因此上线前已是 `DOWN` 的 Monitor 也能在后续失败达到阈值时创建。
- 禁用 Monitor 时关闭未解决 Incident，但不发送恢复通知；删除 Monitor 时级联删除其 Incident。
- 探测语义配置变更只清零计数，不关闭未解决 Incident。

## Heartbeat 规则
Heartbeat 使用最后一次有效心跳、预期间隔和宽限时间判断超时，不直接复用 HTTP 连续失败计数。

- 超时后置 `DOWN`、创建唯一未解决 Incident，失败类型为 `HEARTBEAT_TIMEOUT`（"心跳超时"），`started_at` 使用截止时间。
- 仅在创建 Incident 时写入一条 `HEARTBEAT_TIMEOUT` CheckResult；重复扫描不重复写入。
- 有效上报立即置 `UP` 并关闭未解决 Incident，生成恢复通知；不使用恢复阈值。
- 禁用监控项静默关闭 Incident，不生成恢复通知；重新启用时置 `UNKNOWN` 并获得完整间隔加宽限窗口。

## 一致性与并发
- 同一监控项最多有一个未解决 Incident，由部分唯一索引和探测写回的行锁保证。
- 使用事务、唯一约束或等效并发控制保证不变量。
- 状态变更和 Incident 更新应保持一致。
- 通知失败不能回滚已确认的故障状态。
- 服务重启后不能无条件重发所有历史通知。

## 通知

通知渠道类型、凭据保护、投递路由、重试与限频的完整规则见 [`notifications.md`](notifications.md)。

Incident 侧约束：

- Incident 创建/关闭与 NotificationDelivery 写入同一事务；发送由独立调度线程完成，不在探测事务内发起网络请求（[ADR-0005](../adr/0005-transactional-outbox-notifications.md)）。
- 事件入队时展开该用户所有启用渠道；没有启用渠道时不生成行。
- 禁用 Monitor 关闭 Incident 时不生成恢复通知。
- 通知失败不能回滚已确认的故障状态。
- 投递状态与最后错误通过 Incident 详情暴露，包含渠道名称。
- 单渠道失败不影响其他渠道；服务重启后 `SENT` 不重发、`PENDING` 继续发送。
- 重试调度日志为 `debug`，终态失败日志为 `warn`，不包含凭据或目标 URL。
- 所有外发请求必须设置超时；不得阻塞探测线程。

## 验收
- 未达到失败阈值时不产生故障 Incident。
- 达到阈值后创建一个 Incident。
- 故障期间不会重复创建 Incident。
- 达到恢复条件后正确关闭事件。
- 通知失败不会破坏状态一致性。
- 重试有限且可观察。
