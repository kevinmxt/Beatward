# Beatward 路线图

## 当前阶段

Phase 4A 已完成（CI 与验收证据见 [`quality/phase4a-acceptance.md`](../quality/phase4a-acceptance.md)）；Phase 4B 已完成（证据见 [`quality/phase4b-acceptance.md`](../quality/phase4b-acceptance.md)）；Phase 5A 已完成（本地可验证，证据见 [`quality/phase5a-acceptance.md`](../quality/phase5a-acceptance.md)）。下一阶段为 **Phase 4C：邀请制多用户与国内通知**，完成 Phase 4C/4D 后进入 Phase 5B：VPS 上线验收。

Phase 4C 的目标用户画像、试用指标与设计决策见 [`plans/phase4c-multiuser-notifications.md`](../plans/phase4c-multiuser-notifications.md)。目标用户画像是待验证假设，不是已确认市场。

## 阶段顺序

### Phase 0：工程初始化

- [x] 检查现有仓库、开发环境和 Git 状态
- [x] 初始化 Java 21 / Spring Boot / Maven Wrapper
- [x] 配置 PostgreSQL 与 Flyway
- [x] 初始化 Vue 3 + TypeScript + Vite
- [x] 配置本地 Docker Compose
- [x] 建立 README 和基础测试流程

完成条件：新环境按文档可以启动；数据库迁移和基础构建成功；仓库没有真实凭证。

### Phase 1：HTTP 监控核心

- [x] Monitor CRUD
- [x] URL 与配置校验
- [x] 定时探测
- [x] 超时、状态码与错误分类
- [x] CheckResult 持久化
- [x] 基础监控状态查询

完成条件：正常、失败、超时场景可重复测试；禁用监控不再执行；重启后配置仍存在。

### Phase 1.1：可靠性修复门槛

目标：修复影响结果正确性和公网部署安全边界的问题，不改动产品范围，不做大规模重构。

- [x] 探测配置版本与 in-flight 结果竞态修复
- [x] 探测语义相关配置变更后立即重探
- [x] SSRF 特殊用途地址策略明确化并补测试
- [x] DNS 超时资源边界验证并给出结论
- [x] 解析器许可耗尽与解析超时的日志、指标可观测性
- [x] 建立 CI（后端 verify、前端 build）并修复 `backend/mvnw` 执行位
- [x] 更新 Phase 1 验收文档，区分历史测试记录与 CI 证据

完成条件：配置变更竞态测试通过；执行期 SSRF 测试通过；CI 在干净 checkout 中通过。

非目标：认证、Incident、UI、历史数据清理（均属于后续阶段）。

### Phase 2：Incident 与通知

依赖：Phase 1.1 固化的探测结果持久化语义。

- [x] 连续失败阈值
- [x] 连续成功恢复阈值
- [x] Incident 创建、去重和恢复
- [x] Telegram 通知
- [x] 通知状态、有限重试和日志

完成条件：不会为同一持续故障重复创建 Incident；通知故障不阻塞探测；恢复事件正确关闭。完成证据见 [`quality/phase2-acceptance.md`](../quality/phase2-acceptance.md)。

### Phase 3：Heartbeat

- [x] 心跳监控项
- [x] 高强度随机令牌及安全存储
- [x] 心跳上报接口
- [x] 超时扫描
- [x] 故障与恢复通知

完成条件：有效令牌可上报，无效/撤销令牌被拒绝；超时可触发故障；重复扫描不会创建重复事件。完成证据见 [`quality/phase3-acceptance.md`](../quality/phase3-acceptance.md)。

### Phase 4A：最小可用 UI

- [x] 监控项列表、创建、编辑、禁用和删除
- [x] 当前状态、最近检查时间与延迟
- [x] 历史结果分页、活动 Incident 与通知状态
- [x] 心跳配置与上报命令展示

非目标：图表大屏、复杂主题、自定义仪表盘。

完成条件：核心功能可仅通过 UI 完成。完成证据见 [`quality/phase4a-acceptance.md`](../quality/phase4a-acceptance.md)。

