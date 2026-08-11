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

      <section v-if="!isInstructor" class="recommend-section">
        <div class="result-header">
          <div><span class="eyebrow">AI RECOMMENDATION</span><h2>거래 이력 기반 추천 공급기업</h2><p class="recommend-message">{{ recommendationMessage }}</p></div>
          <span v-if="recommendationCategory" class="recommend-category">관심 품명 · {{ recommendationCategory }}</span>
        </div>
        <div v-if="recommendationLoading" class="loading-grid"><div v-for="i in 3" :key="i" class="skeleton-card"></div></div>
        <div v-else-if="recommendedCourses.length" class="course-grid"><CourseCard v-for="course in recommendedCourses" :key="`recommend-${course.id}`" :course="course" /></div>
        <p v-else class="recommend-empty">{{ recommendationError || '추천 결과가 아직 없습니다.' }}</p>
      </section>

      <section v-if="!isInstructor" class="condition-panel">
        <div class="panel-title">
          <span class="step-number">01</span>
          <div><h2>조달 조건 입력</h2><p>CSV의 품명·단가·공급지역·납품일수·인증정보를 필수조건으로 분석합니다.</p></div>
        </div>
        <form class="condition-form" @submit.prevent="runMatching">
          <label><span>품명</span><select v-model="criteria.product"><option v-for="item in productOptions" :key="item">{{ item }}</option></select></label>
          <label><span>규격</span><select v-model="criteria.specification"><option value="전체">전체 규격</option><option v-for="spec in specificationOptions" :key="spec" :value="spec">{{ spec }}</option></select></label>
          <label><span>품목 키워드</span><input v-model.trim="criteria.keyword" placeholder="예: 연결관, 폴리에틸렌" /></label>
          <div class="condition-field"><span>공급지역</span><RegionMultiSelect v-model="criteria.supplyRegions" placeholder="희망 공급지역 선택" aria-label="희망 공급지역 선택" /></div>
          <label><span>최대 납품일수</span><select v-model.number="criteria.maxDeliveryDays"><option :value="null">전체</option><option v-for="day in deliveryDayOptions" :key="day" :value="day">{{ day }}일 이내</option></select></label>
          <label><span>필요 수량</span><input v-model.number="criteria.quantity" type="number" min="1" placeholder="100" /></label>
          <label><span>총 예산</span><div class="input-unit"><input v-model.number="criteria.budget" type="number" min="0" placeholder="50,000,000" /><em>원</em></div></label>
          <fieldset class="certification-group">
            <legend>인증 및 등록 조건</legend>
            <label v-for="item in certificationOptions" :key="item"><input v-model="criteria.certifications" type="checkbox" :value="item" /> {{ item }}</label>
            <label><input v-model="criteria.masOnly" type="checkbox" /> MAS 등록</label>
            <label><input v-model="criteria.excellentOnly" type="checkbox" /> 우수제품</label>
          </fieldset>
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
import { computed, onMounted, reactive, ref, watch } from 'vue'
import AppHeader from '@/components/AppHeader.vue'
import AppFooter from '@/components/AppFooter.vue'
import CourseCard from '@/components/CourseCard.vue'
import RegionMultiSelect from '@/components/RegionMultiSelect.vue'
import { useCourseStore } from '@/store/course.js'
import { useAuthStore } from '@/store/auth.js'
import { deliveryDayOptions, evaluateCourse, productOptions } from '@/utils/procurement.js'
import { recommendApi } from '@/api/recommend.js'

const courseStore = useCourseStore()
const auth = useAuthStore()
const hasMatched = ref(false)
const recommendedCourses = ref([])
const recommendationMessage = ref('거래 이력과 공급성과를 분석하고 있습니다.')
const recommendationCategory = ref('')
const recommendationLoading = ref(false)
const recommendationError = ref('')
const criteria = reactive({ product: '전체', detailProduct: '', specification: '전체', keyword: '', supplyRegions: [], maxDeliveryDays: null, quantity: null, budget: null, certifications: [], excellentOnly: false, masOnly: false })
const loading = computed(() => courseStore.loading)
const isInstructor = computed(() => auth.user?.role === 'INSTRUCTOR')
const specificationOptions = ['소형', '중형', '대형', '주문 규격']
const certificationOptions = ['KS', '여성기업', '장애인기업', '창업기업', '품질보증조달물품']

