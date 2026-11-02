# Phase 4C：邀请制多用户与国内通知 — 设计共识

## 状态与用途

- 本文件是 grilling 设计访谈的产物，作为 `to-spec` 与 `to-tickets` 的输入。
- 长期行为规范以 [`docs/specs/auth.md`](../specs/auth.md)、[`docs/specs/api.md`](../specs/api.md)、[`docs/architecture/security.md`](../architecture/security.md)、[`docs/architecture/data-model.md`](../architecture/data-model.md)、[`docs/quality/deployment.md`](../quality/deployment.md) 为准；本文件保留决策背景、目标用户假设与被否决方案。
- 阶段范围与完成条件以 [`docs/product/roadmap.md`](../product/roadmap.md) 为准。
- 目标用户画像是**待验证假设**，不是已确认市场；Phase 6 用真实试用数据验证。

## 目标与完成条件

Phase 4C 把 Beatward 从“单管理员自用工具”升级为“邀请制托管试用服务”：

- 目标用户通过邀请码自助注册，各自拥有隔离的监控数据与通知渠道。
- 管理员可生成/撤销邀请码、管理用户状态、生成一次性重置码。
- 用户可在微信中收到故障与恢复告警，不要求安装额外 IM。
- 首期不做公开注册、不做计费、不做完整 SaaS 组织体系。

完成条件：

- 不同用户不能相互读取、修改或删除对方数据。
- 邀请码并发注册不超卖；过期/撤销/用尽的邀请码被拒绝。
- 禁用账号无法登录，已有会话失效，且不再产生探测与告警。
- PushPlus、息知、邮件至少各有一条测试通知与真实故障告警证据。
- 配额生效，超限返回明确错误码与 UI 提示。
- 现有单管理员数据迁移到 `ADMIN` 归属，迁移可回滚到备份。

## 现状事实

- 当前是严格单用户：`monitor`、`check_result`、`incident`、`notification_delivery`、`heartbeat_credential` 均无归属字段。
- `app_user` 已有 `username`、`password_hash`、`role`，但 `role` CHECK 只允许 `ADMIN`；没有账号状态、邮箱、最后登录字段。
- 所有管理 API 统一要求 `ADMIN`；没有管理员账号管理 API/UI，没有邀请码或密码重置流程。
- Telegram 配置是全局环境变量，`notification_delivery.channel` 只允许 `TELEGRAM`；没有用户级通知渠道或渠道凭据存储。
- 后台任务全局调度：探测、心跳、通知、清理各自独立调度器；没有按用户过滤或公平调度。
- 会话保存在应用内存，重启后所有用户需要重新登录。
- 测试使用固定 `test-admin` 单账号，没有跨用户隔离、配额或邀请码测试。
- Phase 5A 已完成本地部署与备份演练；真实 VPS、域名与外部监控注册属于 Phase 5B。

## 已确认决策

| 主题 | 决策 |
|---|---|
| 产品形态 | 邀请制托管试用；不开放公开注册、不做计费/订阅 |
| 试用规模 | 先邀请 3 人跑 2 周，修复明显问题后扩到 5 人 |
| 目标用户 | 三类主画像：独立接单/代维开发者、单 VPS 同故障域开发者、纯微信零安装开发者；小团队为次级画像 |
| 邀请方式 | 用户自助注册，必须填写邀请码；管理员生成/撤销邀请码，可设置最大注册数与过期时间 |
| 邀请码格式 | 高熵随机、创建时显示一次、数据库只存 SHA-256 哈希；丢失后撤销重发 |
| 账号模型 | `ADMIN`/`USER`；账号状态 `ACTIVE`/`DISABLED`；不收集邮箱/手机 |
| 注册流程 | 邀请码 + 用户名 + 密码；注册成功自动登录进入引导页 |
| 密码找回 | 管理员生成一次性重置码/链接（24 小时有效）；不依赖邮件 |
| 管理员权限 | 管理邀请码/用户/重置；默认**不**查看其他用户监控数据 |
| 账号禁用 | 立即失效该用户全部会话；调度器跳过其监控；重新启用后恢复 |
| 数据隔离 | `monitor.user_id` 作为归属根；`check_result`/`incident`/`heartbeat_credential`/`notification_delivery` 经外键推导，不逐表加 `user_id` |
| 配额 | 每用户 20 个监控（含心跳）、3 个通知渠道、HTTP 探测间隔下限 60 秒 |
| 保留期限 | 沿用全局 `CheckResult` 30 天、已解决 Incident 90 天；4C 不做用户级保留 |
| 通知渠道 | PushPlus 主、息知备、邮件兜底；企微/钉钉/飞书作为可选 IM 通道后置；Telegram 仅保留给管理员/海外用户 |
| 通知存储 | 用户级 `notification_channel`；凭据 AES-256-GCM 加密，API/UI 掩码，日志不记录 |
| 通知投递 | 沿用 `NotificationDelivery` outbox，增加 `notification_channel_id`；同一事件向用户所有启用渠道发送；at-least-once、有限重试 |
| 通知限频 | 每用户每渠道默认 10 条/分钟、50 条/小时；沿用 Incident 去重 |
| 邮件通道 | 全局 SMTP（阿里云邮件推送或腾讯云 SES）；用户只填邮箱；域名配置 SPF/DKIM；邮件仅作兜底 |
| 后台调度 | 继续全局单实例调度；探测/心跳/通知按账号状态过滤；不需要全量租户化调度 |
| 部署地区 | 首期阿里云香港/新加坡 2C4G + 自购域名，免 ICP；dnshe 免费子域仅演示 |
| 大陆方案 | 若选大陆地域，ICP 备案是 5B 前置硬门槛；必须使用可备案的自购域名 |
| 4D 范围 | 只保留成本基线、多用户备份复验、结构化审计日志、账号处置策略、与 5B 外部监控衔接；其余延后 |

