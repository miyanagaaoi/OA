<script setup lang="ts">
/**
 * oa-web · 登录页
 * ----------------------------------------------------------------------------
 * 来源：
 *   · `DESIGN.md` › Colors › Brand & Accent（登录页左侧品牌区 logo 44×44px）
 *   · `doc/prd-0.1.md` 6.8「访问方式（REQ-USER-001）/ 登录保持（REQ-USER-002）/
 *     多设备登录（REQ-USER-003）」与「浏览器兼容（REQ-USER-005）」
 *   · `doc/tech-design.md` §6（口令 ≥8 位含字母数字、失败 5 次锁 15 分钟、
 *     会话 Cookie HttpOnly + Secure + SameSite=Lax、记住我 7 天）
 *   · `normify-oa/modules/oa/identity/session/login/**`、`oa/portal/entry/**`
 *
 * 交互要点：
 *   · 账号 + 口令（口令可切换明文）+ 记住我 7 天
 *   · 口令格式前端先校验一遍（≥8 位、字母 + 数字），服务端再校验
 *   · 失败累计 5 次锁定 15 分钟：锁定期间禁用提交并倒计时
 *   · 会话过期（401 跳回）时给出明确提示，而非静默
 *   · 桌面左右分栏、H5 单列；控件 ≥44px 触控目标
 */
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { fetchLockStatus, fetchPasswordPolicy, rememberMe as rememberMeApi } from '@/api/auth'
import { ApiError } from '@/api/http'
import { useUserStore } from '@/stores/user'
import type { LockStatus, PasswordPolicy } from '@/types/api'
import logoUrl from '@/assets/logo-180.png'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const form = reactive({
  account: '',
  password: '',
  rememberMe: true,
})

const passwordVisible = ref(false)
const submitting = ref(false)
const errorText = ref('')
const lock = ref<LockStatus | null>(null)
const policy = ref<PasswordPolicy>({
  minLength: 8,
  requireLetter: true,
  requireDigit: true,
  requireSpecial: false,
  maxAttempts: 5,
  lockMinutes: 15,
})

/** 锁定倒计时（秒），>0 时禁止提交 */
const lockRemaining = ref(0)
let lockTimer: number | null = null

const locked = computed(() => lock.value?.locked === true || lockRemaining.value > 0)

const lockCountdownText = computed(() => {
  if (lockRemaining.value <= 0) return ''
  const m = Math.floor(lockRemaining.value / 60)
  const s = lockRemaining.value % 60
  return `${m} 分 ${s < 10 ? '0' : ''}${s} 秒`
})

/** 剩余可尝试次数（失败 5 次锁 15 分钟） */
const remainingAttempts = computed(() => {
  const used = lock.value?.failedAttempts ?? 0
  return Math.max(0, policy.value.maxAttempts - used)
})

const passwordHint = computed(() => {
  const bits: string[] = [`≥ ${policy.value.minLength} 位`]
  if (policy.value.requireLetter) bits.push('含字母')
  if (policy.value.requireDigit) bits.push('含数字')
  if (policy.value.requireSpecial) bits.push('含符号')
  return bits.join(' · ')
})

const accountError = computed(() => (form.account.trim() ? '' : '请输入账号'))
const passwordError = computed(() => {
  if (!form.password) return '请输入口令'
  const p = policy.value
  if (form.password.length < p.minLength) return `口令不足 ${p.minLength} 位`
  if (p.requireLetter && !/[A-Za-z]/.test(form.password)) return '口令需包含字母'
  if (p.requireDigit && !/\d/.test(form.password)) return '口令需包含数字'
  return ''
})

const canSubmit = computed(
  () => !locked.value && !submitting.value && !accountError.value && !passwordError.value,
)

onMounted(async () => {
  if (userStore.sessionExpired) {
    errorText.value = '会话已失效（或已在其它设备被踢出），请重新登录。'
  }

  const [p, l] = await Promise.all([
    fetchPasswordPolicy().catch(() => policy.value),
    form.account ? fetchLockStatus(form.account).catch(() => null) : Promise.resolve(null),
  ])
  policy.value = p
  if (l) {
    lock.value = l
    lockRemaining.value = l.lockRemainingSeconds
    if (lockRemaining.value > 0) startLockTimer()
  }
})