const displayCourses = computed(() => {
  const courses = Array.isArray(courseStore.courses) ? courseStore.courses : []
  if (!hasMatched.value) return courses
  return courses.map(course => evaluateCourse(course, criteria)).filter(course => course.eligible).sort((a, b) => b.score - a.score)
})

async function runMatching() {
  if (!auth.isDemo && criteria.product !== '전체') {
    const category = Object.entries(courseStore.categoryLabelMap).find(([, label]) => label === criteria.product)?.[0]
    if (category) {
      try { await courseStore.fetchCoursesByCategory(category) } catch { /* store error is shown by the empty state */ }
    }
  } else if (!auth.isDemo && criteria.product === '전체') await courseStore.fetchCourses()
  hasMatched.value = true
}
async function resetMatching() {
  Object.assign(criteria, { product: '전체', detailProduct: '', specification: '전체', keyword: '', supplyRegions: [], maxDeliveryDays: null, quantity: null, budget: null, certifications: [], excellentOnly: false, masOnly: false })
  hasMatched.value = false
  await courseStore.fetchCourses()
}
watch(() => criteria.product, () => { criteria.specification = '전체' })
async function loadRecommendations() {
  if (isInstructor.value || auth.isDemo || !auth.user?.id) return
  recommendationLoading.value = true; recommendationError.value = ''
  try {
    const res = await recommendApi.getForUser(auth.user.id)
    const data = res.data?.data || res.data
    recommendedCourses.value = (data?.recommendedCourses || []).map(courseStore.normalizeCourse)
    recommendationCategory.value = courseStore.normalizeCategory(data?.basedOnCategory)
    recommendationMessage.value = data?.message || '공급성과와 거래 이력을 반영한 추천 결과입니다.'
  } catch (e) {
    recommendationError.value = e.response?.data?.message || '추천 결과를 불러오지 못했습니다.'
  } finally { recommendationLoading.value = false }
}
onMounted(() => Promise.allSettled([courseStore.fetchCourses(), loadRecommendations()]))
</script>