## 目标用户画像

这些画像是**待验证假设**。Phase 6 的招募和验收以画像为准，不以“随便找几个开发者”代替。

### 画像 A：独立接单/代维开发者（“客户站点守护者”）

- **场景**：维护 3–10 个客户站点、小程序后端或 WordPress；客户经常比自己更早发现宕机。
- **现在怎么做**：人工巡检、UptimeRobot 免费档、或在客户服务器上装 Uptime Kuma。
- **痛点**：
  - 不想在每个客户环境装 Docker/数据库，客户也不一定给权限。
  - 不想维护 TLS、升级、备份和监控服务自身。
  - 告警要能到微信；Telegram 不可达。
  - 客户项目之间要隔离，不能共用一个后台账号。
- **为什么不用 Kuma**：Kuma 免费但需要服务器和运维；装在客户服务器上还会和客户站点同故障域。
- **需要 Beatward 提供**：邀请码注册、多个 HTTP/Heartbeat 监控、微信告警、历史记录、简单配额。
- **验证信号**：愿意把至少 2 个真实客户站点迁进来；一次真实故障中通过微信收到告警；愿意为省运维付费。

### 画像 B：单 VPS 独立开发者（“同故障域受害者”）

- **场景**：个人 SaaS、API、博客或 Side Project 跑在一台 VPS；曾把 Kuma 装在同一台 VPS。
- **现在怎么做**：VPS 上自建 Kuma，或 UptimeRobot 免费档 + 手动 cron 监控。
- **痛点**：
  - VPS/网络整体挂掉时，Kuma 跟着挂，发不出告警。
  - UptimeRobot 免费档没有 Heartbeat 或最短 5 分钟；需要 cron/备份/采集任务的被动心跳。
  - 不想为了监控再买一台 VPS 并长期维护。
- **为什么不用 Kuma**：故障域不独立；再买一台 VPS 的成本和运维超过托管月费。
- **需要 Beatward 提供**：外部 HTTP 探测、Heartbeat、60 秒级间隔、微信告警、低月费。
- **验证信号**：接入至少 1 个真实 HTTP 监控 + 1 个真实 cron/备份心跳；能复述“监控器不能和被监控对象同故障域”。

### 画像 C：纯微信/拒绝额外 IM 的开发者（“零安装用户”）

- **场景**：后端/前端/全栈开发者，日常只用微信；不用企业微信、钉钉或飞书；不愿为监控再装 App。
- **现在怎么做**：Telegram 不可达；Server酱/PushPlus 每个服务分别配；或者干脆没有告警。
- **痛点**：配置分散、第三方服务门槛、没有恢复通知、没有历史。
- **为什么不用 Kuma**：Kuma 需要服务器；通知配置对非运维用户偏复杂；多个项目要重复配置。
- **需要 Beatward 提供**：微信扫码/关注即可收告警；测试通知；故障 + 恢复通知；无需加入组织。
- **验证信号**：3 分钟内完成 PushPlus/息知绑定并收到测试消息；真实故障在 1 分钟内到达微信。

### 次级画像 D：小团队（3–5 人）

- **场景**：小团队共用一套监控，需要多个账号、共享可见性、离职交接。
- **痛点**：Kuma 单用户，共用账号不安全；没有邀请/禁用/审计。
- **需要 Beatward 提供**：邀请制多用户、角色、用户级通知渠道、禁用账号。
- **验证信号**：至少 1 个团队邀请第 2 个成员并为不同成员配置通知。

### 明确不做的人群

- **Homelab/自建爱好者**：喜欢折腾 Kuma、已经有服务器和通知配置，会认为托管服务不值得付费。
- **企业客户**：需要 SSO、复杂 RBAC、SLA、多地域探测、短信/电话、合规审计，超出当前范围。
- **价格极度敏感且愿意自运维的用户**：免费 Kuma 已满足需求，不是目标客户。

