<template>
  <div class="landing">
    <AppHeader />
    <main>
      <section class="hero">
        <div class="hero-inner">
          <div class="hero-copy fade-in-up">
            <span class="hero-badge"><i></i> AI 품질 데이터 기반 B2B 소싱</span>
            <h1>가격을 넘어 품질까지,<br><em>더 정확한 공급사를 찾는 AI 소싱.</em></h1>
            <p>품명·규격·수량·예산·공급지역·납기 조건을 입력하면 8만여 건의 품목 데이터와 납품 품질 이력으로 공급사를 비교하고 추천합니다.</p>
            <div class="hero-actions">
              <router-link to="/login" class="btn btn-primary btn-lg">AI로 공급사 찾아보기</router-link>
              <a href="#flow" class="btn btn-outline btn-lg">AI 매칭 과정 보기</a>
            </div>
            <div class="trust-row"><span>추천 기준</span><b>조건 적합도</b><b>가격 경쟁력</b><b>납기 신뢰도</b><b>납품 품질</b></div>
          </div>
          <div class="hero-visual fade-in">
            <img :src="heroImage" alt="산업 자재와 품질 인증 문서" />
            <div class="match-chip"><b>남강철강</b><i>94점</i></div>
          </div>
        </div>
      </section>

      <section class="problem-section">
        <div class="section-inner split-copy">
          <div><span class="section-kicker">WHY MATERIQ</span><h2>좋은 거래는<br>정확한 조건과 검증된 품질에서 시작됩니다.</h2></div>
          <p>MATERIQ는 규격·가격·납기·공급지역·인증뿐 아니라 거래 이후의 납품 품질까지 하나의 기준으로 연결합니다. 구매기업에는 더 신뢰할 수 있는 공급사를, 공급기업에는 조건이 맞는 새로운 거래 기회를 제공합니다.</p>
        </div>
        <div class="section-inner metric-grid">
          <article v-for="metric in metrics" :key="metric.label"><h3>{{ metric.label }}</h3><strong>{{ metric.weight }}<small>점</small></strong><p>{{ metric.desc }}</p></article>
        </div>
      </section>

      <section id="flow" class="flow-section">
        <div class="section-inner">
          <div class="section-heading"><span class="section-kicker">DATA FEEDBACK LOOP</span><h2>거래와 품질 데이터가 다음 추천을 더 정확하게</h2><p>조건 입력부터 발주, 납품 품질관리까지의 결과가 다음 거래의 신뢰도 높은 판단 근거가 됩니다.</p></div>
          <div class="flow-grid">
            <article v-for="(step,index) in flow" :key="step.title"><span class="flow-no">0{{ index+1 }}</span><div class="flow-icon">{{ step.stage }}</div><h3>{{ step.title }}</h3><p>{{ step.desc }}</p><i v-if="index<flow.length-1 && index!==2">→</i></article>
          </div>
          <div class="feedback-banner"><span>QUALITY DATA</span><p><b>납품수량 · 불량수량 · 불량유형 · 불량률</b>이 공급사 품질지표로 누적되어 다음 추천 순위와 추천 근거에 반영됩니다.</p></div>
        </div>
      </section>

      <section class="role-section">
        <div class="section-inner role-grid">
          <article class="buyer">
            <div class="role-icon" aria-hidden="true"><svg viewBox="0 0 24 24"><path d="M3 5h2l2.1 9.2a2 2 0 0 0 2 1.6h7.9a2 2 0 0 0 1.9-1.4L21 8H7"/><circle cx="10" cy="19" r="1.5"/><circle cx="18" cy="19" r="1.5"/></svg></div>
            <span class="role-label">BUYER</span><h2>구매기업</h2><p>필요한 조건을 입력하면 검증된 공급사를 비교하고, 발주부터 납품 품질관리까지 한 흐름으로 관리할 수 있습니다.</p><a href="/login?register=1&amp;role=STUDENT" @click.prevent="startAs('STUDENT')">구매기업으로 시작 →</a>
          </article>
          <article class="supplier">
            <div class="role-icon" aria-hidden="true"><svg viewBox="0 0 24 24"><path d="M3 21V9l6 3V8l6 4V5h4v16M3 21h18M7 17h2m3 0h2m3 0h2"/></svg></div>
            <span class="role-label">SUPPLIER</span><h2>공급기업</h2><p>공급 조건과 실제 납품 품질을 데이터로 증명하고, 자사 역량과 정확히 맞는 새로운 구매 기회를 만날 수 있습니다.</p><a href="/login?register=1&amp;role=INSTRUCTOR" @click.prevent="startAs('INSTRUCTOR')">공급기업으로 등록 →</a>
          </article>
        </div>
      </section>
    </main>

    <Transition name="toast">
      <div v-if="roleNotice" class="role-toast" role="status" aria-live="polite">
        <i aria-hidden="true">!</i>
        <span>{{ roleNotice }}</span>
        <button type="button" aria-label="안내 메시지 닫기" @click="roleNotice = ''">×</button>
      </div>
    </Transition>

    <AppFooter />
  </div>
