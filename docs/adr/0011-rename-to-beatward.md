# ADR-0011: 项目更名为 Beatward

- 状态：Accepted
- 日期：2026-11-02

## 背景

原名称 PulseOps 与已有产品/项目重名，影响识别与检索。项目尚未公网部署（Phase 5B 未开始），没有生产数据，是统一更名的低成本窗口。

## 决策

- 展示名从 PulseOps 改为 Beatward。
- 代码与运行时标识一并更名：Java 包、Maven 坐标、Spring 应用名、配置前缀、环境变量、会话 Cookie、数据库与角色、Docker 项目/镜像/卷、systemd 单元、部署路径、本地域名与 GitHub 仓库。
- 不保留旧标识兼容层；Phase 1–5A 验收记录、历史评审与已完成阶段的设计文档保留原名。
- GitHub 仓库改名为 `kevinmxt/Beatward`，旧 URL 由 GitHub 301 重定向。

## 备选方案

- **仅改展示名**：代码与产品名长期不一致，后续迁移成本更高。
- **保留配置前缀/环境变量兼容层**：没有存量用户，收益低、复杂度高。
- **保留数据库名与卷名**：没有生产数据，保留旧名会形成长期混合命名。

## 后果

收益：标识统一，避免重名，无历史兼容负担。

代价与迁移影响：

- 旧本地/VM 栈需停止并重建：旧 Compose 项目、卷、数据库、systemd timer、Caddy 内部 CA、hosts 与 Cookie 全部失效。
- 旧备份文件名前缀 `pulseops-*` 不再匹配新流程。
- 旧 GitHub 链接经 301 可用；本地 remote 需更新为新地址。
- 日志/指标 tag 变为 `beatward`；当前未接入外部监控，无断档风险。

一次性迁移步骤见 [`../quality/deployment.md`](../quality/deployment.md#从-pulseops-更名迁移一次性)。

回滚：代码整体 revert；GitHub 仓库可反向改名；已重建的本地数据不可恢复（无生产数据）。
