<template>
  <div class="page-wrapper">
    <AppHeader />
    <main class="main-content">
      <section class="page-heading">
        <div>
          <span class="eyebrow">AI SUPPLIER MATCHING</span>
          <h1>{{ isInstructor ? '조달 품목 관리' : '공공조달 데이터로 공급업체 찾기' }}</h1>
          <p>{{ isInstructor ? '등록된 조달 품목·가격·납품조건을 확인하고 신규 거래 기회를 준비하세요.' : '나라장터 등록 품목의 가격·납기·공급지역·인증정보를 비교해 추천합니다.' }}</p>
        </div>
        <router-link v-if="isInstructor" to="/courses/new" class="btn btn-primary">+ 조달 품목 등록</router-link>
      </section>

      <section v-if="!isInstructor" class="condition-panel">
        <div class="panel-title">
          <span class="step-number">01</span>
          <div><h2>조달 조건 입력</h2><p>CSV의 품명·단가·공급지역·납품일수·인증정보를 필수조건으로 분석합니다.</p></div>
        </div>
        <form class="condition-form" @submit.prevent="runMatching">
          <label><span>품명</span><select v-model="criteria.product"><option v-for="item in productOptions" :key="item">{{ item }}</option></select></label>
          <label><span>규격·품목 키워드</span><input v-model.trim="criteria.keyword" placeholder="예: Φ300mm, 폴리에틸렌" /></label>
          <label><span>공급지역</span><input v-model.trim="criteria.supplyRegion" placeholder="예: 전지역, 제주" /></label>
          <label><span>최대 납품일수</span><select v-model.number="criteria.maxDeliveryDays"><option :value="null">전체</option><option v-for="day in deliveryDayOptions" :key="day" :value="day">{{ day }}일 이내</option></select></label>
          <label><span>필요 수량</span><input v-model.number="criteria.quantity" type="number" min="1" placeholder="100" /></label>
          <label><span>총 예산</span><div class="input-unit"><input v-model.number="criteria.budget" type="number" min="0" placeholder="50,000,000" /><em>원</em></div></label>
          <label><span>기업구분</span><select v-model="criteria.companyType"><option v-for="item in companyTypeOptions" :key="item">{{ item }}</option></select></label>
          <label><span>인증 키워드</span><input v-model.trim="criteria.certification" placeholder="예: KS, 여성기업" /></label>
          <div class="toggle-group"><label><input v-model="criteria.masOnly" type="checkbox" /> MAS 등록</label><label><input v-model="criteria.excellentOnly" type="checkbox" /> 우수제품</label></div>
          <button class="match-button" type="submit"><span>조건 분석 및 매칭</span><small>필수조건 필터 + 다기준 점수화</small></button>
        </form>
      </section>

      <section class="results-section">
        <div class="result-header">
          <div>
            <span class="eyebrow">{{ hasMatched ? '02 · MATCHING RESULT' : 'SUPPLIER NETWORK' }}</span>
            <h2>{{ hasMatched ? `조건을 충족한 공급기업 ${displayCourses.length}곳` : '등록 공급기업' }}</h2>
          </div>
          <div v-if="hasMatched" class="score-legend"><b>추천점수</b> 품목적합 30 · 가격 25 · 납기 20 · 공급지역 10 · 인증 10 · 조달등록 5</div>
        </div>

        <div v-if="loading" class="loading-grid"><div v-for="i in 6" :key="i" class="skeleton-card"></div></div>
        <div v-else-if="displayCourses.length" class="course-grid fade-in">
          <CourseCard v-for="course in displayCourses" :key="course.id" :course="course" />
        </div>
        <div v-else class="empty-state">
          <span>조건을 충족한 업체가 없습니다</span>
          <p>품명, 규격, 공급지역, 수량, 예산 또는 납기 조건을 조정해 다시 분석해 보세요.</p>
          <button v-if="hasMatched" class="btn btn-outline" @click="resetMatching">조건 초기화</button>
          <router-link v-else-if="isInstructor" to="/courses/new" class="btn btn-primary">첫 품목 등록하기</router-link>
        </div>
      </section>
    </main>

    <AppFooter />
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import AppHeader from '@/components/AppHeader.vue'
import AppFooter from '@/components/AppFooter.vue'
import CourseCard from '@/components/CourseCard.vue'
import { useCourseStore } from '@/store/course.js'
import { useAuthStore } from '@/store/auth.js'
import { companyTypeOptions, deliveryDayOptions, evaluateCourse, productOptions } from '@/utils/procurement.js'

const courseStore = useCourseStore()
const auth = useAuthStore()
const hasMatched = ref(false)
const criteria = reactive({ product: '전체', detailProduct: '', keyword: '', supplyRegion: '', maxDeliveryDays: null, quantity: null, budget: null, companyType: '전체', certification: '', excellentOnly: false, masOnly: false })
const loading = computed(() => courseStore.loading)
const isInstructor = computed(() => auth.user?.role === 'INSTRUCTOR')