### Phase 4B：认证与数据维护（公网前硬门槛）

- [x] 单用户认证与授权，覆盖所有管理 API
- [x] 未认证访问返回 401/403 的集成测试
- [x] 生产 profile 启动检查：公开监听且未启用认证时拒绝启动
- [x] 历史数据保留策略与分批清理任务
- [x] 备份与恢复演练（证据见 [`quality/phase4b-acceptance.md`](../quality/phase4b-acceptance.md)）

完成条件：未授权访问被拒绝；清理任务遵守保留期限；备份可恢复。完成证据见 [`quality/phase4b-acceptance.md`](../quality/phase4b-acceptance.md)。

### Phase 4C：邀请制多用户与国内通知

前置条件：Phase 4B 已完成。定位为**邀请制托管试用**，不开放公开注册，不做计费。

目标：让 3–5 位目标用户通过邀请码注册，各自拥有隔离的监控数据与通知渠道，并在微信中收到故障与恢复告警。

目标用户画像（待验证，详见 [设计共识](../plans/phase4c-multiuser-notifications.md#目标用户画像)）：

- 独立接单/代维开发者：有多个客户站点，不想在每个客户环境维护监控。
- 单 VPS 开发者：监控目标与监控服务同故障域，自建 Kuma 无法在整机故障时告警。
- 纯微信开发者：只用微信，不安装企微/钉钉/飞书，要求零安装告警。
- 次级：3–5 人小团队，需要多账号与共享可见性。

- [x] 邀请码生成、撤销、最大注册数、过期时间；邀请码注册 + 注册限流
- [x] `ADMIN`/`USER` 角色、账号启用/禁用、一次性重置码、禁用即失效会话并暂停调度
- [x] 多用户数据隔离：`monitor.user_id` 为归属根，历史、Incident、心跳、通知经外键推导
- [x] 管理员邀请码/用户管理 API 与 UI；管理员默认不查看其他用户监控数据
- [ ] 用户级通知渠道：PushPlus 主、息知备、邮件兜底；可选 IM 后置；凭据加密、测试发送、限频、投递状态
- [x] 基础配额：每用户 20 个监控（含心跳）、3 个通知渠道、HTTP 探测间隔下限 60 秒
- [ ] 跨用户越权、邀请码并发、禁用账号、通知路由与失败重试测试
- [x] 现有单管理员数据迁移到 `ADMIN` 归属

完成条件：不同用户不能互读/改/删；邀请码并发注册不超卖；禁用账号无法登录且不再产生探测/告警；PushPlus/息知/邮件测试通知与真实告警可达；配额生效。完成证据建议见 [`quality/phase4c-acceptance.md`](../quality/phase4c-acceptance.md)。

非目标：公开注册、计费/订阅、邮件账号体系、SSO/组织/复杂 RBAC、微信公众号服务号原生推送、短信/电话、多地域探测、状态页、每监控多渠道、通用 webhook。

### Phase 4D：上线前收尾（最小）

前置条件：Phase 4C 已完成。

目标：只补齐进入真实 VPS 前必须确认的成本、备份和审计边界，不扩张成完整 SaaS。

- [ ] 上线成本预算与容量基线（5 用户 / 100 监控 / 60 秒 / 30 天，含 VPS、域名、备份、推送）
- [ ] 多用户数据模型下备份与恢复复验；渠道加密密钥离线备份
- [ ] 管理员操作结构化审计日志（邀请码、禁用、重置、渠道变更、登录失败），不建审计表
- [ ] 账号禁用/删除的数据保留与清理策略（首期手工流程）
- [ ] 结构化外部可用性检查与 5B 外部监控衔接（复用 5B，不重复实现）

完成条件：成本与容量有数字；恢复演练包含多用户数据；关键管理操作可追溯；账号处置有文档流程。完成证据建议见 [`quality/phase4d-acceptance.md`](../quality/phase4d-acceptance.md)。

非目标：完整审计报表、按用户历史保留、自动化数据导出/删除、API 级限流平台、计费。

### Phase 5A：部署准备（本地可验证）

前置条件：Phase 4B 已完成；公网认证硬性门槛见 [`quality/deployment.md`](../quality/deployment.md)。

目标：完成部署配置、脚本和文档，并在本地或本地虚拟机验证，尽量缩短拿到 VPS 后的上线时间。

- [x] 生产 Compose 配置（非 root、重启策略、资源限制、healthcheck、宿主仅发布 80/443）
- [x] Caddy 配置与本地 HTTPS 验证（internal CA 或本地域名）
- [x] 日志轮转（应用、Caddy、Docker 日志驱动）
- [x] 数据库备份的定时调度、加密与异地存放
- [x] 升级与回滚文档及脚本（本地 dry-run）
- [x] 外部健康检查端点与告警接收预案（选型与预案见 [`deployment.md`](../quality/deployment.md#独立外部监控)；注册在 5B）
- [x] 防火墙与 SSH 加固脚本（本地虚拟机演练，VPS 上执行属 5B）

完成条件：生产 Compose 可在本地或虚拟机启动并通过健康检查；Caddy 配置校验通过且本地 HTTPS 可访问；备份调度、加密与恢复演练通过；回滚 dry-run 通过。完成证据见 [`quality/phase5a-acceptance.md`](../quality/phase5a-acceptance.md)。

非目标：真实域名证书签发、公网可达性验收、连续运行验收（均属 Phase 5B）。

### Phase 5B：VPS 上线验收

前置条件：Phase 5A、4C、4D 完成；已购置 VPS 并准备域名。首期首选阿里云香港/新加坡 2C4G + 自购域名（免 ICP）；若选中国大陆地域，ICP 备案是硬门槛，且必须使用可备案的自购域名（dnshe 免费子域不可用）。

- [ ] 确认 VPS 地区、域名与备案状态（香港/新加坡免 ICP；大陆需 ICP）
- [ ] 在 VPS 上执行防火墙与 SSH 加固
- [ ] 真实域名 HTTPS 签发与公网访问
- [ ] 接入外部健康检查并验证探测与告警
- [ ] 公网部署验收：重启恢复、备份恢复、端口暴露检查
- [ ] 连续运行 7 天且无未解决的严重故障

完成条件：HTTPS 可访问、数据库不暴露公网、重启可恢复、备份可恢复、连续运行 7 天且无未解决的严重故障；域名与备案状态符合所选地区。

### Phase 6：邀请制试用验证

- [ ] 先邀请 3 位目标用户，2 周后视情况扩到 5 位
- [ ] 三类主画像各至少 1 位：客户项目/代维、单 VPS 同故障域、纯微信零安装
- [ ] 每人完成通知测试 + 至少 1 个监控；至少 2 人接入真实 Heartbeat
- [ ] 记录激活、14 天留存、误报/漏报/告警延迟、通知失败
- [ ] 与 Uptime Kuma/UptimeRobot 对比，记录用户为何不自己部署
- [ ] 收集 ≥5 条具体反馈；至少 1 人愿意试用后继续使用或付费
- [ ] 输出继续/调整/暂停决策；未达指标不得进入收费或公开 SaaS

## 路线图规则

- 按阶段顺序推进；若要调整顺序，记录原因。
- 未满足验收条件不得标记完成。
- 阶段完成必须附 CI 运行链接（干净 checkout），不能只依据本地测试记录。
- 单实例调度约束以 [`architecture/overview.md`](../architecture/overview.md) 为准；公网认证门槛以 [`quality/deployment.md`](../quality/deployment.md) 为准。
- Phase 4C 的目标用户画像与试用指标以 [`plans/phase4c-multiuser-notifications.md`](../plans/phase4c-multiuser-notifications.md) 为准；画像在 Phase 6 前均为待验证假设。
- 不伪造测试、运行或用户反馈证据。
- 当前阶段只实现必要功能，不提前实现未来阶段。
- 每次任务结束后，只更新实际发生变化的状态。
