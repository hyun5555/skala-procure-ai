<template>
  <div class="page-wrapper">
    <AppHeader />
    <div class="page-layout">
      <main class="main-content">
        <!-- 프로필 카드 -->
        <div class="profile-card fade-in-up">
          <div class="profile-avatar">{{ auth.user?.name?.charAt(0) || '?' }}</div>
          <div class="profile-info">
            <h2 class="profile-name">{{ auth.user?.name || '사용자' }}</h2>
            <p class="profile-email">{{ auth.user?.email || '-' }}</p>
            <span class="badge" :class="isInstructor ? 'badge-amber' : 'badge-blue'">
              {{ isInstructor ? '공급기업' : '구매기업' }}
            </span>
          </div>
        </div>

        <!-- 공급기업 화면 -->
        <section class="instructor-section">
          <section
            v-if="!instructorLoading && myCourses.length"
            class="trade-analytics-card fade-in"
            aria-labelledby="trade-distribution-title"
          >
            <div class="analytics-heading">
              <div>
                <span class="analytics-kicker">TRADE DISTRIBUTION</span>
                <h4 id="trade-distribution-title">품목별 거래 비중</h4>
                <p>등록 품목의 누적 거래건수를 품명별로 집계했습니다.</p>
              </div>
              <div class="top-product-chip">
                <span>거래 비중 1위</span>
                <b>{{ topTradeProduct?.category || '-' }}</b>
              </div>
            </div>

            <div class="analytics-body">
              <div
                class="donut-chart"
                :style="donutChartStyle"
                role="img"
                :aria-label="tradeChartAriaLabel"
              >
                <div class="donut-center">
                  <strong>{{ totalEnrollmentCount.toLocaleString() }}</strong>
                  <span>전체 거래</span>
                </div>
              </div>

              <div class="trade-legend">
                <div v-for="item in tradeDistribution" :key="item.category" class="legend-row">
                  <span class="legend-dot" :style="{ background: item.color }"></span>
                  <div class="legend-label">
                    <b>{{ item.category }}</b>
                    <small>{{ item.count.toLocaleString() }}건</small>
                  </div>
                  <strong>{{ item.percent.toFixed(1) }}%</strong>
                  <div class="legend-track"><i :style="{ width: `${item.percent}%`, background: item.color }"></i></div>
                </div>
              </div>

              <aside class="catalog-summary-panel">
                <div class="catalog-summary-heading">
                  <span>SUPPLIER CATALOG</span>
                  <h3>자사 조달 품목</h3>
                  <p>등록한 품목·단가·납품조건과 누적 거래 성과를 확인할 수 있습니다.</p>
                </div>
                <div class="summary-cards">
                  <div class="summary-card">
                    <div class="summary-label">등록 품목 수</div>
                    <div class="summary-value">{{ myCourses.length }}</div>
                  </div>
                  <div class="summary-card accent">
                    <div class="summary-label">누적 거래 수</div>
                    <div class="summary-value">{{ totalEnrollmentCount.toLocaleString() }}</div>
                  </div>
                </div>
              </aside>
            </div>

            <div class="analytics-footer">
              <p class="analytics-note">품목별 거래 비중은 현재 등록된 누적 거래건수 기준이며, 품질검사 데이터가 연동되면 불량률 분석으로 확장할 수 있습니다.</p>
              <router-link to="/courses/new" class="btn btn-primary catalog-register-button">+ 조달 품목 등록</router-link>
            </div>
          </section>

          <p v-if="statusError" class="status-error" role="alert">{{ statusError }}</p>

          <div v-if="instructorLoading" class="loading-row instructor-loading">
            <div v-for="i in 3" :key="i" class="skeleton-card">
              <div class="skeleton-thumb"></div>
              <div class="skeleton-body">
                <div class="skeleton-line short"></div>
                <div class="skeleton-line"></div>
              </div>
            </div>
          </div>

          <div v-else-if="myCourses.length" class="instructor-course-list fade-in">
            <div
              v-for="course in myCourses"
              :key="course.id"
              class="instructor-course-card"
            >
              <div class="course-card-top">
                <div>
                  <h4 class="course-title">{{ course.title }}</h4>
                  <p class="course-desc">{{ course.description || '설명이 없습니다.' }}</p>
                </div>
                <label class="availability-control" :class="{ soldout: course.status !== 'ACTIVE' }">
                  <span class="availability-copy"><b>{{ course.status === 'ACTIVE' ? '거래 가능' : '품절' }}</b><small>{{ course.status === 'ACTIVE' ? '구매기업에 노출 중' : '거래 및 노출 중지' }}</small></span>
                  <input type="checkbox" :checked="course.status === 'ACTIVE'" :disabled="statusUpdating[course.id]" :aria-label="`${course.title} 거래 가능 상태`" @change="toggleCourseStatus(course, $event.target.checked)">
                  <span class="switch-track" aria-hidden="true"><i></i></span>
                </label>
              </div>

              <div class="course-meta-grid">
                <div class="meta-box">
                  <div class="meta-label">품명</div>
                  <div class="meta-value">{{ course.category || '-' }}</div>
                </div>
                <div class="meta-box">
                  <div class="meta-label">기준 단가</div>
                  <div class="meta-value">{{ formatPrice(course.price) }}</div>
                </div>
                <div class="meta-box">
                  <div class="meta-label">누적 거래</div>
                  <div class="meta-value">
                    {{ course.enrollment_count ?? course.enrollmentCount ?? 0 }}건
                  </div>
                </div>
                <div class="meta-box">
                  <div class="meta-label">품목 ID</div>
                  <div class="meta-value">#{{ course.id }}</div>
                </div>
              </div>

              <div class="course-card-actions">
                <router-link :to="`/courses/${course.id}`" class="action-btn action-primary">
                  품목 상세
                </router-link>
              </div>
            </div>
          </div>

          <p v-else-if="instructorError" class="empty-text">
            {{ instructorError }}
          </p>

          <div v-else class="empty-catalog">
            <p class="empty-text">아직 등록한 조달 품목이 없습니다.</p>
            <router-link to="/courses/new" class="btn btn-primary">첫 조달 품목 등록</router-link>
          </div>
        </section>
      </main>
    </div>

    <AppFooter />
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import AppHeader from '@/components/AppHeader.vue'
import AppFooter from '@/components/AppFooter.vue'
import { useAuthStore } from '@/store/auth.js'
import { courseApi } from '@/api/course.js'
import { useCourseStore } from '@/store/course.js'
import { getDemoCourses, updateDemoCourseStatus } from '@/data/demo.js'

