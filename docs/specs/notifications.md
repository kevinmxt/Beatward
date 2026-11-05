# 通知渠道规范

本规范定义 Phase 4C 的通知渠道、凭据保护、测试发送、投递路由与限频行为。Incident 生命周期与通知触发事务语义见 [`incidents.md`](incidents.md)。

## 范围与通道

用户级渠道支持：

| 类型 | 用途 | 凭据 | 端点 |
|---|---|---|---|
| `PUSHPLUS` | 微信主通道 | 用户 PushPlus Token | 内置官方端点 |
| `XIZHI` | 微信备用通道 | 用户息知 Key | 内置官方端点 |
| `EMAIL` | 邮件兜底 | 收件邮箱 | 管理员配置的全局 SMTP |

- 每用户最多 **3 个启用渠道**；禁用渠道不计入配额，但启用时重新校验。
- 同一用户同类型同名称唯一。
- 旧的全局 Telegram 配置只作为 `ADMIN` 的 legacy 渠道保留：管理员没有启用用户渠道且 Telegram 已配置时才生成投递行；它不属于用户渠道配额，也不应用用户渠道限频。
- 后续可选 IM 渠道（企业微信、钉钉、飞书）必须先补本规范与迁移，不在当前实现范围。

## 凭据保护

- 渠道凭据以 JSON 加密后存入 `notification_channel.config_ciphertext`，算法为 AES-256-GCM，随机 12 字节 IV。
- 密钥来自 `BEATWARD_CHANNEL_SECRET_KEY`，必须是 Base64 编码的 32 字节值。
- 密钥缺失：没有渠道数据时允许启动；存在渠道数据时拒绝启动。
- 密钥非空但非法（非 Base64 或解码后不是 32 字节）：始终拒绝启动。
- 密钥需与数据库备份分开离线备份；丢失后用户必须重新绑定渠道。
- API、UI、日志、错误响应与通知内容不得出现完整凭据。列表只返回 `maskedConfig`：
  - Token/Key：`****` + 末 4 位；
  - 邮箱：保留首字符与域名，例如 `a***@example.com`。
- 不记录完整邀请码、重置码、渠道凭据或请求 URL。息知官方接口把 Key 放在 URL 路径中，应用不得记录请求 URL，客户端/代理调试日志必须关闭。

## 渠道 API 与 UI

- `GET /api/v1/notification-channels?page&size`：当前用户的分页渠道列表，返回元数据与掩码。
- `POST /api/v1/notification-channels`：创建渠道，请求 `{type, name, enabled?, config}`。
- `PUT /api/v1/notification-channels/{id}`：修改名称、启用状态；`config` 省略或为空表示保留原凭据。
- `DELETE /api/v1/notification-channels/{id}`：删除渠道；已存在的待发送投递行按“渠道不存在”安全跳过。
- `POST /api/v1/notification-channels/{id}/test`：同步发送测试通知；**不创建 Incident**，失败也返回 200 并记录 `lastTestStatus`/`lastTestError`。
- 渠道不存在或不属于当前用户统一 404 `CHANNEL_NOT_FOUND`。
- 启用渠道超过 3 个返回 409 `CHANNEL_LIMIT_EXCEEDED`。
- 测试发送与真实投递共享该用户该渠道的限频额度；超限返回 429 `RATE_LIMITED`。
- UI 路由 `/settings/notifications`，显示名称、类型、掩码、启停状态与最近测试结果，支持创建、编辑、启停、测试与删除。

## 投递路由

- Incident 创建/恢复在事务内写入投递行；发送由独立调度线程完成。
- 同一事件向该用户**所有启用渠道**各写一行 `notification_delivery`：
  - `notification_channel_id` 指向渠道；
  - 唯一键为 `(incident_id, event_type, notification_channel_id)`；
  - 历史 Telegram 行在 `notification_channel_id IS NULL` 且 `channel = 'TELEGRAM'` 时按 `(incident_id, event_type, channel)` 唯一。
- 单渠道失败不影响其他渠道；各渠道独立重试与终态。
- 渠道在入队后被删除或禁用：投递行标记 `FAILED`（“Channel is unavailable”），不阻塞本轮其他投递。
- `SENT` 不重发；`PENDING` 重启后继续；非 429 的 4xx 与不可恢复错误立即终态，网络错误/5xx/429 最多尝试 3 次并分别退避 1 分钟与 5 分钟。
- 通知内容包含 Monitor 名称、故障/恢复时间与失败摘要；配置 `BEATWARD_PUBLIC_BASE_URL` 时附加详情链接。
- 投递状态与最后错误通过 Incident 详情暴露；UI 显示渠道名称、状态、尝试次数与错误摘要。

## 限频

- 每用户每渠道：**10 条/分钟、50 条/小时**。
- 超限的投递直接标记 `FAILED`，记录 WARN，不做无限重试。
- 测试发送共用同一额度；API 返回 429，投递侧标记失败。
- 限频在内存中按 `userId + channelId` 维护滚动窗口；进程重启后清空，属于可接受的软限制。

## 账号与隐私

- 投递前复核渠道归属账号状态；禁用账号不发送。
- 告警内容不得包含完整凭据、敏感请求头或响应正文。
- 第三方通道会保留通知内容（PushPlus 约 30 天），试用用户需知情；不要发送不必要的敏感数据。
- 邮件依赖全局 SMTP 与发信域名 SPF/DKIM；SMTP 未配置时测试发送明确失败，不影响其他渠道。

## 配置

| 环境变量 | 说明 |
|---|---|
| `BEATWARD_CHANNEL_SECRET_KEY` | 渠道凭据 AES-256-GCM 密钥（Base64 32 字节） |
| `BEATWARD_PUBLIC_BASE_URL` | 通知详情链接基地址 |
| `BEATWARD_SMTP_HOST` / `PORT` / `USERNAME` / `PASSWORD` / `FROM` / `STARTTLS` / `SSL` | EMAIL 渠道全局 SMTP |

PushPlus 与息知端点内置；`beatward.notification.endpoints.*` 只用于测试或受控代理，不对用户开放。

## 验收映射

- 渠道加密与密钥缺失启动失败：`ChannelSecretCipherTest`、`ChannelSecretStartupValidatorTest`、`NotificationChannelApiIT`。
- 掩码、3 渠道配额与并发：`NotificationChannelApiIT`。
- 测试发送成功/失败、无 SMTP、限频：`NotificationChannelApiIT`、`EmailNotificationSenderTest`。
- 多渠道展开、单渠道隔离、删除跳过、用户隔离、限频：`MultiChannelNotificationIT`。
- legacy Telegram 回归：`NotificationFlowIT`。
- 前端渠道页、角色导航与错误提示：`NotificationSettingsView.test.ts`、`GettingStartedView.test.ts`、`router/index.test.ts`。
- 真实 PushPlus 测试通知与 Incident 告警：`RealPushPlusNotificationIT`（需要 `BEATWARD_TEST_PUSHPLUS_TOKEN` 且 `BEATWARD_RUN_REAL_NOTIFICATIONS=true`，CI 中跳过）。

## 非目标

- 每 Monitor 独立渠道、通用 webhook、短信/电话、多地域路由。
- 渠道凭据托管或第三方 OAuth。
- 细粒度投递重试策略配置。