### 试用验证指标（Phase 6）

- 先邀请 3 人，2 周后视情况扩到 5 人；三类主画像各至少 1 人。
- 5 人中 ≥3 完成“通知测试 + 首个监控”；≥2 接入真实 Heartbeat。
- 14 天后 ≥2 仍活跃（至少每周查看或收到真实告警）。
- 收集 ≥5 条具体反馈，重点记录误报、漏报、告警延迟、通知失败。
- 至少 1 人明确表示试用后愿意继续使用或付费。
- 未达到指标时暂停收费/公开 SaaS 扩展，转为自托管团队版或更窄场景；不得用“功能数量”替代验证。

## 邀请码注册与账号模型

### 数据模型

- `invite_code`：
  - `id`
  - `code_hash`：SHA-256 十六进制摘要，全局唯一
  - `label`：管理员备注
  - `max_uses`：1–100
  - `used_count`：默认 0
  - `expires_at`：默认创建后 7 天，可配置
  - `revoked_at`：可空
  - `created_by`：管理员 `user_id`
  - `created_at`
- `password_reset_token`：
  - `id`、`token_hash`、`user_id`、`expires_at`、`used_at`、`created_by`、`created_at`
  - 单次使用，默认 24 小时过期。
- `app_user` 迁移：
  - `role` CHECK 扩展为 `ADMIN`、`USER`。
  - 新增 `status`（`ACTIVE`/`DISABLED`，默认 `ACTIVE`）。
  - 新增 `disabled_at`、`last_login_at`。
  - 保留 `username` 唯一与密码哈希规则。
- 现有 `monitor` 增加 `user_id NOT NULL`，外键到 `app_user`；现有数据迁移到引导管理员。
- 不逐表增加 `user_id`：`check_result`、`incident`、`heartbeat_credential` 通过 `monitor_id` 归属；`notification_delivery` 通过 `incident_id` 归属。对外查询必须经过归属根过滤。

### 注册流程

1. 管理员在 `/admin/invites` 创建邀请码，设置最大注册数、过期时间、备注；页面显示一次完整邀请码。
2. 用户在 `/register` 输入邀请码、用户名、密码；前端先调用 `/api/v1/auth/session` 获取 CSRF。
3. `POST /api/v1/auth/register` 在单个事务内：
   - 校验邀请码存在、未撤销、未过期、`used_count < max_uses`；
   - 原子占用名额（`UPDATE ... SET used_count = used_count + 1 WHERE ... AND used_count < max_uses`）；
   - 校验用户名唯一与密码规则；
   - 创建 `USER` 账号，状态 `ACTIVE`；
   - 占用与建号同一事务，任何失败回滚名额。
4. 注册成功自动登录并跳转 `/getting-started`；失败返回明确错误码，不泄露不需要的信息。
5. 注册接口限流：按客户端地址与邀请码分别限流；邀请码高熵，仍保留限流防滥用。

### 错误码

| HTTP | code | 场景 |
|---|---|---|
| 400 | `INVITE_INVALID` | 邀请码不存在 |
| 400 | `INVITE_EXPIRED` | 已过期 |
| 400 | `INVITE_REVOKED` | 已撤销 |
| 400 | `INVITE_EXHAUSTED` | 名额用尽 |
| 400 | `USERNAME_TAKEN` | 用户名已存在 |
| 400 | `VALIDATION_ERROR` | 用户名/密码不合法 |
| 429 | `RATE_LIMITED` | 注册/重置尝试超限 |
| 403 | `ACCOUNT_DISABLED` | 账号已禁用 |
| 400 | `RESET_TOKEN_INVALID` | 重置码无效/过期/已使用 |

## 账号状态、角色与会话

- 引导管理员仍由 `BEATWARD_AUTH_BOOTSTRAP_*` 在空表时创建；表非空时忽略并 WARN。
- 注册只能创建 `USER`；管理员由引导或后续人工迁移产生。
- 管理接口分为：
  - 所有已认证 `USER`/`ADMIN` 可访问的业务 API（监控、Incident、通知渠道、自己的账号设置）。
  - 仅 `ADMIN` 可访问的 `/api/v1/admin/**`（邀请码、用户管理、重置码）。
- 管理员默认不能通过业务 API 查看其他用户数据；如需客服排障，后续单独设计只读支持模式并记录审计。
- 账号禁用：
  - 登录返回 403 `ACCOUNT_DISABLED`；
  - 通过现有 `AdminSessionRegistry` 失效该用户全部会话；
  - 探测/心跳调度查询过滤 `app_user.status = ACTIVE`，禁用期间不产生新结果/告警；
  - 重新启用后恢复，不修改 `monitor.enabled`。
