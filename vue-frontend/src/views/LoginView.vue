<template>
  <div class="auth-page">
    <!-- 좌측 배너 : 화면 전체 높이 -->
    <aside class="auth-banner">
      <div class="banner-top">
        <div class="brand">
          <span class="brand-logo">P</span>
          <span class="brand-name">PROCURIX</span>
        </div>
      </div>

      <div class="banner-body">
        <h2>품질 데이터로<br>더 나은 조달을</h2>
        <p>검증된 공급기업과 조건에 맞는 구매기업을 연결합니다.</p>
        <ul class="feature-list">
          <li v-for="f in features" :key="f">
            <span class="dot"></span>{{ f }}
          </li>
        </ul>
      </div>

      <div class="banner-stats">
        <div><strong>1,200+</strong><span>등록 품목</span></div>
        <div><strong>340+</strong><span>공급기업</span></div>
        <div><strong>17개</strong><span>공급 지역</span></div>
      </div>

      <!-- 장식용 도형. 조달·검증을 추상적으로 표현한다. -->
      <span class="deco deco-a" aria-hidden="true"></span>
      <span class="deco deco-b" aria-hidden="true"></span>
      <span class="deco deco-c" aria-hidden="true"></span>
    </aside>

    <!-- 우측 폼 -->
    <main class="auth-main">
      <div class="auth-inner">
        <router-link to="/" class="back-link">← 홈으로</router-link>

        <header class="auth-head">
          <h1>{{ showRegister ? '회원가입' : '다시 오신 것을 환영합니다' }}</h1>
          <p>{{ showRegister
            ? '구매기업 또는 공급기업으로 가입합니다.'
            : 'PROCURIX 기업 계정으로 로그인하세요.' }}</p>
        </header>

        <!--
          세그먼트 토글. 버튼 두 개를 각각 칠하면 빠르게 누를 때
          사라지는 쪽과 나타나는 쪽의 전환이 겹쳐 잔상으로 보인다.
          알약 하나를 좌우로 옮기는 방식이라 겹칠 대상이 없다.
        -->
        <div class="segment" :class="{ right: showRegister }" role="tablist">
          <span class="segment-thumb" aria-hidden="true"></span>
          <button
            type="button"
            role="tab"
            :aria-selected="!showRegister"
            :class="{ active: !showRegister }"
            @click="switchTo(false)"
          >로그인</button>
          <button
            type="button"
            role="tab"
            :aria-selected="showRegister"
            :class="{ active: showRegister }"
            @click="switchTo(true)"
          >회원가입</button>
        </div>

        <!--
          두 패널을 같은 그리드 칸에 겹쳐 둔다.
          v-if 로 갈아끼우면 회원가입 폼(입력 4개)이 더 높아서 전환할 때마다
          세로 중앙 정렬 기준이 바뀌고, 페이지 높이가 변해 좌측 배너까지 움직인다.
          겹쳐 두면 칸 높이가 항상 큰 쪽으로 고정되어 아무것도 밀리지 않는다.
        -->
        <div class="panel-stack">
        <!-- 로그인 -->
        <div class="panel" :class="{ 'is-hidden': showRegister }" :aria-hidden="showRegister">
          <!--
            인증은 auth-server 가 처리한다. 이 버튼은 OAuth2 인가 요청으로
            이동시키고, 아이디·비밀번호 입력은 인증 서버 화면에서 받는다.
            그 화면은 Spring Security 가 런타임에 생성하는 기본 페이지라
            디자인을 바꿀 수 없다. 그래서 이동한다는 사실을 미리 알린다.
          -->
          <p class="notice">
            로그인을 누르면 <b>인증 서버 화면</b>으로 이동합니다.
            영문 <i>Please sign in</i> 화면이 뜨는 것이 정상이며,
            인증이 끝나면 이 사이트로 돌아옵니다.
          </p>

          <button class="btn btn-primary btn-full" @click="handleOAuth">로그인</button>

          <div class="divider"><span>로그인 없이 둘러보기</span></div>

          <div class="demo-actions">
            <button class="demo-btn" @click="handleDemo('STUDENT')">
              <b>구매기업 데모</b>
              <small>업체 매칭 · 발주 · 추천</small>
            </button>
            <button class="demo-btn supplier" @click="handleDemo('INSTRUCTOR')">
              <b>공급기업 데모</b>
              <small>품목 등록 · 대시보드</small>
            </button>
          </div>
        </div>

        <!-- 회원가입 -->
        <form class="panel" :class="{ 'is-hidden': !showRegister }" :aria-hidden="!showRegister" @submit.prevent="handleRegister">
          <label class="field">
            <span>기업명</span>
            <input v-model.trim="registerForm.name" type="text" placeholder="예: 대한정밀" required />
          </label>

          <label class="field">
            <span>이메일</span>
            <input
              v-model.trim="registerForm.email"
              type="email"
              placeholder="buyer@example.com"
              required
              @blur="checkEmailAvailability"
            />
            <small v-if="emailChecking" class="field-hint">확인 중...</small>
            <small v-else-if="emailTaken === true" class="field-hint is-taken">이미 사용 중인 이메일입니다</small>
            <small v-else-if="emailTaken === false" class="field-hint is-free">사용할 수 있는 이메일입니다</small>
          </label>

          <label class="field">
            <span>비밀번호</span>
            <input v-model="registerForm.password" type="password" placeholder="8자 이상" required />
          </label>

          <label class="field">
            <span>역할</span>
            <select v-model="registerForm.role">
              <option value="STUDENT">구매기업 — 품목을 찾고 발주합니다</option>
              <option value="INSTRUCTOR">공급기업 — 조달 품목을 등록합니다</option>
            </select>
          </label>

          <!-- 메시지 자리를 미리 비워 둔다. 떴다 사라질 때 아래가 밀리지 않게 하려는 것이다. -->
          <div class="msg-slot">
            <p v-if="error" class="msg error">{{ error }}</p>
            <p v-else-if="success" class="msg success">{{ success }}</p>
          </div>

          <button type="submit" class="btn btn-primary btn-full" :disabled="loading">
            <span v-if="loading">가입 중...</span>
            <span v-else>회원가입</span>
          </button>
        </form>
        </div>

        <p class="auth-foot">
          공공조달 등록 데이터를 기준으로 가격·납기·인증·공급지역을 비교하고,
          거래 후 품질 데이터를 다음 추천에 반영합니다.
        </p>
      </div>
    </main>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/store/auth.js'
