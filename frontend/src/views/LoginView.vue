<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { login } from '../auth/session'
import { loginErrorMessage } from '../lib/authError'
import { safeRedirect } from '../lib/authRedirect'

const route = useRoute()
const router = useRouter()
const username = ref('')
const password = ref('')
const submitting = ref(false)
const errorMessage = ref<string | null>(null)
const expired = computed(() => route.query.expired === '1')
const changed = computed(() => route.query.changed === '1')
const reset = computed(() => route.query.reset === '1')

async function submit(): Promise<void> {
  errorMessage.value = null
  if (!username.value.trim() || !password.value) {
    errorMessage.value = '请输入用户名和密码'
    return
  }
  submitting.value = true
  try {
    await login(username.value.trim(), password.value)
  } catch (cause) {
    errorMessage.value = loginErrorMessage(cause)
    return
  } finally {
    submitting.value = false
  }
  await router.replace(safeRedirect(route.query.redirect))
}
</script>

<template>
  <section class="auth-page">
    <div class="auth-panel">
      <h1>登录 Beatward</h1>
      <p v-if="expired" class="notice">登录状态已过期，请重新登录。</p>
      <p v-if="changed" class="notice">密码已修改，请重新登录。</p>
      <p v-if="reset" class="notice">密码已重置，请使用新密码登录。</p>

      <form class="auth-form" @submit.prevent="submit">
        <div class="form-field">
          <label for="login-username">用户名</label>
          <input
            id="login-username"
            v-model="username"
            type="text"
            autocomplete="username"
            :disabled="submitting"
          />
        </div>
        <div class="form-field">
          <label for="login-password">密码</label>
          <input
            id="login-password"
            v-model="password"
            type="password"
            autocomplete="current-password"
            :disabled="submitting"
          />
        </div>

        <p v-if="errorMessage" class="error-panel" role="alert">{{ errorMessage }}</p>

        <button class="button button-primary" type="submit" :disabled="submitting">
          {{ submitting ? '登录中…' : '登录' }}
        </button>
      </form>

      <p class="auth-links">
        没有账号？<RouterLink to="/register">使用邀请码注册</RouterLink>
      </p>
      <p class="auth-links">
        忘记密码？<RouterLink to="/reset-password">使用重置码重置</RouterLink>
      </p>
    </div>
  </section>
</template>