- 保护最后一个 `ADMIN`：不能禁用最后一个启用管理员，不能移除自己的管理员角色。
- 密码重置：
  - 管理员为指定用户生成一次性重置码；页面只显示一次；
  - 用户在 `/reset-password` 输入重置码 + 新密码；
  - 成功后更新密码哈希、使该用户全部会话失效、标记 token 已使用。
- 会话仍保存在内存；重启后所有用户重新登录，作为已知限制记录。

## 多用户数据隔离

- `monitor.user_id` 是唯一归属根；所有业务查询通过 `monitor.user_id` 过滤。
- `CheckResult`、`Incident`、`HeartbeatCredential`、`NotificationDelivery` 不重复存 `user_id`，避免双写不一致；通过 `monitor`/`incident` 关联推导。
- 新增索引：
  - `monitor(user_id, enabled)`；
  - 现有 `check_result(monitor_id, started_at DESC)`、`incident(monitor_id, status)` 保持不变；
  - 如通知渠道查询需要，增加 `notification_channel(user_id, enabled)`。
- Repository 层所有用户可见查询必须显式带 `userId` 条件；禁止无归属参数的列表查询。
- 后台任务：
  - 探测扫描选择 `enabled` 且所属用户 `ACTIVE` 的监控；
  - 心跳扫描同样过滤；
  - 通知投递按 Incident → Monitor → `user_id` 解析渠道；账号禁用时跳过；
  - 清理任务保持全局，不按用户拆分。
- 管理员数据访问默认隔离；管理端用户列表只显示账号元数据与监控数量，不显示监控内容。
- 迁移 `V14` 起：增加 `monitor.user_id`、回填到引导管理员、设为 NOT NULL；创建邀请码/重置码/通知渠道表；扩展 `app_user` 角色与状态。
- 测试：
  - 跨用户读取/修改/删除返回 404（不泄露存在性）；
  - 跨用户 Incident/历史/心跳令牌不可见；
  - 禁用用户后调度器不探测、不通知；
  - 管理员访问他人业务数据被拒绝；
  - 迁移后原管理员仍能看到原有监控。

## 通知渠道

### 首期渠道

| 渠道 | 定位 | 用户接入 | 凭据 | 备注 |
|---|---|---|---|---|
| PushPlus | 微信主通道 | 关注公众号，绑定，复制 token | 每用户 token | 免费 200 条/日、5 条/分、相同内容 3 条/时；发送方需实名；第三方留存 30 天 |
| 息知 | 微信备用 | 微信扫码，拿 Key | 每用户 Key | 免费不限量、30 条/分；小独立服务、无 SLA、链接 7 天失效 |
| 邮件 | 兜底 | 填写邮箱 | 邮箱地址 + 全局 SMTP | 阿里云 DM 200 封/日或腾讯 SES 1000 封；QQ/163 可能丢信 |
| Telegram | 管理员/海外 | 现有 Bot | 全局 env | 保留，不作新用户默认 |
| 企微/钉钉/飞书 | 可选 IM | 用户自己的群 webhook | webhook URL + 签名密钥 | 后置；企微需加入组织并有微信插件，不作为默认 |

### 数据模型

- `notification_channel`：
  - `id`
  - `user_id`：归属用户
  - `type`：`PUSHPLUS`、`XIZHI`、`EMAIL`、`TELEGRAM`、`WECOM_WEBHOOK`、`DINGTALK_WEBHOOK`、`FEISHU_WEBHOOK`
  - `name`：用户可读名称
  - `config_ciphertext`：AES-256-GCM 加密后的 JSON（token/key/webhook/email）
  - `enabled`
  - `last_test_at`、`last_test_status`、`last_test_error`
  - `created_at`、`updated_at`
  - 唯一约束：同一用户同类型同名称不重复；每用户最多 3 个启用渠道。
- `notification_delivery`：
  - 增加 `notification_channel_id`（可空，兼容旧 Telegram 行）；
  - 唯一约束从 `(incident_id, event_type, channel)` 调整为 `(incident_id, event_type, notification_channel_id)`；
  - 现有全局 Telegram 投递迁移到管理员渠道或保留 legacy 行，不重复发送。

### 凭据与安全

- 渠道凭据（PushPlus token、息知 Key、webhook URL/签名密钥、邮箱）统一 AES-256-GCM 加密存储。
- 密钥来自 `BEATWARD_CHANNEL_SECRET_KEY`（Base64 32 字节）；缺失且存在渠道数据时拒绝启动；密钥需离线备份，丢失后用户需重新绑定。
- API/UI 返回掩码（如最后 4 位）；日志、错误响应、审计事件不记录完整凭据。
- 内置渠道使用固定官方端点；不接受用户自定义 URL（通用 webhook 后置，且必须复用 SSRF 防护）。
- 邮件由管理员配置全局 SMTP，不使用用户可控 URL。
- 通知内容包含监控名、状态、失败类型、时间、详情链接；不包含密码、Token、完整请求头；URL 查询串按安全规范脱敏。
- 测试通知端点受 CSRF 与限流保护，不创建 Incident。

