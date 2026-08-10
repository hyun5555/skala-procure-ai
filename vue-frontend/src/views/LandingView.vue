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
              <router-link to="/login" class="btn btn-primary btn-lg">공급기업 매칭 시작</router-link>
              <a href="#flow" class="btn btn-outline btn-lg">서비스 흐름 보기</a>
            </div>
            <div class="trust-row"><span>추천 기준</span><b>품목 적합도</b><b>등록 단가</b><b>납품일수</b><b>인증·MAS</b></div>
          </div>
          <div class="match-board fade-in">
            <div class="board-top"><span>AI MATCH REPORT</span><small>실시간 조건 분석</small></div>
            <div class="condition-tags"><span>파형강관</span><span>Φ300mm</span><span>전지역</span><span>30일</span></div>
            <div v-for="(supplier,index) in suppliers" :key="supplier.name" class="supplier-row">
              <b class="rank">0{{ index+1 }}</b><div><strong>{{ supplier.name }}</strong><small>{{ supplier.reason }}</small></div><em>{{ supplier.score }}<i>점</i></em>
            </div>
            <div class="board-foot"><span>필수조건 충족 업체 3곳</span><b>조달 등록정보 반영 완료 ✓</b></div>
          </div>
        </div>
      </section>

      <section class="problem-section">
        <div class="section-inner split-copy">
          <div><span class="section-kicker">WHY PROCURIX</span><h2>가장 싼 업체가<br>가장 좋은 업체는 아닙니다.</h2></div>
          <p>낮은 단가 뒤에 숨은 재검사·재작업·생산 지연 비용까지 고려해야 합니다. Procurix는 흩어진 가격, 납기, 품질 이력을 하나의 판단 기준으로 연결합니다.</p>
        </div>
        <div class="section-inner metric-grid">
          <article v-for="metric in metrics" :key="metric.label"><span>{{ metric.icon }}</span><strong>{{ metric.value }}</strong><small>{{ metric.label }}</small><p>{{ metric.desc }}</p></article>
        </div>
      </section>

      <section id="flow" class="flow-section">
        <div class="section-inner">
          <div class="section-heading"><span class="section-kicker">DATA FEEDBACK LOOP</span><h2>거래할수록 더 정확해지는 추천</h2><p>납품 이후의 품질 결과가 다음 조달의 더 나은 판단 근거가 됩니다.</p></div>
          <div class="flow-grid">
            <article v-for="(step,index) in flow" :key="step.title"><span class="flow-no">0{{ index+1 }}</span><div class="flow-icon">{{ step.icon }}</div><h3>{{ step.title }}</h3><p>{{ step.desc }}</p><i v-if="index<flow.length-1">→</i></article>
          </div>
          <div class="feedback-banner"><span>QUALITY DATA</span><p><b>납품수량 · 불량수량 · 불량유형</b>이 공급업체 품질지표로 누적되고 다음 추천점수에 반영됩니다.</p><em>↻</em></div>
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
  {name:'남강철강 주식회사',score:95,reason:'30일 납품 · MAS 등록'},
  {name:'영남산업 주식회사',score:91,reason:'전지역 공급 · KS 인증'},
  {name:'주식회사 제철산업',score:87,reason:'예산 충족 · 우수제품'}
]
const metrics=[
  {icon:'◎',value:'30',label:'품목 적합도',desc:'품명·세부품명·규격 키워드'},
  {icon:'₩',value:'25',label:'가격 점수',desc:'수량과 총예산 대비 등록 단가'},
  {icon:'◷',value:'20',label:'납기 점수',desc:'30~120일 등록 납품일수'},
  {icon:'✓',value:'25',label:'조달 신뢰도',desc:'공급지역·인증·우수제품·MAS'}
]
const flow=[
  {icon:'▦',title:'품목 등록',desc:'공급기업이 품목·단가·납품조건 등록'},
  {icon:'⌕',title:'조건 분석',desc:'품명·규격·지역·납기로 후보 필터링'},
  {icon:'AI',title:'추천 순위',desc:'가격·납기·인증·조달정보를 점수화'},
  {icon:'↗',title:'발주·납품',desc:'추천 근거 확인 후 발주와 결제 진행'},
  {icon:'✓',title:'품질 등록',desc:'납품수량·불량유형을 품질 데이터로 축적'}
]
</script>