</template>

<script setup>
import { onBeforeUnmount, ref } from 'vue'
import { useRouter } from 'vue-router'
import AppHeader from '@/components/AppHeader.vue'
import AppFooter from '@/components/AppFooter.vue'
import { useAuthStore } from '@/store/auth.js'
import heroImage from '@/assets/images/hero/materiq-industrial-hero.webp'
const auth=useAuthStore()
const router=useRouter()
const roleNotice=ref('')
let roleNoticeTimer

function showRoleNotice(message) {
  roleNotice.value=message
  window.clearTimeout(roleNoticeTimer)
  roleNoticeTimer=window.setTimeout(() => { roleNotice.value='' }, 4000)
}

function startAs(role) {
  const destination = role === 'INSTRUCTOR' ? '/mypage' : '/courses'
  if (auth.isAuthenticated) {
    if (auth.user?.role !== role) {
      showRoleNotice(role === 'INSTRUCTOR'
        ? '현재 구매기업 계정으로 로그인되어 있습니다. 공급기업 기능은 공급기업 계정으로 로그인한 후 이용해 주세요.'
        : '현재 공급기업 계정으로 로그인되어 있습니다. 구매기업 기능은 구매기업 계정으로 로그인한 후 이용해 주세요.')
      return
    }
    router.push(destination)
    return
  }
  router.push({ name: 'Login', query: { register: '1', role } })
}

onBeforeUnmount(() => window.clearTimeout(roleNoticeTimer))

const metrics=[
  {weight:30,label:'조건 적합도',desc:'품명과 규격, 공급지역이 구매기업의 필수 조건에 얼마나 정확히 맞는지 확인합니다.'},
  {weight:20,label:'가격 경쟁력',desc:'동일·유사 품목의 등록 단가를 비교해 예산 안에서 선택 가능한 공급사를 찾습니다.'},
  {weight:20,label:'납기 신뢰도',desc:'요청 납기와 공급사의 납품일수, 누적 납기 성과를 함께 비교합니다.'},
  {weight:30,label:'납품 품질',desc:'거래 후 등록된 납품수량과 불량률을 다음 공급사 추천의 핵심 근거로 활용합니다.'}
]
const flow=[
  {stage:'REQUEST',title:'구매 조건 등록',desc:'구매기업이 품명, 규격, 수량, 예산, 공급지역과 희망 납기를 입력합니다.'},
  {stage:'ANALYZE',title:'조건·데이터 분석',desc:'필수조건을 먼저 확인하고 가격, 납기, 인증, 거래 데이터를 같은 기준으로 분석합니다.'},
  {stage:'MATCH',title:'공급사 추천',desc:'조건을 충족한 공급사를 순위화하고 선택 이유와 세부 점수를 함께 제공합니다.'},
  {stage:'ORDER',title:'발주·납품',desc:'추천 근거를 검토해 발주하면 공급기업이 확정된 수량과 배송지에 맞춰 납품합니다.'},
  {stage:'QUALITY',title:'납품 품질관리',desc:'납품수량, 불량수량과 불량유형을 등록해 거래별 품질 결과를 관리합니다.'},
  {stage:'LEARN',title:'추천 고도화',desc:'누적된 품질과 거래 이력을 다음 추천 점수와 공급사 신뢰도에 반영합니다.'}
]
</script>