onBeforeUnmount(() => {
  if (lockTimer !== null) window.clearInterval(lockTimer)
})

function startLockTimer(): void {
  if (lockTimer !== null) window.clearInterval(lockTimer)
  lockTimer = window.setInterval(() => {
    lockRemaining.value = Math.max(0, lockRemaining.value - 1)
    if (lockRemaining.value === 0 && lockTimer !== null) {
      window.clearInterval(lockTimer)
      lockTimer = null
      ElMessage({ type: 'success', message: '锁定已解除，可以重新登录' })
    }
  }, 1000)
}

async function handleSubmit(): Promise<void> {
  errorText.value = ''
  if (!canSubmit.value) return

  submitting.value = true
  try {
    await userStore.login({
      account: form.account.trim(),
      password: form.password,
      rememberMe: form.rememberMe,
      deviceFingerprint: deviceFingerprint(),
    })

    if (form.rememberMe) {
      // 勾选「记住我」后 7 天内免登录，期间访问自动续期（REQ-USER-002）
      await rememberMeApi().catch(() => undefined)
    }

    ElMessage({ type: 'success', message: '登录成功' })
    const redirect = (route.query.redirect as string | undefined) || '/task/pending'
    await router.replace(redirect)
  } catch (error) {
    const apiError = error instanceof ApiError ? error : null
    const message = apiError?.message || '登录失败，请稍后重试'
    errorText.value = message

    // 失败累计 / 锁定信息回读（不信任前端计数）
    try {
      const status = await fetchLockStatus(form.account.trim())
      lock.value = status
      lockRemaining.value = status.lockRemainingSeconds
      if (lockRemaining.value > 0) startLockTimer()
    } catch {
      // 忽略：以服务端返回的 message 为准
    }
  } finally {
    submitting.value = false
  }
}

/**
 * 设备指纹：仅取稳定的浏览器特征做弱标识，不采集隐私。
 * 服务端 `sys_user_session.device_fingerprint` 以此软踢出最早会话（REQ-USER-003）。
 */
function deviceFingerprint(): string {
  const raw = [navigator.userAgent, navigator.language, screen.width, screen.height, new Date().getTimezoneOffset()].join('|')
  let hash = 0
  for (let i = 0; i < raw.length; i += 1) {
    hash = (hash << 5) - hash + raw.charCodeAt(i)
    hash |= 0
  }
  return `web-${Math.abs(hash).toString(36)}`
}
</script>