### 投递与限频

- 沿用事务 outbox：Incident 变更与投递行同事务写入；发送在独立调度线程。
- 同一 `(incident_id, event_type, notification_channel_id)` 唯一；至少一次语义，有限重试。
- 用户所有启用渠道都发送；单渠道失败不回滚 Incident，不阻塞其他渠道。
- 每用户每渠道默认 10 条/分钟、50 条/小时；超出记录 WARN 并标记失败，不做无限重试。
- 渠道删除后，待发送投递行按“渠道不存在即跳过”处理；不抛错终止 tick。
- `NotificationDelivery` 的 UI 显示每个渠道的结果与最后错误摘要。

### 迁移

- 现有 Telegram 全局配置：迁移为引导管理员的 `TELEGRAM` 渠道，或保留 env 作为管理员 legacy 渠道；新用户不再使用全局 Telegram。
- 现有 Incident/NotificationDelivery 行保留；新投递按渠道行生成，避免历史行重复发送。
- 迁移前备份；迁移后验证管理员仍能收到 Telegram（如已配置）。

## 配额与资源控制

- 每用户：
  - 最多 20 个监控（HTTP + HEARTBEAT 合计）；
  - 最多 3 个启用通知渠道；
  - HTTP 探测间隔下限 60 秒（托管 profile）；
  - Heartbeat 间隔沿用现有最小 30 秒。
- 配额在创建/更新时校验；并发创建使用用户行锁或原子计数，测试并发不超限。
- 超限错误码：`QUOTA_EXCEEDED`、`CHANNEL_LIMIT_EXCEEDED`；UI 显示当前用量与上限。
- 容量估算：
  - 60 秒间隔、20 个监控、30 天保留：约 86.4 万条 CheckResult/用户；
  - 5 用户约 432 万条，2 vCPU/4GB/40GB 可承载；
  - 10 秒间隔会使数据量增加 6 倍，因此托管 profile 不允许。
- 全局仍为单实例调度；5 用户规模不需要 Redis/Kafka/分布式队列。

## 管理端与前端

- 新增路由：
  - `/register`：邀请码注册（公开）。
  - `/reset-password`：一次性重置码改密（公开）。
  - `/getting-started`：注册后引导：添加通知渠道 → 测试 → 添加首个监控。
  - `/admin/invites`：邀请码创建/列表/撤销；创建时显示一次完整码。
  - `/admin/users`：用户列表、状态、角色、监控数量、禁用/启用、生成重置码。
  - `/settings/notifications`：通知渠道列表、添加、测试、启用/禁用、删除。
- 现有 `/monitors`、`/incidents`、`/settings` 对 `USER` 开放，但只显示当前用户数据。
- 导航按角色显示管理入口；`USER` 不显示 `/admin/**`。
- 注册成功自动登录；401/403/429/配额错误沿用统一错误信封与 UI 提示。
- 前端不做本地持久化认证状态；沿用内存会话。
- 测试：注册流程、邀请码错误、管理员页面权限、配额提示、通知渠道表单与测试发送、禁用账号登录失败。

## 部署与域名

- 首期 VPS：阿里云香港或新加坡 2C4G / 40GB+，预算约 S$15–30/月；购买前核对带宽、流量、续费价格。
- 域名：自购可管理域名的普通域名，使用 Caddy ACME；**不使用 dnshe 免费子域作为正式服务域名**（无所有权、无 SLA，且不能 ICP 备案）。
- 香港/新加坡不需要 ICP；若选中国大陆地域：
  - ICP 备案是 5B 前置硬门槛，约 1–3 周；
  - 必须使用可备案的自购域名；dnshe 子域不能备案；
  - 收费运营可能涉及经营性 ICP/企业主体，提前评估；个人备案通常只能做非经营性个人网站。
- 新增环境变量：
  - `BEATWARD_CHANNEL_SECRET_KEY`：渠道凭据加密密钥；
  - `BEATWARD_PUBLIC_BASE_URL`：通知中的详情链接；
  - `BEATWARD_SMTP_*`：邮件通道全局配置；
  - `BEATWARD_REGISTRATION_*`：注册/重置限流参数（如需要）。
- 邮件域名配置 SPF/DKIM；SMTP 密码只存 `/etc/beatward/beatward.env`（600），不进入 Git/日志。
- 备份包含渠道凭据密文；`BEATWARD_CHANNEL_SECRET_KEY` 与 `age` 私钥分开离线备份。
- 部署脚本/Compose 资源限制沿用 5A；通知渠道增加的外发请求必须设置连接/响应超时。

## Phase 6 试用验证

