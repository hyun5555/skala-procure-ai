<template>
  <div class="landing">
    <AppHeader />
    <main>
      <section class="hero">
        <div class="hero-inner">
          <div class="hero-copy fade-in-up">
            <span class="hero-badge"><i></i> 품질 데이터 기반 B2B 조달 플랫폼</span>
            <h1>가격만 보던 조달에서,<br><em>품질까지 예측하는 조달</em>로.</h1>
            <p>품명·규격·수량·예산·공급지역·납기 조건을 입력하면 8만여 건의 조달 품목 데이터로 공급업체를 비교하고 추천합니다.</p>
            <div class="hero-actions">
              <router-link to="/login" class="btn btn-primary btn-lg">AI로 업체 찾아보기</router-link>
              <a href="#flow" class="btn btn-outline btn-lg">AI 매칭 과정 보기</a>
            </div>
            <div class="trust-row"><span>추천 기준</span><b>품목 적합도</b><b>등록 단가</b><b>납품일수</b><b>인증·MAS</b></div>
          </div>
          <div class="match-board fade-in">
            <div class="board-top"><span>AI MATCH REPORT</span><small>매칭 예시</small></div>
            <div class="condition-tags"><span>파형강관</span><span>Φ300mm</span><span>전지역</span><span>30일</span></div>
            <div v-for="(supplier,index) in suppliers" :key="supplier.name" class="supplier-row">
              <b class="rank">0{{ index+1 }}</b><div><strong>{{ supplier.name }}</strong><small>{{ supplier.reason }}</small></div>
            </div>
            <div class="board-foot"><span>필수조건 충족 업체 3곳</span><b>조달 등록정보 반영 완료 ✓</b></div>
          </div>
        </div>
      </section>

      <section class="problem-section">
        <div class="section-inner split-copy">
          <div><span class="section-kicker">WHY PROCURIX</span><h2>가격만으로는<br>좋은 공급업체를 고르기 어렵습니다.</h2></div>
          <p>공급업체를 선택할 때는 가격뿐 아니라 제품 규격, 납기, 공급 가능 지역, 인증정보 등 여러 조건을 함께 확인해야 합니다. Procurix는 흩어진 정보를 하나의 기준으로 비교하여 구매 조건에 적합한 공급업체를 찾을 수 있도록 지원합니다.</p>
        </div>
        <div class="section-inner metric-grid">
          <article v-for="metric in metrics" :key="metric.label"><h3>{{ metric.label }}</h3><strong>{{ metric.weight }}<small>점</small></strong><p>{{ metric.desc }}</p></article>
        </div>
      </section>

      <section id="flow" class="flow-section">
        <div class="section-inner">
          <div class="section-heading"><span class="section-kicker">DATA FEEDBACK LOOP</span><h2>거래할수록 더 정확해지는 추천</h2><p>납품 이후의 품질 결과가 다음 조달의 더 나은 판단 근거가 됩니다.</p></div>
          <div class="flow-grid">
            <article v-for="(step,index) in flow" :key="step.title"><span class="flow-no">0{{ index+1 }}</span><div class="flow-icon">{{ step.stage }}</div><h3>{{ step.title }}</h3><p>{{ step.desc }}</p><i v-if="index<flow.length-1">→</i></article>
          </div>
          <div class="feedback-banner"><span>QUALITY DATA</span><p><b>납품수량 · 불량수량 · 불량유형</b>이 공급업체 품질지표로 누적되고 다음 추천점수에 반영됩니다.</p></div>
        </div>
      </section>

      <section class="role-section">
        <div class="section-inner role-grid">
          <article><span class="role-label">BUYER</span><h2>구매기업</h2><p>복잡한 조달 조건을 한 번에 비교하고 추천 이유까지 확인해 일관된 발주 결정을 내립니다.</p><router-link to="/login">구매기업으로 시작 →</router-link></article>
          <article class="supplier"><span class="role-label">SUPPLIER</span><h2>공급기업</h2><p>조달 품목과 납품조건, 실제 품질 성과를 데이터로 증명하고 조건이 맞는 신규 구매기업과 연결됩니다.</p><router-link to="/login">공급기업으로 등록 →</router-link></article>
        </div>
      </section>
    </main>
    <AppFooter />
  </div>