<template>
  <div class="oa-login">
    <!-- 左侧品牌区（桌面）：H5 隐藏，避免占用首屏 -->
    <section class="brand">
      <img class="brand-logo" :src="logoUrl" alt="集团 OA 审批系统" width="44" height="44" />
      <h1 class="brand-title">集团OA审批系统</h1>
      <p class="brand-sub">
        集团—公司—部门—科室四级组织，事项 / 资金 / 合同 / 印鉴证照四类审批单，
        7 个审批节点全流程留痕。
      </p>
      <ul class="brand-list">
        <li>审批人发起时快照，调岗离职不影响在途单据</li>
        <li>数据域由服务端强制过滤，越权不可见</li>
        <li>签名与轨迹只追加，审计留痕 ≥10 年</li>
      </ul>
      <p class="brand-foot">内部系统 · 请通过公司内网或 VPN 访问</p>
    </section>

    <!-- 右侧表单区 -->
    <section class="panel">
      <div class="panel-inner">
        <header class="panel-head">
          <h2 class="title">登录</h2>
          <p class="sub">使用集团统一账号口令登录</p>
        </header>

        <!-- 会话失效提示（401 跳回时展示，而非静默） -->
        <div v-if="userStore.sessionExpired" class="notice is-warning" role="status">
          会话已失效或在其它设备被踢出，请重新登录。
        </div>

        <!-- 失败锁定提示（失败 5 次锁 15 分钟，REQ-NFR-005） -->
        <div v-if="locked" class="notice is-error" role="alert">
          <b>账号已锁定</b>
          <span>
            连续失败 {{ lock?.failedAttempts ?? policy.maxAttempts }} 次，需等待
            <i class="oa-tnum">{{ lockCountdownText }}</i>
            后重试；也可联系系统管理员解锁。
          </span>
        </div>
        <div v-else-if="lock && lock.failedAttempts > 0" class="notice is-warning" role="alert">
          口令错误，已失败 <i class="oa-tnum">{{ lock.failedAttempts }}</i> 次，
          剩余 <i class="oa-tnum">{{ remainingAttempts }}</i> 次机会。
        </div>

        <form class="form" novalidate @submit.prevent="handleSubmit">
          <label class="field">
            <span class="field-label">账号</span>
            <input
              v-model="form.account"
              class="control"
              type="text"
              name="account"
              autocomplete="username"
              inputmode="text"
              placeholder="请输入账号 / 工号"
              :disabled="locked"
            />
            <span v-if="form.account && accountError" class="field-error">{{ accountError }}</span>
          </label>

          <label class="field">
            <span class="field-label">口令</span>
            <span class="control-wrap">
              <input
                v-model="form.password"
                class="control"
                :type="passwordVisible ? 'text' : 'password'"
                name="password"
                autocomplete="current-password"
                placeholder="请输入口令"
                :disabled="locked"
              />
              <button
                class="reveal"
                type="button"
                :aria-label="passwordVisible ? '隐藏口令' : '显示口令'"
                @click="passwordVisible = !passwordVisible"
              >
                {{ passwordVisible ? '隐藏' : '显示' }}
              </button>
            </span>
            <span class="field-hint">口令要求：{{ passwordHint }}</span>
            <span v-if="form.password && passwordError" class="field-error">{{ passwordError }}</span>
          </label>

          <label class="remember">
            <input v-model="form.rememberMe" type="checkbox" :disabled="locked" />
            <span>记住我（7 天内免登录）</span>
          </label>

          <p v-if="errorText" class="form-error" role="alert">{{ errorText }}</p>

          <button class="submit" type="submit" :disabled="!canSubmit">
            {{ submitting ? '正在登录…' : locked ? '账号已锁定' : '登录' }}
          </button>
        </form>

        <!-- H5 入口约定：扫码进入（REQ-USER-001），此处占位不请求外部资源 -->
        <footer class="panel-foot">
          <p>手机端可直接用浏览器访问本地址，或扫描 OA 首页的移动端二维码。</p>
          <p class="mono">当前会话上限 {{ userStore.clientConfig.maxDevices }} 台设备，超出将踢出最早登录的设备。</p>
        </footer>
      </div>
    </section>
  </div>
</template>

<style scoped>
.oa-login {
  display: grid;
  grid-template-columns: minmax(320px, 1fr) 480px;
  min-height: 100%;
  background: var(--oa-color-canvas);
}

/* ---------------- 品牌区 ---------------- */
.brand {
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: var(--oa-space-md);
  padding: var(--oa-space-section);
  background: var(--oa-color-surface-1);
  border-right: 1px solid var(--oa-color-hairline);
}

.brand-logo {
  width: 44px;
  height: 44px;
  object-fit: contain;
}

.brand-title {
  font: var(--oa-font-display);
  letter-spacing: var(--oa-letter-spacing-display);
  color: var(--oa-color-ink);
}

.brand-sub {
  max-width: 460px;
  font: var(--oa-font-body);
  color: var(--oa-color-ink-muted);
}

.brand-list {
  display: flex;
  flex-direction: column;
  gap: var(--oa-space-xs);
  margin-top: var(--oa-space-xs);
  font: var(--oa-font-body-sm);
  color: var(--oa-color-ink-muted);
}

.brand-list li {
  position: relative;
  padding-left: var(--oa-space-md);
}

.brand-list li::before {
  content: '';
  position: absolute;
  left: 0;
  top: 8px;
  width: 6px;
  height: 6px;
  border-radius: var(--oa-radius-full);
  background: var(--oa-color-primary);
}

.brand-foot {
  margin-top: var(--oa-space-xl);
  font: var(--oa-font-caption);
  color: var(--oa-color-ink-subtle);
}

/* ---------------- 表单区 ---------------- */
.panel {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: var(--oa-space-xl) var(--oa-space-lg);
}

.panel-inner {
  width: 100%;
  max-width: 360px;
}

.panel-head {
  margin-bottom: var(--oa-space-lg);
}

.title {
  font: var(--oa-font-title-page);
  letter-spacing: var(--oa-letter-spacing-title-page);
  color: var(--oa-color-ink);
}

.sub {
  margin-top: var(--oa-space-xxs);
  font: var(--oa-font-body-sm);
  color: var(--oa-color-ink-muted);
}