const auth = useAuthStore()
const courseStore = useCourseStore()

const isInstructor = computed(() => auth.user?.role === 'INSTRUCTOR')

/* 강사용 */
const myCourses = ref([])
const instructorLoading = ref(true)
const instructorError = ref('')
const statusError = ref('')
const statusUpdating = ref({})

const totalEnrollmentCount = computed(() =>
  myCourses.value.reduce((sum, course) => {
    const count = Number(course.enrollment_count ?? course.enrollmentCount ?? 0)
    return sum + (Number.isNaN(count) ? 0 : count)
  }, 0)
)

const chartColors = ['#62B4E2', '#A8D3EC', '#7C8992', '#C5CDD2', '#86B9D5', '#9EB9AA', '#D6A997', '#657B88']

const tradeDistribution = computed(() => {
  const grouped = new Map()

  myCourses.value.forEach(course => {
    const category = course.category || '기타'
    const count = Number(course.enrollment_count ?? course.enrollmentCount ?? 0)
    grouped.set(category, (grouped.get(category) || 0) + (Number.isNaN(count) ? 0 : count))
  })

  const total = totalEnrollmentCount.value
  let start = 0

  return [...grouped.entries()]
    .sort((a, b) => b[1] - a[1])
    .map(([category, count], index) => {
      const percent = total > 0 ? (count / total) * 100 : 0
      const item = { category, count, percent, start, end: start + percent, color: chartColors[index % chartColors.length] }
      start += percent
      return item
    })
})

const topTradeProduct = computed(() => tradeDistribution.value[0] || null)

const donutChartStyle = computed(() => {
  if (!totalEnrollmentCount.value) return { background: '#E7ECEA' }
  const stops = tradeDistribution.value.map(item => `${item.color} ${item.start}% ${item.end}%`)
  return { background: `conic-gradient(${stops.join(', ')})` }
})

const tradeChartAriaLabel = computed(() =>
  tradeDistribution.value.map(item => `${item.category} ${item.percent.toFixed(1)}퍼센트`).join(', ')
)

function formatPrice(price) {
  const value = Number(price ?? 0)
  if (Number.isNaN(value)) return '-'
  return `${value.toLocaleString()}원`
}

