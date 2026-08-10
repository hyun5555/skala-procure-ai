<template>
  <div class="login-page">
    <div class="login-layout">
      <!-- 좌측 브랜딩 -->
      <div class="login-left">
        <div class="brand">
          <span class="brand-logo">P</span>
          <span class="brand-name">PROCURIX</span>
        </div>
        <div class="brand-content">
          <h2>품질 데이터로<br>더 나은 조달을</h2>
          <p>검증된 공급기업과 조건에 맞는 구매기업을 연결합니다.</p>
          <ul class="feature-list">
            <li v-for="f in features" :key="f">
              <span class="dot"></span>{{ f }}
            </li>
          </ul>
        </div>
      </div>

      <!-- 우측 -->
      <div class="login-right">
        <div class="login-box fade-in-up">
          <router-link to="/" class="back-link">← 홈으로</router-link>

          <!-- 로그인 영역 -->
          <div v-if="!showRegister" class="section">
            <h3 class="section-title">로그인</h3>
            <p class="section-desc">Procurix 기업 계정으로 로그인합니다.</p>
            <button class="btn btn-primary btn-full" @click="handleOAuth">로그인</button>
            <div class="demo-divider"><span>로그인 없이 둘러보기</span></div>
            <div class="demo-actions">
              <button class="demo-btn" @click="handleDemo('STUDENT')"><b>구매기업 데모</b><small>업체 매칭 · 발주 · 추천</small></button>
              <button class="demo-btn supplier" @click="handleDemo('INSTRUCTOR')"><b>공급기업 데모</b><small>품목 등록 · 대시보드</small></button>
            </div>
            <div class="switch-link">
              계정이 없으신가요?
              <button class="text-btn" @click="showRegister = true">회원가입</button>
            </div>
          </div>

          <!-- 회원가입 영역 -->
          <div v-else class="section">
            <h3 class="section-title">회원가입</h3>
            <form @submit.prevent="handleRegister" class="form">
              <div class="form-group">
                <label class="form-label">기업명</label>
                <input v-model="registerForm.name" type="text" class="form-input" placeholder="예: 대한정밀" required />
              </div>
              <div class="form-group">
                <label class="form-label">이메일</label>
                <input v-model="registerForm.email" type="email" class="form-input" placeholder="user@example.com" required />
              </div>
              <div class="form-group">
                <label class="form-label">비밀번호</label>
                <input v-model="registerForm.password" type="password" class="form-input" placeholder="8자 이상" required />
              </div>
              <div class="form-group">
                <label class="form-label">역할</label>
                <select v-model="registerForm.role" class="form-input">
                  <option value="STUDENT">구매기업</option>
                  <option value="INSTRUCTOR">공급기업</option>
                </select>
              </div>
              <div v-if="error" class="error-msg">{{ error }}</div>
              <div v-if="success" class="success-msg">{{ success }}</div>
              <button type="submit" class="btn btn-primary btn-full" :disabled="loading">
                <span v-if="loading">가입 중...</span>
                <span v-else>회원가입</span>
              </button>
            </form>
            <div class="switch-link">
              이미 계정이 있으신가요?
              <button class="text-btn" @click="showRegister = false">로그인</button>
            </div>
          </div>

        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useAuthStore } from '@/store/auth.js'
import { authApi } from '@/api/auth.js'
import { useRouter } from 'vue-router'

const auth = useAuthStore()
const router = useRouter()

const showRegister = ref(false)
const loading = ref(false)
const error = ref('')
const success = ref('')

const registerForm = ref({ name: '', email: '', password: '', role: 'STUDENT' })

const features = ['품명·규격·지역 기반 공급업체 필터링', '단가·납기·인증·MAS 종합 추천', '거래 후 품질 데이터 피드백']

function handleOAuth() {
  auth.redirectToLogin()
}

function handleDemo(role) {
  auth.enterDemo(role)
  router.push(role === 'INSTRUCTOR' ? '/mypage' : '/courses')
}