</template>

<script setup>
import AppHeader from '@/components/AppHeader.vue'
import AppFooter from '@/components/AppFooter.vue'
const suppliers=[
  {name:'남강철강 주식회사',reason:'30일 납품 · MAS 등록'},
  {name:'영남산업 주식회사',reason:'전지역 공급 · KS 인증'},
  {name:'주식회사 제철산업',reason:'예산 충족 · 우수제품'}
]
const metrics=[
  {weight:30,label:'규격 적합도',desc:'품명, 세부품명, 규격과 제품 조건이 구매 요청사항에 얼마나 부합하는지 확인합니다.'},
  {weight:25,label:'가격 경쟁력',desc:'동일하거나 유사한 규격의 등록 단가를 비교해 예산에 적합한 제품을 찾습니다.'},
  {weight:20,label:'납기 적합도',desc:'구매기업이 요청한 납기와 공급기업의 등록 납품일수를 비교합니다.'},
  {weight:25,label:'공급 신뢰도',desc:'공급 가능 지역과 인증, 우수제품 및 MAS 등록정보를 종합해 확인합니다.'}
]
const flow=[
  {stage:'REGISTER',title:'품목 등록',desc:'공급기업이 품명, 규격, 공급 단가, 납품 가능 지역과 소요일을 등록합니다.'},
  {stage:'ANALYZE',title:'조건 분석',desc:'구매기업의 수량, 예산, 납기와 공급지역을 기준으로 거래 가능한 후보를 선별합니다.'},
  {stage:'MATCH',title:'추천 순위',desc:'품목 적합성, 가격, 납기, 인증과 조달 등록정보를 종합해 비교 순위를 제공합니다.'},
  {stage:'ORDER',title:'발주·납품',desc:'구매 담당자가 추천 근거를 검토해 발주하고, 공급기업이 계약 조건에 맞춰 납품합니다.'},
  {stage:'QUALITY',title:'품질 등록',desc:'납품수량과 불량수량으로 불량률을 계산하고, 검사 결과를 다음 거래의 품질지표로 반영합니다.'}
]
</script>