import { authApi } from '@/api/auth.js'

const auth = useAuthStore()
const router = useRouter()

const showRegister = ref(false)
const loading = ref(false)
const error = ref('')
const success = ref('')

const registerForm = ref({ name: '', email: '', password: '', role: 'STUDENT' })

// null = 아직 확인 안 함, true = 중복, false = 사용 가능
const emailTaken = ref(null)
const emailChecking = ref(false)

const features = [
  '품명·규격·지역 기반 공급업체 필터링',
  '단가·납기·인증·MAS 종합 추천',
  '거래 후 품질 데이터 피드백'
]

function switchTo(register) {
  error.value = ''
  success.value = ''
  emailTaken.value = null
  showRegister.value = register
}

function handleOAuth() {
  auth.redirectToLogin()
}

function handleDemo(role) {
  auth.enterDemo(role)
  router.push(role === 'INSTRUCTOR' ? '/mypage' : '/courses')
}

// 제출 전에 중복을 알려주기 위한 조회다.
// 확인 실패는 가입을 막지 않는다. 최종 판정은 서버의 POST 응답이다.
async function checkEmailAvailability() {
  const email = registerForm.value.email
  if (!email || !email.includes('@')) {
    emailTaken.value = null
    return
  }

  emailChecking.value = true
  try {
    const res = await authApi.checkEmail(email)
    emailTaken.value = res.data?.data?.available === false
  } catch (e) {
    emailTaken.value = null
  } finally {
    emailChecking.value = false
  }
}

