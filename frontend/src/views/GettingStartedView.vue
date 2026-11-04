<script setup lang="ts">
import { authState } from '../auth/session'
</script>

<template>
  <section>
    <header class="page-header">
      <div>
        <h1>开始使用</h1>
        <p class="muted">欢迎，{{ authState.username }}。按下面的顺序完成初始配置。</p>
      </div>
    </header>

    <ol class="getting-started-steps">
      <li>
        <h2>1. 添加通知渠道</h2>
        <p class="muted">配置 PushPlus、息知或邮箱，确保故障时能收到告警。</p>
      </li>
      <li>
        <h2>2. 发送测试通知</h2>
        <p class="muted">确认渠道配置正确，再开始添加监控。</p>
      </li>
      <li>
        <h2>3. 添加首个监控</h2>
        <p class="muted">监控功能将在账号数据隔离完成后开放，届时可创建 HTTP 或 Heartbeat 监控。</p>
      </li>
    </ol>
  </section>
</template>