<style scoped>
.landing{background:#fff;display:flex;min-height:100vh;flex-direction:column}
.landing main{flex:1}
.section-inner{max-width:1180px;margin:0 auto;padding-left:24px;padding-right:24px}
.hero{background:#fff;border-bottom:1px solid var(--color-border);overflow:hidden}
.hero-inner{max-width:1180px;margin:0 auto;padding:78px 24px 74px;display:grid;grid-template-columns:1.02fr .98fr;gap:66px;align-items:center}
.hero-badge{display:inline-flex;align-items:center;gap:8px;padding:7px 11px;border:1px solid #d9edf9;border-radius:999px;background:#f4faff;font-size:10px;font-weight:700;color:var(--color-accent-dark);letter-spacing:.04em}
.hero-badge i{width:7px;height:7px;border-radius:50%;background:var(--color-accent);box-shadow:0 0 0 4px #e9f6fd}
.hero h1{font-size:48px;line-height:1.22;letter-spacing:-.055em;margin:18px 0}
.hero h1 em{font-style:normal;color:#55aee0;background:linear-gradient(transparent 74%,#e6f4fc 0)}
.hero-copy>p{max-width:610px;color:var(--color-text-secondary);font-size:16px;line-height:1.8}
.hero-actions{display:flex;gap:10px;margin:28px 0}
.btn-lg{padding:13px 21px}
.trust-row{display:flex;align-items:center;gap:13px;flex-wrap:wrap;font-size:10px;color:var(--color-text-muted)}
.trust-row span{padding-right:12px;border-right:1px solid var(--color-border)}
.trust-row b{font-weight:600}
.hero-visual{position:relative;border-radius:20px;box-shadow:0 24px 55px rgba(32,44,52,.14)}
.hero-visual img{width:100%;aspect-ratio:1.34;object-fit:cover;border-radius:20px}
.match-chip{position:absolute;right:20px;bottom:-17px;display:flex;align-items:center;gap:9px;padding:12px 18px;border:1px solid rgba(225,229,232,.8);border-radius:999px;background:rgba(255,255,255,.96);box-shadow:var(--shadow-md);font-size:12px}
.match-chip i{font-style:normal;color:var(--color-accent-dark);font-size:16px;font-weight:800}
.problem-section{padding:86px 0;background:#fff}
.split-copy{display:grid;grid-template-columns:1fr 1fr;gap:80px;align-items:end}
.section-kicker{display:block;font-size:10px;font-weight:800;letter-spacing:.16em;color:var(--color-accent-dark);margin-bottom:10px}
.split-copy h2,.section-heading h2{font-size:34px;line-height:1.35;letter-spacing:-.04em}
.split-copy>p,.section-heading p{color:var(--color-text-secondary);font-size:14px;line-height:1.8}
.metric-grid{display:grid;grid-template-columns:repeat(4,1fr);gap:12px;margin-top:38px}
.metric-grid article{padding:24px;border:1px solid var(--color-border);border-radius:14px;background:#fff}
.metric-grid article:nth-child(1){background:#f3f9fd}.metric-grid article:nth-child(2){background:#f8fafb}.metric-grid article:nth-child(3){background:#f1f7f3}.metric-grid article:nth-child(4){background:#fbf5f2}
.metric-grid h3{font-size:18px;line-height:1.35;color:var(--color-text-primary);letter-spacing:-.025em}
.metric-grid strong{display:block;font-size:30px;line-height:1;margin-top:14px}
.metric-grid strong small{margin-left:3px;font-size:12px;font-weight:700;color:var(--color-text-muted)}
.metric-grid p{font-size:11px;color:var(--color-text-muted);margin-top:8px}
.flow-section{padding:86px 0;background:#f6f7f8}
.section-heading{text-align:center}
.section-heading p{margin-top:9px}
.flow-grid{display:grid;grid-template-columns:repeat(3,1fr);gap:14px;margin-top:44px}
.flow-grid article{position:relative;padding:25px 22px;background:#fff;border:1px solid var(--color-border);border-radius:16px;min-height:230px}
.flow-no{font-size:11px;font-weight:700;color:var(--color-text-muted)}
.flow-icon{display:inline-flex;align-items:center;justify-content:center;width:auto;height:35px;padding:0 13px;border-radius:8px;background:var(--color-primary-light);color:var(--color-accent-dark);font-size:12px;font-weight:800;letter-spacing:.1em;margin:25px 0 16px}
.flow-grid h3{font-size:17px;letter-spacing:-.02em}
.flow-grid p{font-size:12px;color:var(--color-text-muted);line-height:1.7;margin-top:8px}
.flow-grid article>i{position:absolute;right:-15px;top:50%;z-index:2;width:20px;height:20px;display:grid;place-items:center;background:#dceff9;border-radius:50%;color:var(--color-accent-dark);font-size:10px;font-style:normal}
.feedback-banner{margin-top:16px;padding:16px 20px;background:#171b20;color:#fff;border-radius:12px;display:flex;align-items:center;gap:20px}
.feedback-banner>span{font-size:9px;letter-spacing:.15em;color:#8ecdef}
.feedback-banner p{font-size:11px;color:#bdc7cd;flex:1}
.feedback-banner b{color:#fff}
.role-section{padding:86px 0}
.role-grid{display:grid;grid-template-columns:1fr 1fr;gap:16px}
.role-grid article{position:relative;padding:38px;border-radius:18px;border:1px solid;overflow:hidden}
.role-grid article.buyer{background:#eef8fe;border-color:#cde8f8}
.role-grid article.supplier{background:#f2f8f3;border-color:#d5e9d9}
.role-icon{position:absolute;right:32px;top:32px;display:grid;place-items:center;width:54px;height:54px;border-radius:16px}
.role-icon svg{width:27px;height:27px;fill:none;stroke:currentColor;stroke-width:1.8;stroke-linecap:round;stroke-linejoin:round}
.buyer .role-icon{background:#dff2fd;color:#398fc0}
.supplier .role-icon{background:#deeee2;color:#4f7d5d}
.role-label{font-size:9px;letter-spacing:.16em;font-weight:800}
.buyer .role-label,.buyer a{color:var(--color-accent-dark)}
.supplier .role-label,.supplier a{color:#4f7d5d}
.role-grid h2{font-size:27px;margin:12px 0}
.role-grid p{font-size:13px;color:var(--color-text-secondary);line-height:1.7;max-width:440px}
.role-grid a{display:inline-block;margin-top:24px;font-size:12px;font-weight:700}
.role-toast{position:fixed;left:50%;bottom:24px;z-index:200;display:flex;align-items:flex-start;gap:11px;width:min(800px,calc(100% - 32px));padding:15px 14px;border:1px solid #cfe7f5;border-radius:12px;background:rgba(255,255,255,.98);box-shadow:0 16px 45px rgba(26,44,56,.18);transform:translateX(-50%);color:var(--color-text-primary);font-size:12px;font-weight:600;line-height:1.55}
.role-toast>i{display:grid;place-items:center;width:24px;height:24px;flex-shrink:0;border-radius:50%;background:var(--color-primary-light);color:var(--color-accent-dark);font-size:12px;font-style:normal;font-weight:900}
.role-toast span{flex:1;padding-top:2px}
.role-toast button{display:grid;place-items:center;width:24px;height:24px;flex-shrink:0;border-radius:50%;background:transparent;color:var(--color-text-muted);font-size:18px;line-height:1}
.role-toast button:hover{background:var(--color-bg-tertiary);color:var(--color-text-primary)}
.toast-enter-active,.toast-leave-active{transition:opacity .2s ease,transform .2s ease}
.toast-enter-from,.toast-leave-to{opacity:0;transform:translate(-50%,12px)}
@media(max-width:900px){.hero-inner{grid-template-columns:1fr;padding-top:60px}
.split-copy{grid-template-columns:1fr;gap:20px}
.metric-grid{grid-template-columns:repeat(2,1fr)}
.flow-grid{grid-template-columns:1fr 1fr}
.flow-grid article>i{display:none}
}
@media(max-width:600px){.hero-inner{padding:48px 16px}
.hero h1{font-size:35px}
.metric-grid,.role-grid,.flow-grid{grid-template-columns:1fr}
.section-inner{padding-left:16px;padding-right:16px}
.problem-section,.flow-section,.role-section{padding:60px 0}
.feedback-banner{align-items:flex-start;flex-direction:column}
.role-toast{bottom:16px}
}
</style>
