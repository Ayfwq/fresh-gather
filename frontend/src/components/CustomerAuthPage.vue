<script setup>
import { computed, ref, watch } from 'vue'
import { ArrowRight, CircleAlert, Leaf, LoaderCircle, ShieldCheck } from '@lucide/vue'
import { useMarket } from '../store.js'

const props = defineProps({ initialRole: { type: String, default: 'customer' } })
const emit = defineEmits(['authenticated'])
const market = useMarket()
const role = ref(props.initialRole)
const mode = ref('login')
const username = ref('')
const phone = ref('')
const password = ref('')
const confirmPassword = ref('')
const busy = ref(false)
const error = ref('')
const isRegister = computed(() => role.value === 'customer' && mode.value === 'register')

watch(() => props.initialRole, value => {
  role.value = value
  mode.value = 'login'
})

function selectRole(next) {
  role.value = next
  mode.value = 'login'
  username.value = ''
  phone.value = ''
  password.value = ''
  confirmPassword.value = ''
  error.value = ''
}

function switchMode(next) {
  mode.value = next
  error.value = ''
  password.value = ''
  confirmPassword.value = ''
}

async function submit() {
  if (busy.value) return
  error.value = ''
  if (isRegister.value && password.value !== confirmPassword.value) {
    error.value = '两次输入的密码不一致'
    return
  }
  busy.value = true
  try {
    if (market.state.mode !== 'live') await market.setMode('live')
    if (role.value === 'merchant') {
      await market.adminLogin(username.value.trim(), password.value)
    } else if (isRegister.value) {
      const nickname = username.value.trim()
      await market.registerCustomer({ username: nickname, name: nickname, phone: phone.value.trim(), password: password.value })
    } else {
      await market.loginCustomer(username.value.trim(), password.value)
    }
    emit('authenticated', role.value)
  } catch (cause) {
    error.value = cause.message || '操作失败，请稍后重试'
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <main class="customer-auth-page">
    <section class="auth-story" aria-label="鲜集介绍">
      <img class="auth-story__photo" src="/fresh-harvest-hero.png" alt="当季新鲜果蔬" />
      <div class="auth-story__veil" />
      <a class="auth-brand" href="#market" aria-label="鲜集首页">
        <span class="auth-brand__mark"><Leaf :size="22" /></span>
        <span><strong>鲜集</strong><small>FRESH MARKET</small></span>
      </a>
      <div class="auth-story__copy">
        <span class="auth-kicker">FRESH FROM THE FARM</span>
        <h1>把田间的清晨，<br />带回家。</h1>
        <p>当季好鲜，每日挑选。<br />登录鲜集，继续发现今天的好味道。</p>
      </div>
      <span class="auth-story__foot"><ShieldCheck :size="15" /> 新鲜直采 · 冷链送达</span>
    </section>

    <section class="auth-panel" aria-labelledby="customer-auth-title">
      <div class="auth-panel__inner">
        <a class="auth-brand auth-brand--mobile" href="#market" aria-label="鲜集首页">
          <span class="auth-brand__mark"><Leaf :size="22" /></span>
          <span><strong>鲜集</strong><small>FRESH MARKET</small></span>
        </a>
        <div class="auth-role-switch" role="tablist" aria-label="选择登录身份">
          <button type="button" role="tab" :aria-selected="role === 'customer'" :class="{ active: role === 'customer' }" @click="selectRole('customer')">客户登录 / 注册</button>
          <button type="button" role="tab" :aria-selected="role === 'merchant'" :class="{ active: role === 'merchant' }" @click="selectRole('merchant')">商家登录</button>
        </div>
        <span class="auth-kicker">{{ role === 'merchant' ? 'MERCHANT ACCOUNT' : 'CUSTOMER ACCOUNT' }}</span>
        <h2 id="customer-auth-title">{{ role === 'merchant' ? '登录商家工作台' : isRegister ? '创建鲜集客户账号' : '欢迎回到鲜集' }}</h2>
        <p class="auth-intro">
          {{ role === 'merchant'
            ? '使用管理员创建的员工账号和密码登录。'
            : isRegister
              ? '填写昵称、手机号和密码，注册后即可选购与查看订单。'
              : '登录后即可选购鲜品，查看订单与配送进度。' }}
        </p>

        <form class="customer-auth-form" @submit.prevent="submit">
          <label>{{ role === 'merchant' ? '员工昵称' : '昵称' }}
            <input v-model.trim="username" required :minlength="isRegister ? 2 : undefined" maxlength="24" autocomplete="username" :placeholder="role === 'merchant' ? '输入管理员创建的员工昵称' : '请输入昵称'" />
          </label>
          <label v-if="isRegister">手机号
            <input v-model.trim="phone" required type="tel" inputmode="numeric" pattern="1[3-9][0-9]{9}" maxlength="11" autocomplete="tel" placeholder="请输入 11 位手机号" />
          </label>
          <label>密码
            <input v-model="password" required :minlength="isRegister ? 8 : undefined" :maxlength="role === 'merchant' ? 64 : 72" type="password" :autocomplete="isRegister ? 'new-password' : 'current-password'" :placeholder="isRegister ? '至少 8 个字符' : '请输入密码'" />
          </label>
          <label v-if="isRegister">确认密码
            <input v-model="confirmPassword" required type="password" minlength="8" maxlength="72" autocomplete="new-password" placeholder="再次输入密码" />
          </label>
          <p v-if="role === 'merchant'" class="auth-account-note"><ShieldCheck :size="15" /> 商家账号由管理员在员工管理中创建，不开放自主注册。</p>
          <p v-else-if="isRegister" class="auth-account-note"><ShieldCheck :size="15" /> 昵称需为 2–24 位字母、汉字、数字、下划线或连字符。</p>
          <p v-if="error" class="auth-error" role="alert"><CircleAlert :size="16" />{{ error }}</p>
          <button class="auth-submit" type="submit" :disabled="busy">
            <LoaderCircle v-if="busy" class="spin" :size="17" />
            <span>{{ busy ? '正在连接…' : role === 'merchant' ? '登录商家工作台' : isRegister ? '注册并登录' : '客户登录' }}</span>
            <ArrowRight v-if="!busy" :size="17" />
          </button>
        </form>

        <button v-if="role === 'customer'" class="auth-switch" type="button" @click="switchMode(isRegister ? 'login' : 'register')">
          {{ isRegister ? '已有账号？返回客户登录' : '还没有账号？注册客户账号' }} <ArrowRight :size="14" />
        </button>
        <p class="auth-privacy"><ShieldCheck :size="14" /> {{ role === 'merchant' ? '员工账号由门店管理员统一管理' : '账号安全登录，个人信息仅用于订单服务' }}</p>
      </div>
    </section>
  </main>
</template>

<style scoped>
.customer-auth-page{min-height:100dvh;display:grid;grid-template-columns:minmax(330px,1fr) minmax(440px,.92fr);background:#f6f6f0;color:#435140}
.auth-story{position:relative;isolation:isolate;display:flex;min-height:100dvh;flex-direction:column;justify-content:space-between;overflow:hidden;padding:40px clamp(32px,6vw,90px);background:#314e39;color:#fff}
.auth-story__photo,.auth-story__veil{position:absolute;z-index:-1;inset:0;width:100%;height:100%}
.auth-story__photo{object-fit:cover;object-position:center}
.auth-story__veil{background:linear-gradient(90deg,#1f382ddd 0%,#2b493ac7 49%,#29463565 100%),linear-gradient(0deg,#233b34a8,transparent 48%)}
.auth-brand{display:inline-flex;width:max-content;align-items:center;gap:11px;color:inherit;text-decoration:none}
.auth-brand__mark{width:43px;height:43px;display:grid;place-items:center;border:1px solid #ffffff48;border-radius:14px;background:#ffffff1c;color:#f5f4e7}
.auth-brand>span:last-child{display:grid;gap:1px}
.auth-brand strong{font-family:var(--serif);font-size:23px;font-weight:600;letter-spacing:.06em}
.auth-brand small{font-size:8px;letter-spacing:.17em;opacity:.76}
.auth-story__copy{margin:10vh 0 13vh}
.auth-kicker{color:#9aa77f;font-size:9px;font-weight:650;letter-spacing:.2em}
.auth-story .auth-kicker{color:#e9c68f}
.auth-story h1{margin:20px 0 16px;color:#fff;font-family:var(--serif);font-size:clamp(36px,4.5vw,60px);font-weight:500;line-height:1.45;letter-spacing:.04em}
.auth-story__copy p{margin:0;color:#f5f3e6d9;font-size:14px;line-height:2.1;letter-spacing:.04em}
.auth-story__foot{display:flex;align-items:center;gap:8px;color:#f3f1e5cf;font-size:11px;letter-spacing:.05em}
.auth-panel{display:grid;place-items:center;padding:48px 34px;background:radial-gradient(ellipse at 100% 0%,#e8eddf 0,transparent 39%),#fbfbf7}
.auth-panel__inner{width:min(100%,410px)}
.auth-brand--mobile{display:none}
.auth-role-switch{display:grid;grid-template-columns:1fr 1fr;gap:4px;margin-bottom:30px;padding:4px;border:1px solid #e8ebe2;border-radius:9px;background:#f1f3ec}
.auth-role-switch button{height:38px;border:0;border-radius:6px;background:transparent;color:#8b9383;font-size:12px;cursor:pointer}
.auth-role-switch button.active{background:#fffefa;color:#345b40;box-shadow:0 1px 5px #24352414;font-weight:650}
.auth-panel__inner>.auth-kicker{display:block;margin-bottom:13px}
.auth-panel h2{margin:0;color:#3e503f;font-family:var(--serif);font-size:clamp(27px,3vw,34px);font-weight:550;letter-spacing:.035em}
.auth-intro{margin:10px 0 25px;color:#929686;font-size:13px;line-height:1.9}
.customer-auth-form{display:grid;gap:16px}
.customer-auth-form label{display:grid;gap:8px;color:#727c6d;font-size:11px}
.customer-auth-form input{width:100%;height:48px;padding:0 14px;border:1px solid #e4e8dd;border-radius:8px;outline:none;background:#fffefa;color:#435140;font-size:14px;transition:border-color .18s,box-shadow .18s}
.customer-auth-form input:focus{border-color:#88a17b;box-shadow:0 0 0 3px #88a17b25}
.customer-auth-form input::placeholder{color:#b2b6a8}
.auth-submit{width:100%;height:49px;display:flex;align-items:center;justify-content:center;gap:10px;margin-top:4px;border:1px solid #345b40;border-radius:8px;background:#345b40;color:#fff;box-shadow:0 5px 12px #284c3520;font-size:13px;font-weight:650;cursor:pointer;transition:background .18s,transform .18s}
.auth-submit:hover:not(:disabled){transform:translateY(-1px);background:#294d36}
.auth-submit:disabled{cursor:wait;opacity:.72}
.auth-submit svg:last-child{margin-left:4px}
.auth-error,.auth-account-note{display:flex;align-items:flex-start;gap:8px;margin:0;font-size:11px;line-height:1.7}
.auth-error{color:#ac4f40}
.auth-account-note{color:#929686}
.auth-account-note svg{flex:none;color:#829474}
.auth-switch{display:flex;align-items:center;justify-content:center;gap:6px;width:100%;margin-top:17px;padding:9px;border:0;background:none;color:#6b8060;font-size:12px;cursor:pointer}
.auth-privacy{display:flex;align-items:center;justify-content:center;gap:7px;margin:32px 0 0;color:#a3a694;font-size:10px}
.auth-privacy svg{color:#829474}
@media(max-width:760px){.customer-auth-page{grid-template-columns:1fr;background:#fbfbf7}.auth-story{display:none}.auth-panel{min-height:100dvh;padding:28px 24px}.auth-brand--mobile{display:inline-flex;margin-bottom:28px}.auth-role-switch{margin-bottom:24px}.auth-panel__inner>.auth-kicker{margin-bottom:10px}.auth-privacy{margin-top:27px}}
</style>