<style scoped>
.landing{background:#fff}.section-inner{max-width:1180px;margin:0 auto;padding-left:24px;padding-right:24px}.hero{background:linear-gradient(120deg,#f2f8f6 0%,#fff 65%);border-bottom:1px solid var(--color-border);overflow:hidden}.hero-inner{max-width:1180px;margin:0 auto;padding:88px 24px 84px;display:grid;grid-template-columns:1.08fr .92fr;gap:70px;align-items:center}.hero-badge{display:inline-flex;align-items:center;gap:7px;font-size:11px;font-weight:700;color:var(--color-primary);letter-spacing:.04em}.hero-badge i{width:7px;height:7px;border-radius:50%;background:var(--color-secondary);box-shadow:0 0 0 4px #f9e9d9}.hero h1{font-size:48px;line-height:1.22;letter-spacing:-.055em;margin:18px 0}.hero h1 em{font-style:normal;color:var(--color-primary)}.hero-copy>p{max-width:610px;color:var(--color-text-secondary);font-size:16px;line-height:1.8}.hero-actions{display:flex;gap:10px;margin:28px 0}.btn-lg{padding:13px 21px}.trust-row{display:flex;align-items:center;gap:13px;flex-wrap:wrap;font-size:10px;color:var(--color-text-muted)}.trust-row span{padding-right:12px;border-right:1px solid var(--color-border)}.trust-row b{font-weight:600}.match-board{background:#173C37;color:#fff;border-radius:20px;padding:24px;box-shadow:0 24px 60px rgba(10,60,52,.22);position:relative}.match-board:before{content:'';position:absolute;inset:10px;border:1px solid rgba(255,255,255,.08);border-radius:14px;pointer-events:none}.board-top,.board-foot{display:flex;justify-content:space-between;align-items:center}.board-top span{font-size:11px;font-weight:800;letter-spacing:.16em}.board-top small{font-size:9px;color:#8dd7c8}.condition-tags{display:flex;gap:6px;flex-wrap:wrap;margin:20px 0 15px}.condition-tags span{font-size:9px;border:1px solid rgba(255,255,255,.16);border-radius:20px;padding:5px 9px;color:#cfe3df}.supplier-row{position:relative;display:grid;grid-template-columns:32px 1fr auto;gap:10px;align-items:center;padding:14px 0;border-top:1px solid rgba(255,255,255,.1)}.supplier-row .rank{font-size:10px;color:#77aa9f}.supplier-row div{display:flex;flex-direction:column;gap:3px}.supplier-row strong{font-size:13px}.supplier-row small{font-size:9px;color:#9eb7b2}.supplier-row em{font-size:23px;font-weight:800;color:#75d4c1;font-style:normal}.supplier-row em i{font-size:9px;font-style:normal;margin-left:2px}.board-foot{margin-top:12px;font-size:9px;color:#9eb7b2}.board-foot b{color:#f3b675}.problem-section{padding:86px 0;background:#fff}.split-copy{display:grid;grid-template-columns:1fr 1fr;gap:80px;align-items:end}.section-kicker{display:block;font-size:10px;font-weight:800;letter-spacing:.16em;color:var(--color-primary);margin-bottom:10px}.split-copy h2,.section-heading h2{font-size:34px;line-height:1.35;letter-spacing:-.04em}.split-copy>p,.section-heading p{color:var(--color-text-secondary);font-size:14px;line-height:1.8}.metric-grid{display:grid;grid-template-columns:repeat(4,1fr);gap:12px;margin-top:38px}.metric-grid article{padding:24px;border:1px solid var(--color-border);border-radius:14px}.metric-grid article>span{display:grid;place-items:center;width:32px;height:32px;border-radius:8px;background:var(--color-primary-light);color:var(--color-primary);font-weight:800}.metric-grid strong{display:block;font-size:30px;margin-top:18px}.metric-grid small{font-size:11px;font-weight:700;color:var(--color-primary)}.metric-grid p{font-size:11px;color:var(--color-text-muted);margin-top:8px}.flow-section{padding:86px 0;background:#f3f7f6}.section-heading{text-align:center}.section-heading p{margin-top:9px}.flow-grid{display:grid;grid-template-columns:repeat(5,1fr);gap:10px;margin-top:44px}.flow-grid article{position:relative;padding:20px 18px;background:#fff;border:1px solid var(--color-border);border-radius:14px;min-height:190px}.flow-no{font-size:9px;color:var(--color-text-muted)}.flow-icon{display:grid;place-items:center;width:40px;height:40px;border-radius:10px;background:var(--color-primary-light);color:var(--color-primary);font-size:13px;font-weight:800;margin:24px 0 14px}.flow-grid h3{font-size:14px}.flow-grid p{font-size:11px;color:var(--color-text-muted);line-height:1.6;margin-top:6px}.flow-grid article>i{position:absolute;right:-15px;top:50%;z-index:2;width:20px;height:20px;display:grid;place-items:center;background:#dfe9e6;border-radius:50%;color:var(--color-primary);font-size:10px;font-style:normal}.feedback-banner{margin-top:16px;padding:16px 20px;background:#173c37;color:#fff;border-radius:12px;display:flex;align-items:center;gap:20px}.feedback-banner>span{font-size:9px;letter-spacing:.15em;color:#75d4c1}.feedback-banner p{font-size:11px;color:#bed0cc;flex:1}.feedback-banner b{color:#fff}.feedback-banner em{font-style:normal;font-size:22px;color:#75d4c1}.role-section{padding:86px 0}.role-grid{display:grid;grid-template-columns:1fr 1fr;gap:16px}.role-grid article{padding:38px;border-radius:18px;background:#eff7f5;border:1px solid #d8e9e4}.role-grid article.supplier{background:#fff6ed;border-color:#f5dfc9}.role-label{font-size:9px;letter-spacing:.16em;font-weight:800;color:var(--color-primary)}.role-grid h2{font-size:27px;margin:12px 0}.role-grid p{font-size:13px;color:var(--color-text-secondary);line-height:1.7;max-width:440px}.role-grid a{display:inline-block;margin-top:24px;color:var(--color-primary);font-size:12px;font-weight:700}@media(max-width:900px){.hero-inner{grid-template-columns:1fr;padding-top:60px}.split-copy{grid-template-columns:1fr;gap:20px}.metric-grid{grid-template-columns:repeat(2,1fr)}.flow-grid{grid-template-columns:1fr 1fr}.flow-grid article>i{display:none}}@media(max-width:600px){.hero-inner{padding:48px 16px}.hero h1{font-size:35px}.metric-grid,.role-grid,.flow-grid{grid-template-columns:1fr}.section-inner{padding-left:16px;padding-right:16px}.problem-section,.flow-section,.role-section{padding:60px 0}.feedback-banner{align-items:flex-start;flex-direction:column}}
</style>
