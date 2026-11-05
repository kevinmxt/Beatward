<script setup lang="ts">
import { RouterLink, RouterView, useRouter } from 'vue-router'
import { authState, logout } from './auth/session'

const router = useRouter()

async function handleLogout(): Promise<void> {
  try {
    await logout()
  } catch {
    // Even when the request fails, the local session is cleared.
  }
  await router.replace('/login')
}
</script>

<template>
  <div class="app-shell">
    <header v-if="authState.authenticated" class="app-header">
      <RouterLink class="brand" to="/monitors">Beatward</RouterLink>
      <nav class="app-nav">
        <RouterLink to="/monitors">监控项</RouterLink>
        <RouterLink to="/incidents">Incidents</RouterLink>
        <template v-if="authState.role === 'ADMIN'">
          <RouterLink to="/admin/invites">邀请码</RouterLink>
          <RouterLink to="/admin/users">用户管理</RouterLink>
        </template>
      </nav>
      <div class="app-user">
        <span class="inline-label">{{ authState.username }}</span>
        <RouterLink to="/settings">设置</RouterLink>
        <button class="button" type="button" @click="handleLogout">登出</button>
      </div>
    </header>
    <main class="app-main">
      <RouterView />
    </main>
  </div>
</template>