<style scoped>
.landing{background:#fff;display:flex;min-height:100vh;flex-direction:column}
.landing main{flex:1}
.section-inner{max-width:1180px;margin:0 auto;padding-left:24px;padding-right:24px}
.hero{background:linear-gradient(120deg,#f2f8f6 0%,#fff 65%);border-bottom:1px solid var(--color-border);overflow:hidden}
.hero-inner{max-width:1180px;margin:0 auto;padding:88px 24px 84px;display:grid;grid-template-columns:1.08fr .92fr;gap:70px;align-items:center}
.hero-badge{display:inline-flex;align-items:center;gap:7px;font-size:11px;font-weight:700;color:var(--color-primary);letter-spacing:.04em}
.hero-badge i{width:7px;height:7px;border-radius:50%;background:var(--color-secondary);box-shadow:0 0 0 4px #f9e9d9}
.hero h1{font-size:48px;line-height:1.22;letter-spacing:-.055em;margin:18px 0}
.hero h1 em{font-style:normal;color:var(--color-primary)}
.hero-copy>p{max-width:610px;color:var(--color-text-secondary);font-size:16px;line-height:1.8}
.hero-actions{display:flex;gap:10px;margin:28px 0}
.btn-lg{padding:13px 21px}
.trust-row{display:flex;align-items:center;gap:13px;flex-wrap:wrap;font-size:10px;color:var(--color-text-muted)}
.trust-row span{padding-right:12px;border-right:1px solid var(--color-border)}
.trust-row b{font-weight:600}
.match-board{background:#173C37;color:#fff;border-radius:20px;padding:24px;box-shadow:0 24px 60px rgba(10,60,52,.22);position:relative}
.match-board:before{content:'';position:absolute;inset:10px;border:1px solid rgba(255,255,255,.08);border-radius:14px;pointer-events:none}
.board-top,.board-foot{display:flex;justify-content:space-between;align-items:center}
.board-top span{font-size:11px;font-weight:800;letter-spacing:.16em}
.board-top small{font-size:9px;color:#8dd7c8}
.condition-tags{display:flex;gap:6px;flex-wrap:wrap;margin:20px 0 15px}
.condition-tags span{font-size:9px;border:1px solid rgba(255,255,255,.16);border-radius:20px;padding:5px 9px;color:#cfe3df}
.supplier-row{position:relative;display:grid;grid-template-columns:32px 1fr;gap:10px;align-items:center;padding:14px 0;border-top:1px solid rgba(255,255,255,.1)}
.supplier-row .rank{font-size:10px;color:#77aa9f}
.supplier-row div{display:flex;flex-direction:column;gap:3px}
.supplier-row strong{font-size:13px}
.supplier-row small{font-size:9px;color:#9eb7b2}
.board-foot{margin-top:12px;font-size:9px;color:#9eb7b2}
.board-foot b{color:#f3b675}
.problem-section{padding:86px 0;background:#fff}
.split-copy{display:grid;grid-template-columns:1fr 1fr;gap:80px;align-items:end}
.section-kicker{display:block;font-size:10px;font-weight:800;letter-spacing:.16em;color:var(--color-primary);margin-bottom:10px}
.split-copy h2,.section-heading h2{font-size:34px;line-height:1.35;letter-spacing:-.04em}
.split-copy>p,.section-heading p{color:var(--color-text-secondary);font-size:14px;line-height:1.8}
.metric-grid{display:grid;grid-template-columns:repeat(4,1fr);gap:12px;margin-top:38px}
.metric-grid article{padding:24px;border:1px solid var(--color-border);border-radius:14px}
.metric-grid h3{font-size:18px;line-height:1.35;color:var(--color-primary);letter-spacing:-.025em}
.metric-grid strong{display:block;font-size:30px;line-height:1;margin-top:14px}
.metric-grid strong small{margin-left:3px;font-size:12px;font-weight:700;color:var(--color-text-muted)}
.metric-grid p{font-size:11px;color:var(--color-text-muted);margin-top:8px}
.flow-section{padding:86px 0;background:#f3f7f6}
.section-heading{text-align:center}
.section-heading p{margin-top:9px}
.flow-grid{display:grid;grid-template-columns:repeat(5,1fr);gap:10px;margin-top:44px}
.flow-grid article{position:relative;padding:20px 18px;background:#fff;border:1px solid var(--color-border);border-radius:14px;min-height:190px}
.flow-no{font-size:9px;color:var(--color-text-muted)}
.flow-icon{display:inline-flex;align-items:center;justify-content:center;width:auto;height:28px;padding:0 10px;border-radius:6px;background:var(--color-primary-light);color:var(--color-primary);font-size:10px;font-weight:800;letter-spacing:.1em;margin:24px 0 14px}
.flow-grid h3{font-size:14px}
.flow-grid p{font-size:11px;color:var(--color-text-muted);line-height:1.6;margin-top:6px}
.flow-grid article>i{position:absolute;right:-15px;top:50%;z-index:2;width:20px;height:20px;display:grid;place-items:center;background:#dfe9e6;border-radius:50%;color:var(--color-primary);font-size:10px;font-style:normal}
.feedback-banner{margin-top:16px;padding:16px 20px;background:#173c37;color:#fff;border-radius:12px;display:flex;align-items:center;gap:20px}
.feedback-banner>span{font-size:9px;letter-spacing:.15em;color:#75d4c1}
.feedback-banner p{font-size:11px;color:#bed0cc;flex:1}
.feedback-banner b{color:#fff}
.role-section{padding:86px 0}
.role-grid{display:grid;grid-template-columns:1fr 1fr;gap:16px}
.role-grid article{padding:38px;border-radius:18px;background:#eff7f5;border:1px solid #d8e9e4}
.role-grid article.supplier{background:#fff6ed;border-color:#f5dfc9}
.role-label{font-size:9px;letter-spacing:.16em;font-weight:800;color:var(--color-primary)}
.role-grid h2{font-size:27px;margin:12px 0}
.role-grid p{font-size:13px;color:var(--color-text-secondary);line-height:1.7;max-width:440px}
.role-grid a{display:inline-block;margin-top:24px;color:var(--color-primary);font-size:12px;font-weight:700}
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
}
</style>