async function toggleCourseStatus(course, isActive) {
  const nextStatus = isActive ? 'ACTIVE' : 'INACTIVE'
  const previousStatus = course.status
  statusError.value = ''
  statusUpdating.value = { ...statusUpdating.value, [course.id]: true }
  course.status = nextStatus
  try {
    if (auth.isDemo) updateDemoCourseStatus(course.id, nextStatus)
    else await courseApi.updateStatus(course.id, nextStatus)
  } catch (error) {
    course.status = previousStatus
    statusError.value = error.response?.data?.message || '품목 거래 상태를 변경하지 못했습니다.'
  } finally {
    statusUpdating.value = { ...statusUpdating.value, [course.id]: false }
  }
}

/**
 * course 객체에서 강사 식별자 추출
 */
function getCourseInstructorId(course) {
  return (
    course.instructorId ??
    course.instructor_id ??
    course.instructor ??
    course.teacherId ??
    course.teacher_id ??
    null
  )
}

async function loadInstructorCourses() {
  try {
    if (auth.isDemo) {
      myCourses.value = getDemoCourses().filter(course => Number(course.instructorId) === Number(auth.user?.id)).map(courseStore.normalizeCourse)
      return
    }
    if (!auth.user) {
      console.warn('[MyPage] instructor auth.user is missing')
      instructorError.value = '조달 품목 정보를 불러오지 못했습니다.'
      return
    }

    if (!auth.user.id) {
      console.warn('[MyPage] instructor auth.user.id is missing:', auth.user)
      instructorError.value = '조달 품목 정보를 불러오지 못했습니다.'
      return
    }

    const res = await courseApi.getMine()
    console.log('[MyPage] course list response:', res.data)

    let courses = []
    if (Array.isArray(res.data?.data)) {
      courses = res.data.data
    } else if (Array.isArray(res.data)) {
      courses = res.data
    } else {
      console.warn('[MyPage] unexpected course response shape:', res.data)
    }

    const instructorId = Number(auth.user.id)
    myCourses.value = courses
      .filter(course => {
        const courseInstructorId = Number(getCourseInstructorId(course))
        return !Number.isNaN(courseInstructorId) && courseInstructorId === instructorId
      })
      .map(courseStore.normalizeCourse)

    console.log('[MyPage] filtered myCourses =', myCourses.value)
  } catch (error) {
    console.error('[MyPage] failed to load instructor courses:', error)
    instructorError.value = '현재 조달 품목 정보를 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.'
  } finally {
    instructorLoading.value = false
  }
}

onMounted(loadInstructorCourses)
</script>

<style scoped>
.page-wrapper {
  min-height: 100vh;
  background: var(--color-bg-secondary);
}

.page-layout {
  max-width: 1160px;
  margin: 0 auto;
  padding: 32px 24px;
  width: 100%;
}

.main-content {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 32px;
}

.profile-card {
  display: flex;
  align-items: center;
  gap: 20px;
  background: var(--color-bg-primary);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  padding: 28px;
  box-shadow: var(--shadow-sm);
}