- 先 3 人，2 周后视情况扩到 5 人；三类主画像各至少 1 人。
- 招募来源：个人网络、独立开发者群、Java/Spring Boot 社区、客户项目/代维朋友；不做泛广告投放。
- 每人完成：通知测试 + 至少 1 个 HTTP 监控；至少 2 人接入真实 Heartbeat。
- 记录：激活时间、首个监控、通知延迟、误报/漏报、通知失败、与 Uptime Kuma/UptimeRobot 的对比理由。
- 14 天留存 ≥2 人；≥5 条具体反馈；≥1 人愿意继续使用或付费。
- 未达标时：暂停收费/公开 SaaS，优先考虑自托管团队版或更窄场景，不继续堆功能。

## 非目标与延后

- 公开注册、邀请码之外的获客。
- 计费、订阅、支付、发票。
- 邮箱/手机号账号体系、邮件验证、邮件找回密码。
- SSO、组织/团队层级、复杂 RBAC、审计报表。
- 微信公众号原生模板消息/服务号；微信客服（48 小时限制）。
- 企业微信作为默认通道（需加入组织 + 微信插件）。
- 短信、电话、语音告警。
- 多地域探测、CDN 探测、多视角可用性。
- 状态页、图表大屏、自定义仪表盘。
- 每监控独立渠道选择、通知升级策略、值班轮换。
- 通用 webhook、Slack/Discord 等海外 IM（后续按需求）。
- 用户级历史保留策略、自动化数据导出/删除。
- 数据库审计表、应用层全量 API 限流平台。
- 用户数据的管理员客服只读访问。

## 已考虑但未采用

- **企业微信作为首选**：群机器人只支持内部群；外部群收不到机器人消息；用户必须加入你的企微组织并关注微信插件。对第三方托管服务侵入性太强。
- **微信公众号服务号原生推送**：个人不能注册服务号；企业认证 300 元/年 + ICP；首期不值得。
- **PushPlus 作为唯一通道**：免费 200 条/日、5 条/分、同文 3 条/时、需实名、第三方留存；必须多通道。
- **Server酱免费档作为主通道**：免费仅 5 条/日，不够。
- **WxPusher 作为主通道**：主力渠道需要装 App，微信 ClawBot 10 条/24h，不满足零安装高频告警。
- **只做 IM webhook**：钉钉/飞书需要用户注册账号并进群；企微需组织；不满足“有微信就行”。
- **通用 webhook 首期开放**：SSRF、payload 兼容和用户配置成本高，后置。
- **给每张表加 `user_id`**：双写和不一致风险；以 monitor 为归属根即可。
- **公开展开注册**：滥用、配额和运营成本不可控；邀请码足够。
- **4D 全量实现**：审计表、API 限流、自动删除等超出 5 人试用所需。
- **阿里云大陆 + dnshe 免费域名**：免费子域不能 ICP 备案；正式大陆方案必须自购可备案域名。
- **香港/新加坡上使用 dnshe 正式域名**：无所有权/无 SLA，只适合演示。
- **管理员默认查看所有用户数据**：隐私边界不清；需要时再设计只读支持模式。
- **每监控选择通知渠道**：首期用户级默认渠道足够，避免配置复杂化。
- **数据库审计表**：结构化日志 + journald 足以支撑 5 人试用。
- **邮箱验证**：用户邮箱只用于告警兜底，测试发送即可验证。
- **内存会话改为 JDBC 会话**：单实例重启登出可接受，不增加表和清理负担。

## 风险与缓解

- **第三方推送不稳定/限额/封号**：多通道（PushPlus + 息知 + 邮件）、测试发送、限频、失败可见；不承诺单一通道 SLA。
- **邮件进垃圾箱/丢信**：SPF/DKIM、测试发送、仅作兜底；不把邮件作为唯一通道。
- **跨用户隔离遗漏**：Repository 层强制 `userId`、跨用户测试、代码审查清单、迁移后抽样。
- **邀请码并发超卖**：原子占用 + 事务 + 并发测试。
- **禁用账号仍被调度**：调度查询过滤账号状态 + 集成测试；不只依赖登录拦截。
- **渠道密钥丢失**：密钥离线备份；丢失后只能重新绑定；文档明确。
- **管理员误操作**：保护最后一个管理员；邀请码/重置码显示一次；关键操作结构化审计。
- **范围膨胀**：4D 只保留五项；Phase 6 未达指标不扩 SaaS。
- **竞争风险**：Uptime Kuma 是最大替代；只打“独立故障域 + 免运维 + 纯微信 + 小团队”组合，不在功能数量上竞争。
- **香港/新加坡延迟**：5 人试用可接受；大陆探测精度问题记录为后续能力，不在 4C 解决。
- **会话重启失效**：所有用户重新登录；文档明确，不作为缺陷。
- **通知内容隐私**：第三方推送会留存内容；UI/文档告知；不发送敏感 URL 查询串。

## 任务拆分与依赖

每个任务一个 issue 与一个 PR，标签 `ready-for-agent`。