.notice {
  display: flex;
  flex-direction: column;
  gap: 2px;
  margin-bottom: var(--oa-space-md);
  padding: var(--oa-space-sm);
  border-radius: var(--oa-radius-sm);
  font: var(--oa-font-body-sm);
}

.notice b {
  font-weight: 500;
}

.notice.is-warning {
  background: var(--oa-color-warning-surface);
  color: var(--oa-color-warning);
}

.notice.is-error {
  background: var(--oa-color-error-surface);
  color: var(--oa-color-error);
}

.form {
  display: flex;
  flex-direction: column;
  gap: var(--oa-space-md);
}

.field {
  display: block;
}

.field-label {
  display: block;
  margin-bottom: var(--oa-space-xs);
  font: var(--oa-font-label);
  color: var(--oa-color-ink-muted);
}

.control-wrap {
  display: block;
  position: relative;
}

.control {
  width: 100%;
  height: var(--oa-space-control);
  padding: 0 var(--oa-space-sm);
  border: 1px solid var(--oa-color-hairline);
  border-radius: var(--oa-radius-sm);
  background: var(--oa-color-canvas);
  color: var(--oa-color-ink);
  font: var(--oa-font-body);
}

.control:hover {
  border-color: var(--oa-color-hairline-strong);
}

.control:focus {
  border-color: var(--oa-color-primary);
  outline: none;
  box-shadow: var(--oa-shadow-focus-ring);
}

.control:disabled {
  background: var(--oa-color-canvas-subtle);
  color: var(--oa-color-ink-disabled);
}

.control-wrap .control {
  padding-right: 56px;
}

.reveal {
  position: absolute;
  right: 4px;
  top: 50%;
  transform: translateY(-50%);
  height: 24px;
  padding: 0 var(--oa-space-xs);
  border: 0;
  border-radius: var(--oa-radius-xs);
  background: transparent;
  color: var(--oa-color-primary);
  font: var(--oa-font-caption);
  cursor: pointer;
}

.field-hint {
  display: block;
  margin-top: 4px;
  font: var(--oa-font-caption);
  color: var(--oa-color-ink-subtle);
}

.field-error {
  display: block;
  margin-top: 2px;
  font: var(--oa-font-caption);
  color: var(--oa-color-error);
}

.remember {
  display: inline-flex;
  align-items: center;
  gap: var(--oa-space-xs);
  font: var(--oa-font-body-sm);
  color: var(--oa-color-ink-muted);
  cursor: pointer;
}

.form-error {
  padding: var(--oa-space-xs) var(--oa-space-sm);
  border-radius: var(--oa-radius-sm);
  background: var(--oa-color-error-surface);
  color: var(--oa-color-error);
  font: var(--oa-font-body-sm);
}

.submit {
  height: var(--oa-space-control);
  border: 0;
  border-radius: var(--oa-radius-sm);
  background: var(--oa-color-primary);
  color: var(--oa-color-on-primary);
  font: var(--oa-font-button);
  cursor: pointer;
}

.submit:hover:not(:disabled) {
  background: var(--oa-color-primary-hover);
}

.submit:active:not(:disabled) {
  background: var(--oa-color-primary-active);
}

.submit:disabled {
  background: var(--oa-color-primary-border);
  color: var(--oa-color-ink-muted);
  cursor: not-allowed;
}

.panel-foot {
  margin-top: var(--oa-space-xl);
  padding-top: var(--oa-space-md);
  border-top: 1px solid var(--oa-color-hairline);
  font: var(--oa-font-caption);
  color: var(--oa-color-ink-subtle);
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.panel-foot .mono {
  font: var(--oa-font-mono);
}

/* ---------------- 断点 ---------------- */
@media (max-width: 1024px) {
  .oa-login {
    grid-template-columns: minmax(0, 1fr);
  }

  .brand {
    display: none;
  }
}

@media (max-width: 768px) {
  .panel {
    align-items: flex-start;
    padding: var(--oa-space-xl) var(--oa-space-md) var(--oa-space-xxl);
  }

  /* H5：触控目标 ≥44px（PRD 6.8） */
  .control,
  .submit {
    height: var(--oa-space-control-h5);
  }

  .reveal {
    height: 36px;
  }
}
</style>