<style scoped>
.page-wrapper { min-height:100vh; background:var(--color-bg-secondary); }
.main-content { max-width:1200px; margin:0 auto; padding:44px 24px 80px; }
.page-heading { display:flex; align-items:flex-end; justify-content:space-between; gap:24px; margin-bottom:28px; }
.eyebrow { display:block; margin-bottom:7px; color:var(--color-primary); font-size:10px; font-weight:800; letter-spacing:.16em; }
.page-heading h1 { font-size:30px; line-height:1.3; letter-spacing:-.04em; }
.page-heading p { margin-top:8px; color:var(--color-text-secondary); font-size:14px; }
.condition-panel { padding:26px; border:1px solid #d9e6ed; border-radius:18px; background:#fff; box-shadow:0 14px 40px rgba(30,48,60,.08); margin-bottom:42px; }
.recommend-section{margin-bottom:36px;padding:24px;border:1px solid var(--color-border);border-radius:18px;background:linear-gradient(135deg,#f7fbfd,#fff)}.recommend-message{margin-top:5px;font-size:12px;color:var(--color-text-muted)}.recommend-category{padding:7px 11px;border-radius:999px;background:var(--color-primary-light);color:var(--color-accent-dark);font-size:10px;font-weight:700}.recommend-empty{padding:22px;border-radius:10px;background:#fff;color:var(--color-text-muted);font-size:12px;text-align:center}
.panel-title { display:flex; align-items:center; gap:13px; margin-bottom:22px; }
.panel-title h2, .result-header h2 { font-size:19px; }
.panel-title p { font-size:12px; color:var(--color-text-muted); margin-top:2px; }
.step-number { width:38px; height:38px; display:grid; place-items:center; border-radius:10px; color:#fff; background:var(--color-primary); font-size:12px; font-weight:800; }
.condition-form { display:grid; grid-template-columns:repeat(5,1fr); gap:12px; align-items:end; }
.condition-form label,.condition-field { display:flex; flex-direction:column; gap:7px; min-width:0; }
.condition-form label>span,.condition-field>span { font-size:11px; font-weight:700; color:var(--color-text-secondary); }
.condition-field :deep(.select-trigger) { height:46px; }
.condition-form input,.condition-form select { width:100%; height:46px; padding:0 12px; border:1px solid var(--color-border); border-radius:9px; outline:none; color:var(--color-text-primary); background:#fff; }
.condition-form input:focus,.condition-form select:focus { border-color:var(--color-primary); box-shadow:0 0 0 3px var(--color-primary-light); }
.input-unit { position:relative; }
.input-unit input { padding-right:52px; }
.input-unit em { position:absolute; right:11px; top:50%; transform:translateY(-50%); font-size:10px; color:var(--color-text-muted); font-style:normal; }
.match-button { height:46px; padding:0 18px; border-radius:9px; color:#fff; background:var(--color-primary); display:flex; flex-direction:column; align-items:flex-start; justify-content:center; }
.match-button span { font-size:13px; font-weight:700; }
.match-button small { font-size:9px; opacity:.7; }
.certification-group { grid-column:span 2;min-height:46px;display:flex;align-content:center;align-items:center;gap:7px 11px;flex-wrap:wrap;padding:7px 11px;border:1px solid var(--color-border);border-radius:9px; }
.certification-group legend { padding:0 5px;font-size:10px;font-weight:700;color:var(--color-text-secondary); }
.certification-group label { display:flex;flex-direction:row;align-items:center;gap:5px;font-size:10px;color:var(--color-text-secondary);white-space:nowrap; }
.certification-group input { width:14px;height:14px;accent-color:var(--color-accent-dark); }
.result-header { display:flex; align-items:flex-end; justify-content:space-between; gap:20px; margin-bottom:18px; }
.score-legend { font-size:10px; color:var(--color-text-muted); padding:9px 12px; border:1px solid var(--color-border); border-radius:8px; background:#fff; }
.score-legend b { color:var(--color-primary); margin-right:7px; }
.course-grid,.loading-grid { display:grid; grid-template-columns:repeat(3,1fr); gap:16px; }
.skeleton-card { height:285px; border-radius:14px; background:linear-gradient(90deg,#edf1f3 25%,#f8fafb 50%,#edf1f3 75%); background-size:200% 100%; animation:shimmer 1.4s infinite; }
@keyframes shimmer { to { background-position:-200% 0; } }
.empty-state { padding:70px 20px; text-align:center; background:#fff; border:1px dashed var(--color-border); border-radius:14px; }
.empty-state span { font-weight:700; }
.empty-state p { margin:7px 0 18px; color:var(--color-text-muted); font-size:13px; }
@media(max-width:1000px){.condition-form{grid-template-columns:repeat(3,1fr)}.course-grid,.loading-grid{grid-template-columns:repeat(2,1fr)}}
@media(max-width:680px){.main-content{padding:28px 16px}.page-heading{align-items:flex-start;flex-direction:column}.condition-form{grid-template-columns:1fr}.certification-group{grid-column:auto}.course-grid,.loading-grid{grid-template-columns:1fr}.result-header{align-items:flex-start;flex-direction:column}.score-legend{line-height:1.5}}
.eyebrow,.score-legend b { color:var(--color-accent-dark); }
.condition-form input:focus,.condition-form select:focus { border-color:var(--color-accent); box-shadow:0 0 0 3px var(--color-primary-light); }
</style>