| 编号 | 内容 | 关键验收 | 依赖 |
|---|---|---|---|
| 4C-1 | 邀请码与账号模型：`app_user` 角色/状态迁移、`invite_code`、`password_reset_token`、注册/重置 API、注册限流、引导管理员、并发占用测试 | 邀请码并发不超卖；过期/撤销/用尽拒绝；注册自动登录；重置码单次有效；禁用账号 403 | — |
| 4C-2 | 多用户数据隔离：`monitor.user_id` 迁移、Repository/查询归属过滤、调度器账号状态过滤、现有数据回填、跨用户测试 | 跨用户 404；管理员默认不可见他人数据；禁用用户不探测/不通知；迁移后管理员数据完整 | 4C-1 |
| 4C-3 | 管理员端：邀请码/用户管理 API 与 UI、禁用/启用、重置码、保护最后管理员、角色导航 | 管理员可生成/撤销邀请码、禁用用户并失效会话；`USER` 无法访问 `/admin/**` | 4C-1、4C-2 |
| 4C-4 | 通知渠道：`notification_channel` 表与加密、PushPlus/息知/邮件适配器、测试发送、投递 outbox 渠道维度、限频、UI | 三渠道测试通知可达；真实 Incident 按归属发送；凭据掩码/不落日志；失败重试与投递状态可见 | 4C-1、4C-2 |
| 4C-5 | 用户端 UI 与引导：注册页、重置页、`/getting-started`、通知设置、配额提示、角色导航、前端测试 | 新用户可独立完成注册→绑定通知→首个监控；配额错误可读；`USER` 不显示管理入口 | 4C-1 ~ 4C-4 |
| 4C-6 | 文档与验收：`specs/notifications.md`、ADR-0011/0012、auth/api/ui/data-model/security/deployment 更新、`phase4c-acceptance.md`、全量验证 | 文档齐全；跨用户/邀请码/通知/配额/禁用证据完整；全量验证只跑一次 | 4C-1 ~ 4C-5 |
| 4D-1 | 成本与容量基线：5 用户/100 监控/60s/30 天的 VPS、域名、备份、推送成本表 | 预算数字可复核；容量假设与压测/估算一致 | 4C 完成 |
| 4D-2 | 多用户备份恢复复验：含渠道密文的备份/恢复、密钥离线备份、恢复库启动验证 | 恢复后用户/监控/渠道归属一致；无密钥时启动按规范失败/提示 | 4C 完成 |
| 4D-3 | 结构化审计与账号处置：管理员操作日志字段、禁用/删除数据保留策略、人工删除流程 | 关键操作可追溯；账号处置有文档与演练 | 4C 完成 |

执行顺序：4C-1 → 4C-2 → 4C-3/4C-4 并行 → 4C-5 → 4C-6；4D-1 可在 4C-4 期间准备，4D-2/4D-3 在 4C 完成后。每个 PR 只触发必要 CI 轮次，最终全量验证只做一次。

## 文档更新清单

- 新建 `docs/specs/notifications.md`：渠道类型、凭据、投递、限频、测试、隐私边界。
- 新建 `docs/adr/0011-multi-user-ownership.md`：邀请制多用户、归属根、角色/状态、管理员数据边界。
- 新建 `docs/adr/0012-notification-channels-and-secrets.md`：渠道抽象、推送选型、凭据加密、第三方风险。
- 新建 `docs/quality/phase4c-acceptance.md`、`docs/quality/phase4d-acceptance.md`（阶段收口时）。
- 更新 `docs/specs/auth.md`：邀请注册、账号状态、重置码、管理员角色、禁用会话。
- 更新 `docs/specs/api.md`：注册/重置/管理端/通知渠道端点与错误码。
- 更新 `docs/specs/ui.md`：注册、引导、管理页、通知设置、角色导航、配额提示。
- 更新 `docs/architecture/data-model.md`：`monitor.user_id`、`invite_code`、`password_reset_token`、`notification_channel`、归属推导。
- 更新 `docs/architecture/security.md`：邀请码/重置码、渠道密钥加密、通知内容脱敏、管理员数据边界。
- 更新 `docs/quality/deployment.md`：VPS 地区与 ICP 边界、域名要求、SMTP/SPF/DKIM、渠道密钥环境变量。
- 更新 `docs/index.md`：登记本文件、`specs/notifications.md`、ADR-0011/0012、阶段验收文档。
- 更新 `README.md` 与 `.env.example`：邀请注册、管理员引导、通知渠道、VPS/域名说明。
- 更新 `GLOSSARY.md`：Invite Code、Account Status、Notification Channel、Channel Credential。
- 更新 `docs/product/vision.md`：托管试用目标用户假设（指向本文件）。
- 更新 `docs/product/roadmap.md`：4C/4D 范围、5B 地区/备案、Phase 6 试用指标。

## ADR 草稿要点

### ADR-0011：邀请制多用户与归属模型