async function handleRegister() {
  error.value = ''
  success.value = ''
  loading.value = true
  try {
    await authApi.register(registerForm.value)
    success.value = '회원가입이 완료되었습니다. 로그인 탭에서 인증해 주세요.'
    registerForm.value = { name: '', email: '', password: '', role: 'STUDENT' }
    emailTaken.value = null
  } catch (e) {
    error.value = e.response?.data?.message || '회원가입에 실패했습니다.'
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.auth-page {
  min-height: 100vh;
  display: grid;
  grid-template-columns: 1fr 1fr;
  background: var(--color-bg-primary);
}

/* ── 좌측 배너 : 화면 전체 높이 ───────────────────── */
.auth-banner {
  position: relative;
  overflow: hidden;
  background: linear-gradient(160deg, #102f2a 0%, #126B5B 55%, #1a806e 100%);
  padding: 56px 56px 48px;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  gap: 40px;
}
.banner-top,
.banner-body,
.banner-stats { position: relative; z-index: 1; }

.brand { display: flex; align-items: center; gap: 10px; }
.brand-logo {
  width: 42px; height: 42px; border-radius: 11px;
  display: grid; place-items: center;
  background: #fff; color: #126B5B;
  font-weight: 900; font-size: 18px;
}
.brand-name { font-size: 19px; font-weight: 700; color: #fff; letter-spacing: .02em; }

.banner-body h2 {
  font-size: clamp(30px, 3.2vw, 42px);
  font-weight: 700;
  color: #fff;
  line-height: 1.3;
  letter-spacing: -0.5px;
  margin-bottom: 16px;
}
.banner-body p {
  font-size: 15px;
  color: rgba(255,255,255,0.78);
  margin-bottom: 32px;
  max-width: 34ch;
  line-height: 1.7;
}
.feature-list { list-style: none; display: flex; flex-direction: column; gap: 14px; }
.feature-list li {
  display: flex; align-items: center; gap: 11px;
  font-size: 14px; color: rgba(255,255,255,0.88);
}
.dot {
  width: 7px; height: 7px; border-radius: 50%;
  background: rgba(255,255,255,0.65); flex-shrink: 0;
}

.banner-stats {
  display: flex;
  gap: 40px;
  padding-top: 28px;
  border-top: 1px solid rgba(255,255,255,0.16);
}
.banner-stats div { display: flex; flex-direction: column; gap: 3px; }
.banner-stats strong { font-size: 22px; font-weight: 700; color: #fff; }
.banner-stats span { font-size: 12px; color: rgba(255,255,255,0.6); }

/* 장식 도형 */
.deco { position: absolute; border-radius: 50%; pointer-events: none; }
.deco-a {
  width: 460px; height: 460px;
  right: -170px; top: -120px;
  background: radial-gradient(circle at 35% 35%, rgba(255,255,255,0.16), rgba(255,255,255,0) 68%);
}
.deco-b {
  width: 300px; height: 300px;
  right: -90px; bottom: 60px;
  border: 1px solid rgba(255,255,255,0.14);
  background: transparent;
}
.deco-c {
  width: 170px; height: 170px;
  right: 90px; bottom: 175px;
  border: 1px solid rgba(255,255,255,0.10);
  background: transparent;
}

/* ── 우측 폼 ─────────────────────────────────────── */
.auth-main {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 48px 40px;
}
.auth-inner {
  width: 100%;
  max-width: 400px;
  display: flex;
  flex-direction: column;
  gap: 22px;
}

.back-link { font-size: 13px; color: var(--color-text-secondary); transition: var(--transition); }
.back-link:hover { color: var(--color-primary); }

/* 탭에 따라 문구 길이가 달라도 높이가 흔들리지 않게 고정한다 */
.auth-head { min-height: 66px; }
.auth-head h1 {
  font-size: 27px;
  font-weight: 700;
  color: var(--color-text-primary);
  letter-spacing: -0.4px;
  line-height: 1.3;
  text-wrap: balance;
}
.auth-head p { font-size: 14px; color: var(--color-text-secondary); margin-top: 7px; }

/* 세그먼트 토글 */
.segment {
  position: relative;
  display: grid;
  grid-template-columns: 1fr 1fr;
  padding: 4px;
  background: var(--color-bg-tertiary);
  border-radius: 12px;
}

/* 활성 표시는 이 알약 하나가 전담한다. 버튼 배경은 건드리지 않는다. */
.segment-thumb {
  position: absolute;
  top: 4px;
  left: 4px;
  width: calc(50% - 4px);
  height: calc(100% - 8px);
  border-radius: 9px;
  background: var(--color-bg-primary);
  box-shadow: var(--shadow-sm);
  transition: transform 0.18s cubic-bezier(0.4, 0, 0.2, 1);
}
.segment.right .segment-thumb { transform: translateX(100%); }

.segment button {
  position: relative;
  z-index: 1;
  padding: 10px 12px;
  border: none;
  border-radius: 9px;
  background: transparent;
  color: var(--color-text-secondary);
  font-size: 14px;
  font-weight: 600;
  font-family: var(--font-sans);
  cursor: pointer;
  /* all 이 아니라 color 만. 배경·그림자는 알약이 처리하므로 겹칠 일이 없다. */
  transition: color 0.15s ease;
}
.segment button:hover { color: var(--color-text-primary); }
.segment button.active { color: var(--color-text-primary); }

@media (prefers-reduced-motion: reduce) {
  .segment-thumb { transition: none; }
}

/* 두 패널을 같은 칸에 겹쳐 높이를 큰 쪽으로 고정한다. 전환해도 아무것도 밀리지 않는다. */
.panel-stack { display: grid; }
.panel-stack > .panel { grid-area: 1 / 1; }

.panel { display: flex; flex-direction: column; gap: 14px; }
.panel.is-hidden {
  visibility: hidden;   /* 자리는 차지하되 탭 순서에서 빠진다 */
  pointer-events: none;
}

/*
  자식의 transition 을 끊어야 한다.

  .field input 과 .demo-btn 이 `transition: all 0.2s` 를 쓰는데, all 에는
  visibility 가 포함된다. 부모가 hidden 이 되면 자식은 그것을 상속받지만
  transition 때문에 200ms 동안 visible 을 유지한다 -- 그 사이 두 패널이
  겹쳐 보이는 것이 잔상의 정체다. 세그먼트가 아니라 여기가 원인이었다.
*/
.panel.is-hidden,
.panel.is-hidden * {
  transition: none;
}

.notice {
  font-size: 12px;
  line-height: 1.65;
  color: var(--color-text-secondary);
  background: var(--color-bg-secondary);
  border-left: 3px solid var(--color-primary);
  border-radius: 0 8px 8px 0;
  padding: 11px 13px;
}
.notice b { color: var(--color-text-primary); }
.notice i { font-style: normal; font-family: ui-monospace, Menlo, monospace; }

.field { display: flex; flex-direction: column; gap: 7px; }
.field > span { font-size: 11px; font-weight: 700; color: var(--color-text-secondary); }
.field input,
.field select {
  width: 100%;
  padding: 12px;
  border: 1px solid var(--color-border);
  border-radius: 10px;
  outline: none;
  font-size: 14px;
  font-family: var(--font-sans);
  color: var(--color-text-primary);
  background: var(--color-bg-primary);
  transition: var(--transition);
}
.field input:focus,
.field select:focus {
  border-color: var(--color-primary);
  box-shadow: 0 0 0 3px var(--color-primary-light);
}

.btn-full { width: 100%; justify-content: center; padding: 13px; font-size: 15px; }

.divider {
  display: flex; align-items: center; gap: 10px;
  color: var(--color-text-muted); font-size: 11px;
}
.divider::before,
.divider::after { content: ''; flex: 1; height: 1px; background: var(--color-border); }

.demo-actions { display: grid; grid-template-columns: 1fr 1fr; gap: 8px; }
.demo-btn {
  display: flex; flex-direction: column; gap: 3px;
  padding: 12px;
  border: 1px solid var(--color-border);
  border-radius: 10px;
  background: var(--color-bg-primary);
  cursor: pointer;
  text-align: left;
  transition: var(--transition);
}
.demo-btn:hover { border-color: var(--color-primary); background: var(--color-bg-secondary); }
.demo-btn b { font-size: 12px; color: var(--color-text-primary); }
.demo-btn small { font-size: 10px; color: var(--color-text-muted); }
.demo-btn.supplier b { color: var(--color-secondary); }

/* 메시지 유무와 관계없이 높이를 유지한다 */
.field-hint { font-size: 11px; }
.field-hint.is-taken { color: #b91c1c; }
.field-hint.is-free { color: var(--color-primary); }

.msg-slot { min-height: 38px; display: flex; align-items: center; }
.msg { font-size: 12px; padding: 10px 12px; border-radius: 8px; width: 100%; }
.msg.error { background: #fef2f2; color: #b91c1c; }
.msg.success { background: var(--color-primary-light); color: var(--color-primary-dark); }

.auth-foot {
  font-size: 12px;
  line-height: 1.7;
  color: var(--color-text-muted);
  text-align: center;
  padding-top: 6px;
}

/* ── 반응형 ──────────────────────────────────────── */
@media (max-width: 960px) {
  .auth-page { grid-template-columns: 1fr; }
  .auth-banner {
    padding: 36px 28px;
    gap: 28px;
    min-height: auto;
  }
  .banner-stats { gap: 28px; padding-top: 20px; }
  .deco-b, .deco-c { display: none; }
  .auth-main { padding: 36px 24px 56px; }
}
</style>