async function handleRegister() {
  error.value = ''
  success.value = ''
  loading.value = true
  try {
    await authApi.register(registerForm.value)
    success.value = '회원가입 완료! 로그인 페이지로 이동합니다.'
    registerForm.value = { name: '', email: '', password: '', role: 'STUDENT' }
    setTimeout(() => {
      showRegister.value = false
      success.value = ''
    }, 2000)
  } catch (e) {
    error.value = e.response?.data?.message || '회원가입에 실패했습니다.'
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-page {
  min-height: 100vh;
  display: flex;
  align-items: stretch;
}
.login-layout {
  display: grid;
  grid-template-columns: 1fr 1fr;
  width: 100%;
  min-height: 100vh;
}
.login-left {
  background: linear-gradient(160deg, #102f2a 0%, #126B5B 55%, #1a806e 100%);
  padding: 48px;
  display: flex;
  flex-direction: column;
  gap: 48px;
}
.brand { display: flex; align-items: center; gap: 10px; }
.brand-logo { width: 40px; height: 40px; border-radius: 10px; display:grid; place-items:center; background:#fff; color:var(--color-primary); font-weight:900; }
.brand-name { font-size: 18px; font-weight: 700; color: #fff; }
.brand-content h2 {
  font-size: 32px; font-weight: 700; color: #fff;
  line-height: 1.35; margin-bottom: 14px;
}
.brand-content p { font-size: 15px; color: rgba(255,255,255,0.75); margin-bottom: 28px; }
.feature-list { list-style: none; display: flex; flex-direction: column; gap: 12px; }
.feature-list li { display: flex; align-items: center; gap: 10px; font-size: 14px; color: rgba(255,255,255,0.85); }
.dot { width: 7px; height: 7px; border-radius: 50%; background: rgba(255,255,255,0.6); flex-shrink: 0; }

.login-right {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 48px;
  background: var(--color-bg-primary);
}
.login-box { width: 100%; max-width: 400px; }
.back-link {
  display: inline-block;
  font-size: 13px;
  color: var(--color-text-secondary);
  margin-bottom: 32px;
  transition: var(--transition);
}
.back-link:hover { color: var(--color-primary); }

.section { display: flex; flex-direction: column; gap: 16px; }
.section-title { font-size: 22px; font-weight: 700; color: var(--color-text-primary); margin-bottom: 4px; }
.section-desc { font-size: 14px; color: var(--color-text-secondary); margin-bottom: 4px; }

.form { display: flex; flex-direction: column; gap: 14px; }
.form-group { display: flex; flex-direction: column; gap: 6px; }
.form-label { font-size: 13px; font-weight: 500; color: var(--color-text-secondary); }
.form-input {
  padding: 10px 14px;
  border: 1.5px solid var(--color-border);
  border-radius: var(--radius-md);
  font-size: 14px;
  font-family: var(--font-sans);
  color: var(--color-text-primary);
  background: var(--color-bg-primary);
  transition: var(--transition);
  outline: none;
}
.form-input:focus { border-color: var(--color-primary); box-shadow: 0 0 0 3px var(--color-primary-light); }
.btn-full { width: 100%; padding: 12px; font-size: 15px; justify-content: center; margin-top: 4px; }
.demo-divider { display:flex; align-items:center; gap:10px; color:var(--color-text-muted); font-size:10px; margin:4px 0; }
.demo-divider::before,.demo-divider::after { content:''; flex:1; height:1px; background:var(--color-border); }
.demo-actions { display:grid; grid-template-columns:1fr 1fr; gap:8px; }
.demo-btn { padding:12px; border:1px solid #bcd9d2; border-radius:10px; background:var(--color-primary-light); color:var(--color-primary-dark); text-align:left; display:flex; flex-direction:column; gap:3px; }
.demo-btn.supplier { background:#fff7ef; border-color:#f0d3b6; color:#8a4a15; }
.demo-btn b { font-size:12px; }
.demo-btn small { font-size:9px; opacity:.75; }

.switch-link {
  text-align: center;
  font-size: 13px;
  color: var(--color-text-secondary);
  margin-top: 4px;
}
.text-btn {
  background: none;
  border: none;
  color: var(--color-primary);
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  padding: 0 2px;
  text-decoration: underline;
}
.error-msg {
  padding: 10px 14px;
  background: #fef2f2;
  border: 1px solid #fecaca;
  border-radius: var(--radius-md);
  font-size: 13px;
  color: #dc2626;
}
.success-msg {
  padding: 10px 14px;
  background: #f0fdf4;
  border: 1px solid #bbf7d0;
  border-radius: var(--radius-md);
  font-size: 13px;
  color: #16a34a;
}
</style>