- **决策**：`monitor.user_id` 为唯一归属根；其余业务表经外键推导；`ADMIN` 管理账号但不默认访问用户数据；邀请码自助注册；账号状态禁用即暂停调度；会话内存存储。
- **备选**：每表加 `user_id`、管理员全局可见、公开注册、Session JDBC、组织/团队层级。
- **后果**：所有用户查询必须带归属条件；后台任务需过滤禁用账号；管理员客服排障需另设只读模式；重启导致全员重新登录；邀请码是增长控制阀。

### ADR-0012：通知渠道抽象与凭据加密

- **决策**：用户级 `notification_channel`；首期 PushPlus、息知、邮件，Telegram 保留；渠道凭据 AES-256-GCM 加密；固定官方端点；投递 outbox 增加渠道维度；多通道同时发送、单渠道失败不影响其他渠道。
- **备选**：全局 Telegram、企业微信默认、微信公众号服务号、单一 PushPlus、通用 webhook 首期开放。
- **后果**：第三方推送可用性和额度成为产品风险；需要多通道与失败可见；渠道密钥必须离线备份；邮件需域名 SPF/DKIM；通知内容会经第三方留存，需在 UI/文档告知。

## 验收映射

| 路线图要求 | 覆盖任务 | 证据 |
|---|---|---|
| 邀请码注册、管理员生成/撤销/最大注册数 | 4C-1、4C-3 | 注册/邀请码并发/过期/撤销测试与 UI 证据 |
| 多用户数据隔离 | 4C-2 | 跨用户 API/历史/心跳/通知隔离测试；迁移抽样 |
| 禁用账号 | 4C-1、4C-2 | 登录 403、会话失效、调度跳过集成测试 |
| 用户级通知渠道（微信/邮件） | 4C-4 | PushPlus/息知/邮件测试通知；真实 Incident 投递；凭据掩码/日志检查 |
| 配额 | 4C-2、4C-5 | 20 监控/3 渠道/60 秒并发测试与 UI 错误 |
| 管理员默认不访问用户数据 | 4C-2、4C-3 | 管理员访问他人业务数据 404/403 测试 |
| 现有数据迁移 | 4C-2 | 迁移后管理员监控数量/抽样一致；备份可恢复 |
| 成本与容量基线 | 4D-1 | 预算表与容量估算记录 |
| 多用户备份恢复 | 4D-2 | `phase4d-acceptance.md` 恢复演练 |
| 结构化审计/账号处置 | 4D-3 | 日志样例与处置流程文档 |

## 调研依据（2026-11-01）

- 企业微信群机器人/外部群限制：[官方文档](https://developer.work.weixin.qq.com/document/path/91770)、[社区答复](https://developer.work.weixin.qq.com/community/question/detail?content_id=16795894401797824536)
- 企业微信微信插件：[官方帮助](https://open.work.weixin.qq.com/help2/pc/14797)
- 钉钉/飞书机器人：[钉钉](https://open.dingtalk.com/document/dingstart/robot-application-overview)、[飞书](https://open.feishu.cn/document/client-docs/bot-v3/add-custom-bot)
- PushPlus：[额度](https://www.pushplus.plus/use.html)、[会员](https://www.pushplus.plus/vip.html)
- 息知：[官网](https://xz.qqoq.net/)
- Server酱：[FAQ](https://sct.ftqq.com/docs/getting-started/faq/)
- 邮件：[阿里云 DirectMail 限额](https://www.alibabacloud.com/help/zh/direct-mail/product-overview/limits)、[腾讯云 SES](https://cloud.tencent.com/document/product/1288)
- DNSHE：[README](https://github.com/dnshe/DNSHE-FreeDomains/blob/main/README_ZH.md)、[TOS](https://www.dnshe.com/tos.html)
- ICP 与阿里云价格：[阿里云 ICP](https://help.aliyun.com/zh/icp-filing/basic-icp-service/user-guide/overview)、[阿里云轻量](https://www.aliyun.com/product/swas)
- 竞品通知能力：[Uptime Kuma](https://github.com/louislam/uptime-kuma)、[UptimeRobot](https://uptimerobot.com/pricing/)、[Healthchecks.io](https://healthchecks.io/pricing/)

## 下一步

1. 创建 Phase 4C 父 issue 与 4C-1 ~ 4C-6、4D-1 ~ 4D-3 子 issue；父 issue 链接本文件，不复制全文。
2. 先起草 `specs/notifications.md`、ADR-0011、ADR-0012 与 `specs/auth.md` 增量，再进入 4C-1。
3. 4C-1/4C-2 完成后先做内部 3 人预演，再开放邀请码给目标画像用户。
4. 4C-4 完成后记录 PushPlus/息知/邮件的真实投递证据；不把测试通知当作真实告警证据。
5. Phase 6 达标前不启动计费/公开 SaaS。