.profile-avatar {
  width: 64px;
  height: 64px;
  border-radius: 50%;
  background: var(--color-primary-light);
  color: var(--color-accent-dark);
  font-size: 24px;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.profile-info {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.profile-name {
  font-size: 20px;
  font-weight: 700;
}

.profile-email {
  font-size: 14px;
  color: var(--color-text-secondary);
}

.badge {
  display: inline-flex;
  align-items: center;
  width: fit-content;
  padding: 6px 12px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 600;
}

.badge-blue {
  background: #eaf6fd;
  color: #2e7faf;
}

.badge-amber {
  background: #f8e2da;
  color: #9a684c;
}

.loading-row {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 16px;
}

.instructor-loading {
  margin-bottom: 20px;
}

.skeleton-card {
  background: var(--color-bg-primary);
  border-radius: var(--radius-lg);
  overflow: hidden;
  border: 1px solid var(--color-border);
}

.skeleton-thumb {
  height: 110px;
  background: linear-gradient(90deg, #f0f0f0 25%, #e0e0e0 50%, #f0f0f0 75%);
  background-size: 200% 100%;
  animation: shimmer 1.4s infinite;
}

.skeleton-body {
  padding: 14px 16px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.skeleton-line {
  height: 12px;
  border-radius: 6px;
  background: linear-gradient(90deg, #f0f0f0 25%, #e0e0e0 50%, #f0f0f0 75%);
  background-size: 200% 100%;
  animation: shimmer 1.4s infinite;
}

.skeleton-line.short {
  width: 40%;
}

.summary-cards {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px;
}

.summary-card {
  background: #fff;
  border: 1px solid var(--color-border);
  border-radius: 10px;
  padding: 15px;
  box-shadow: none;
}

.summary-card.accent { background: var(--color-primary); border-color: var(--color-primary); }
.summary-card.accent .summary-label { color: rgba(255,255,255,.72); }
.summary-card.accent .summary-value { color: #fff; }

.summary-label {
  font-size: 12px;
  color: var(--color-text-muted);
  margin-bottom: 8px;
}

.summary-value {
  font-size: 25px;
  font-weight: 700;
  color: var(--color-text-primary);
}

.trade-analytics-card {
  background: var(--color-bg-primary);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  padding: 24px 26px 18px;
  box-shadow: var(--shadow-sm);
  margin-bottom: 22px;
}

.analytics-heading {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 20px;
  padding-bottom: 18px;
  border-bottom: 1px solid var(--color-border);
}

.analytics-kicker {
  display: block;
  color: var(--color-accent-dark);
  font-size: 9px;
  font-weight: 800;
  letter-spacing: .14em;
  margin-bottom: 5px;
}

.analytics-heading h4 { font-size: 17px; }
.analytics-heading p { margin-top: 4px; font-size: 11px; color: var(--color-text-muted); }

.top-product-chip {
  min-width: 130px;
  padding: 9px 12px;
  border-radius: 9px;
  background: var(--color-primary-light);
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.top-product-chip span { font-size: 9px; color: var(--color-text-muted); }
.top-product-chip b { font-size: 12px; color: var(--color-primary-dark); }

.analytics-body {
  display: grid;
  grid-template-columns: 190px minmax(210px, 1fr) 240px;
  gap: 24px;
  align-items: center;
  padding: 24px 8px 20px;
}

.catalog-summary-panel {
  align-self: stretch;
  padding: 18px;
  border-radius: 12px;
  background: var(--color-bg-secondary);
  border: 1px solid var(--color-border);
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  gap: 18px;
}

.catalog-summary-heading > span {
  display: block;
  margin-bottom: 7px;
  color: var(--color-secondary);
  font-size: 8px;
  font-weight: 800;
  letter-spacing: .13em;
}

.catalog-summary-heading h3 { font-size: 16px; }
.catalog-summary-heading p { margin-top: 6px; color: var(--color-text-muted); font-size: 10px; line-height: 1.55; }

.donut-chart {
  position: relative;
  width: 174px;
  height: 174px;
  margin: 0 auto;
  border-radius: 50%;
  box-shadow: 0 8px 24px rgba(36, 64, 80, .12);
}

.donut-chart::after {
  content: '';
  position: absolute;
  inset: 25px;
  border-radius: 50%;
  background: #fff;
  box-shadow: inset 0 0 0 1px var(--color-border);
}

.donut-center {
  position: absolute;
  z-index: 1;
  inset: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
}

.donut-center strong { font-size: 27px; line-height: 1; color: var(--color-text-primary); }
.donut-center span { margin-top: 6px; font-size: 10px; color: var(--color-text-muted); }

.trade-legend { display: grid; gap: 14px; }
.legend-row { display: grid; grid-template-columns: 10px minmax(105px, 1fr) auto; column-gap: 10px; align-items: center; }
.legend-dot { width: 9px; height: 9px; border-radius: 3px; }
.legend-label { display: flex; justify-content: space-between; align-items: center; gap: 12px; }
.legend-label b { font-size: 12px; }
.legend-label small { color: var(--color-text-muted); font-size: 10px; }
.legend-row > strong { color: var(--color-text-primary); font-size: 12px; }
.legend-track { grid-column: 2 / 4; height: 5px; margin-top: 5px; background: var(--color-bg-tertiary); border-radius: 5px; overflow: hidden; }
.legend-track i { display: block; height: 100%; border-radius: inherit; }

.analytics-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 18px;
  padding-top: 13px;
  border-top: 1px solid var(--color-border);
}

.analytics-note {
  flex: 1;
  color: var(--color-text-muted);
  font-size: 9px;
  line-height: 1.5;
}

.catalog-register-button { flex-shrink:0;padding:9px 14px;font-size:11px; }

.instructor-course-list {
  display: grid;
  gap: 18px;
}

.instructor-course-card {
  background: var(--color-bg-primary);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  padding: 22px;
  box-shadow: var(--shadow-sm);
}

.course-card-top {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 18px;
}

.course-title {
  font-size: 18px;
  font-weight: 700;
  margin-bottom: 8px;
}

.course-desc {
  font-size: 14px;
  color: var(--color-text-secondary);
  line-height: 1.5;
  white-space: pre-line;
}

.status-badge {
  display: inline-flex;
  align-items: center;
  white-space: nowrap;
  border-radius: 999px;
  padding: 6px 10px;
  font-size: 12px;
  font-weight: 600;
}

.status-active {
  background: #eaf8ef;
  color: #0f8a3b;
}

.status-inactive {
  background: #f3f4f6;
  color: #6b7280;
}

.availability-control { display:flex;align-items:center;gap:11px;flex-shrink:0;padding:8px 10px;border:1px solid #cfe5d7;border-radius:11px;background:#f4faf6;cursor:pointer;transition:var(--transition); }
.availability-control.soldout { border-color:#e0e3e5;background:#f6f7f8; }
.availability-copy { display:flex;min-width:82px;flex-direction:column;text-align:right; }
.availability-copy b { color:#277258;font-size:11px; }
.availability-copy small { margin-top:2px;color:var(--color-text-muted);font-size:8px; }
.availability-control.soldout .availability-copy b { color:#68727a; }
.availability-control input { position:absolute;width:1px;height:1px;opacity:0;pointer-events:none; }
.switch-track { position:relative;width:42px;height:23px;flex-shrink:0;border-radius:999px;background:#a8afb4;transition:background .2s ease; }
.switch-track i { position:absolute;left:3px;top:3px;width:17px;height:17px;border-radius:50%;background:#fff;box-shadow:0 2px 5px rgba(0,0,0,.18);transition:transform .2s ease; }
.availability-control input:checked + .switch-track { background:#65b58b; }
.availability-control input:checked + .switch-track i { transform:translateX(19px); }
.availability-control input:focus-visible + .switch-track { outline:3px solid var(--color-primary-light);outline-offset:2px; }
.availability-control:has(input:disabled) { cursor:wait;opacity:.65; }

.course-meta-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 12px;
  margin-bottom: 18px;
}

.meta-box {
  background: var(--color-bg-secondary);
  border-radius: var(--radius-md);
  padding: 14px;
}

.meta-label {
  font-size: 12px;
  color: var(--color-text-muted);
  margin-bottom: 6px;
}

.meta-value {
  font-size: 15px;
  font-weight: 600;
  color: var(--color-text-primary);
}

.course-card-actions {
  display: flex;
  justify-content: flex-end;
}

.action-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  text-decoration: none;
  border-radius: var(--radius-md);
  padding: 10px 16px;
  font-size: 14px;
  font-weight: 600;
  transition: var(--transition);
}

.action-primary {
  background: var(--color-primary);
  color: white;
}

.action-primary:hover {
  opacity: 0.92;
}

.empty-text {
  color: var(--color-text-muted);
  font-size: 14px;
}

.status-error { margin-bottom:14px;padding:10px 12px;border:1px solid #efcccc;border-radius:9px;background:#fff6f6;color:#a53b3b;font-size:11px;font-weight:600; }
.empty-catalog { display:flex;align-items:center;justify-content:space-between;gap:16px;padding:22px;border:1px dashed var(--color-border-hover);border-radius:var(--radius-lg);background:#fff; }

@keyframes shimmer {
  to {
    background-position: -200% 0;
  }
}

@media (max-width: 992px) {
  .loading-row,
  .course-meta-grid {
    grid-template-columns: 1fr;
  }

  .analytics-body { grid-template-columns: 170px 1fr; gap: 20px; }
  .catalog-summary-panel { grid-column: 1 / -1; }
}

@media (max-width: 640px) {
  .profile-card {
    flex-direction: column;
    align-items: flex-start;
  }

  .analytics-heading { flex-direction: column; }
  .top-product-chip { width: 100%; }
  .analytics-body { grid-template-columns: 1fr; }
  .catalog-summary-panel { grid-column: auto; }

  .course-card-top {
    flex-direction: column;
  }

  .summary-cards {
    grid-template-columns: 1fr;
  }

  .analytics-footer { align-items:flex-start;flex-direction:column; }
  .catalog-register-button { width:100%;justify-content:center; }
  .availability-control { width:100%;justify-content:space-between; }
}
</style>