const displayCourses = computed(() => {
  const courses = Array.isArray(courseStore.courses) ? courseStore.courses : []
  if (!hasMatched.value) return courses
  return courses.map(course => evaluateCourse(course, criteria)).filter(course => course.eligible).sort((a, b) => b.score - a.score)
})

function runMatching() { hasMatched.value = true }
function resetMatching() {
  Object.assign(criteria, { product: '전체', detailProduct: '', keyword: '', supplyRegion: '', maxDeliveryDays: null, quantity: null, budget: null, companyType: '전체', certification: '', excellentOnly: false, masOnly: false })
  hasMatched.value = false
}
onMounted(() => courseStore.fetchCourses())
</script>

<style scoped>
.page-wrapper { min-height:100vh; background:var(--color-bg-secondary); }
.main-content { max-width:1200px; margin:0 auto; padding:44px 24px 80px; }
.page-heading { display:flex; align-items:flex-end; justify-content:space-between; gap:24px; margin-bottom:28px; }
.eyebrow { display:block; margin-bottom:7px; color:var(--color-primary); font-size:10px; font-weight:800; letter-spacing:.16em; }
.page-heading h1 { font-size:30px; line-height:1.3; letter-spacing:-.04em; }
.page-heading p { margin-top:8px; color:var(--color-text-secondary); font-size:14px; }
.condition-panel { padding:26px; border:1px solid #cfe0db; border-radius:18px; background:#fff; box-shadow:0 14px 40px rgba(12,79,68,.08); margin-bottom:42px; }
.panel-title { display:flex; align-items:center; gap:13px; margin-bottom:22px; }
.panel-title h2, .result-header h2 { font-size:19px; }
.panel-title p { font-size:12px; color:var(--color-text-muted); margin-top:2px; }
.step-number { width:38px; height:38px; display:grid; place-items:center; border-radius:10px; color:#fff; background:var(--color-primary); font-size:12px; font-weight:800; }
.condition-form { display:grid; grid-template-columns:repeat(5,1fr); gap:12px; align-items:end; }
.condition-form label { display:flex; flex-direction:column; gap:7px; min-width:0; }
.condition-form label>span { font-size:11px; font-weight:700; color:var(--color-text-secondary); }
.condition-form input,.condition-form select { width:100%; height:46px; padding:0 12px; border:1px solid var(--color-border); border-radius:9px; outline:none; color:var(--color-text-primary); background:#fff; }
.condition-form input:focus,.condition-form select:focus { border-color:var(--color-primary); box-shadow:0 0 0 3px var(--color-primary-light); }
.input-unit { position:relative; }
.input-unit input { padding-right:52px; }
.input-unit em { position:absolute; right:11px; top:50%; transform:translateY(-50%); font-size:10px; color:var(--color-text-muted); font-style:normal; }
.match-button { height:46px; padding:0 18px; border-radius:9px; color:#fff; background:var(--color-primary); display:flex; flex-direction:column; align-items:flex-start; justify-content:center; }
.match-button span { font-size:13px; font-weight:700; }
.match-button small { font-size:9px; opacity:.7; }
.toggle-group { height:46px; display:flex; align-items:center; gap:10px; padding:0 11px; border:1px solid var(--color-border); border-radius:9px; }
.toggle-group label { display:flex; flex-direction:row; align-items:center; gap:5px; font-size:10px; color:var(--color-text-secondary); }
.toggle-group input { width:14px; height:14px; }
.result-header { display:flex; align-items:flex-end; justify-content:space-between; gap:20px; margin-bottom:18px; }
.score-legend { font-size:10px; color:var(--color-text-muted); padding:9px 12px; border:1px solid var(--color-border); border-radius:8px; background:#fff; }
.score-legend b { color:var(--color-primary); margin-right:7px; }
.course-grid,.loading-grid { display:grid; grid-template-columns:repeat(3,1fr); gap:16px; }
.skeleton-card { height:285px; border-radius:14px; background:linear-gradient(90deg,#edf1f0 25%,#f7f9f8 50%,#edf1f0 75%); background-size:200% 100%; animation:shimmer 1.4s infinite; }
@keyframes shimmer { to { background-position:-200% 0; } }
.empty-state { padding:70px 20px; text-align:center; background:#fff; border:1px dashed var(--color-border); border-radius:14px; }
.empty-state span { font-weight:700; }
.empty-state p { margin:7px 0 18px; color:var(--color-text-muted); font-size:13px; }
@media(max-width:1000px){.condition-form{grid-template-columns:repeat(3,1fr)}.course-grid,.loading-grid{grid-template-columns:repeat(2,1fr)}}
@media(max-width:680px){.main-content{padding:28px 16px}.page-heading{align-items:flex-start;flex-direction:column}.condition-form{grid-template-columns:1fr}.course-grid,.loading-grid{grid-template-columns:1fr}.result-header{align-items:flex-start;flex-direction:column}.score-legend{line-height:1.5}}
</style>
