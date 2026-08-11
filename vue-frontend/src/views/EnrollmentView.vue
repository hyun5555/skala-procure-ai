<template>
  <div class="page-wrapper">
    <div class="view-content" :inert="selectedEnrollment || undefined" :aria-hidden="selectedEnrollment ? 'true' : undefined">
      <AppHeader />
      <main class="main-content">
        <section class="account-section fade-in-up" aria-labelledby="account-heading">
          <div class="section-heading">
            <span>MY INFO</span>
            <div>
              <h1 id="account-heading">나의 정보</h1>
              <p>현재 로그인한 구매기업 계정입니다.</p>
            </div>
          </div>
          <div class="profile-card">
            <div class="profile-avatar">{{ auth.user?.name?.charAt(0) || '?' }}</div>
            <div class="profile-info">
              <strong>{{ auth.user?.name || '구매기업' }}</strong>
              <span>{{ auth.user?.email || '-' }}</span>
            </div>
            <span class="buyer-badge">구매기업</span>
          </div>
        </section>

        <div class="page-head"><span>ORDER MANAGEMENT</span><h2 class="page-title">나의 발주 내역</h2><p>발주 접수부터 결제, 생산·납품, 품질 데이터 반영까지 확인합니다.</p></div>

        <div v-if="loading" class="loading-center">
          <div class="spinner"></div>
        </div>

        <div v-else-if="enrollments.length" class="enrollment-list fade-in">
          <div v-for="item in enrollments" :key="item.id" class="enrollment-card">
            <div class="enroll-info">
              <span class="badge" :class="getBadge(item.course?.category)">
                {{ item.course?.category }}
              </span>
              <h3 class="enroll-title">{{ item.course?.title }}</h3>
              <p class="enroll-instructor">
                <span>공급기업: {{ item.course?.instructorName || '공급기업' }}</span>
                <span v-if="item.orderRequest?.notes" class="request-note">요청사항: {{ item.orderRequest.notes }}</span>
              </p>
              <div v-if="item.orderRequest" class="request-summary">
                <span>수량 {{ Number(item.orderRequest.quantity).toLocaleString() }}{{ item.orderRequest.unit }}</span>
                <span>희망 납품일 {{ item.orderRequest.deliveryDate }}</span>
                <span>납품 장소 {{ item.orderRequest.deliveryPlace }}</span>
                <span v-if="item.orderRequest.contactName || item.orderRequest.contactPhone">담당자 {{ [item.orderRequest.contactName, item.orderRequest.contactPhone].filter(Boolean).join(' · ') }}</span>
              </div>
            </div>

            <div class="enroll-status">
              <span
                :class="[
                  'status-badge',
                  item.status === 'ACTIVE' ? 'status-active' : 'status-pending'
                ]"
              >
                {{ item.status === 'ACTIVE' ? '주문 확정' : '결제 대기' }}
              </span>
              <button type="button" class="btn btn-ghost btn-sm" @click="openOrderDetail(item)">
                발주 상세
              </button>
            </div>
          </div>
        </div>

        <div v-else class="empty-state">
          <p class="empty-icon">📭</p>
          <p>진행 중인 발주가 없습니다.</p>
          <router-link to="/courses" class="btn btn-primary" style="margin-top:16px;">
            공급기업 찾기
          </router-link>
        </div>
      </main>
      <AppFooter />
    </div>

    <Teleport to="body">
      <div v-if="selectedEnrollment" class="detail-backdrop" @click.self="closeOrderDetail">
        <section class="detail-modal" role="dialog" aria-modal="true" aria-labelledby="detail-modal-title">
          <div class="detail-head">
            <div><span>ORDER DETAIL</span><h2 id="detail-modal-title">발주 상세</h2></div>
            <button ref="detailCloseButton" type="button" class="detail-close" aria-label="발주 상세 닫기" @click="closeOrderDetail">×</button>
          </div>

          <div class="detail-product">
            <div><span>발주 품목</span><strong>{{ selectedEnrollment.course?.title }}</strong><small>{{ selectedEnrollment.course?.instructorName || '공급기업' }}</small></div>
            <span :class="['status-badge',selectedEnrollment.status==='ACTIVE'?'status-active':'status-pending']">{{ selectedEnrollment.status==='ACTIVE'?'주문 확정':'결제 대기' }}</span>
          </div>

          <div v-if="selectedEnrollment.orderRequest" class="detail-fields">
            <article><span>발주 수량</span><strong>{{ Number(selectedEnrollment.orderRequest.quantity).toLocaleString() }}{{ selectedEnrollment.orderRequest.unit }}</strong></article>
            <article><span>예상 견적 금액</span><strong>{{ Number(selectedEnrollment.orderRequest.estimatedTotal || 0).toLocaleString() }}원</strong></article>
            <article><span>희망 납품일</span><strong>{{ selectedEnrollment.orderRequest.deliveryDate }}</strong></article>
            <article><span>납품 장소</span><strong>{{ selectedEnrollment.orderRequest.deliveryPlace }}</strong></article>
            <article class="detail-wide"><span>요청사항</span><p>{{ selectedEnrollment.orderRequest.notes || '별도 요청사항 없음' }}</p></article>
            <div class="detail-section-title detail-wide">담당자 정보</div>
            <article><span>담당자명</span><strong>{{ selectedEnrollment.orderRequest.contactName }}</strong></article>
            <article><span>연락처</span><strong>{{ selectedEnrollment.orderRequest.contactPhone }}</strong></article>
          </div>
          <div v-else class="missing-detail"><b>입력 상세정보가 없습니다.</b><p>현재 서버에 저장된 기존 발주는 품목과 주문 상태만 확인할 수 있습니다.</p></div>

          <div class="detail-meta"><span>발주번호 #{{ selectedEnrollment.id }}</span><span>접수일 {{ formatCreatedAt(selectedEnrollment.createdAt) }}</span></div>
          <div class="detail-actions"><button type="button" class="btn btn-primary" @click="closeOrderDetail">확인</button></div>
        </section>
      </div>
    </Teleport>
  </div>
</template>

<script setup>
import { ref, computed, nextTick, onBeforeUnmount, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import AppHeader from '@/components/AppHeader.vue'
import AppFooter from '@/components/AppFooter.vue'
import { enrollmentApi } from '@/api/enrollment.js'
import { useAuthStore } from '@/store/auth.js'
import { useCourseStore } from '@/store/course.js'
import { getDemoEnrollments } from '@/data/demo.js'

const router = useRouter()
const auth = useAuthStore()
const courseStore = useCourseStore()

const enrollments = ref([])
const loading = ref(true)
const selectedEnrollment = ref(null)
const detailCloseButton = ref(null)
let bodyOverflowBeforeModal = ''
let focusedElementBeforeModal = null

const isInstructor = computed(() => auth.user?.role === 'INSTRUCTOR')

const categoryConfig = {
  '파형강관': { bg: 'thumb-teal', badge: 'badge-teal' }, '파형강관이음관': { bg: 'thumb-teal', badge: 'badge-teal' },
  '피복강관': { bg: 'thumb-blue', badge: 'badge-blue' }, '피복강관이음': { bg: 'thumb-blue', badge: 'badge-blue' },
  '스틸파일': { bg: 'thumb-purple', badge: 'badge-purple' }, '주철관': { bg: 'thumb-purple', badge: 'badge-purple' },
  '주철제관이음': { bg: 'thumb-pink', badge: 'badge-pink' }, '기타 관류': { bg: 'thumb-gray', badge: 'badge-gray' }
}

function getBadge(cat) {
  return categoryConfig[cat]?.badge || 'badge-gray'
}

function normalizeEnrollment(item) {
  return {
    ...item,
    course: courseStore.normalizeCourse(item.course),
    orderRequest: item.orderRequest || null
  }
}

function openOrderDetail(item) {
  selectedEnrollment.value = item
  focusedElementBeforeModal = document.activeElement
  bodyOverflowBeforeModal = document.body.style.overflow
  document.body.style.overflow = 'hidden'
  nextTick(() => detailCloseButton.value?.focus())
}

function closeOrderDetail() {
  selectedEnrollment.value = null
  document.body.style.overflow = bodyOverflowBeforeModal
  nextTick(() => focusedElementBeforeModal?.focus())
}

function handleEscape(event) {
  if (event.key === 'Escape' && selectedEnrollment.value) closeOrderDetail()
}

function formatCreatedAt(value) {
  if (!value) return '-'
  return new Date(value).toLocaleString('ko-KR', { dateStyle: 'medium', timeStyle: 'short' })
}

onMounted(async () => {
  // 강사는 이 페이지 접근 불가 → 마이페이지로 이동
  if (isInstructor.value) {
    console.warn('[EnrollmentView] instructor tried to access /enrollments, redirect to /mypage')
    router.replace('/mypage')
    return
  }

  try {
    if (auth.isDemo) {
      enrollments.value = getDemoEnrollments().map(normalizeEnrollment)
      return
    }
    const res = await enrollmentApi.getMyEnrollments()
    console.log('[EnrollmentView] my enrollments response:', res.data)

    if (Array.isArray(res.data?.data)) {
      enrollments.value = res.data.data.map(normalizeEnrollment)
    } else if (Array.isArray(res.data)) {
      enrollments.value = res.data.map(normalizeEnrollment)
    } else {
      enrollments.value = []
    }
  } catch (error) {
    console.error('[EnrollmentView] failed to load enrollments:', error)
    enrollments.value = []
  } finally {
    loading.value = false
  }
})

onMounted(() => document.addEventListener('keydown', handleEscape))
onBeforeUnmount(() => {
  document.removeEventListener('keydown', handleEscape)
  if (selectedEnrollment.value) document.body.style.overflow = bodyOverflowBeforeModal
})
</script>

<style scoped>
.page-wrapper,
.view-content {
  min-height: 100vh;
  background: var(--color-bg-secondary);
}

.view-content {
  display: flex;
  flex-direction: column;
}

.request-summary {
  display: flex;
  flex-wrap: wrap;
  gap: 4px 12px;
  margin-top: 8px;
  font-size: 11px;
  color: var(--color-text-secondary);
}

.request-summary span:not(:last-child)::after {
  content: '·';
  margin-left: 12px;
  color: var(--color-border-hover);
}

.main-content {
  flex: 1;
  width: 100%;
  max-width: 1100px;
  min-width: 0;
  margin: 0 auto;
  padding: 42px 24px 80px;
}

.account-section { margin-bottom: 34px; }
.section-heading { display:flex;align-items:center;gap:13px;margin-bottom:14px; }
.section-heading>span { display:grid;place-items:center;width:42px;height:42px;border-radius:11px;background:var(--color-primary-light);color:var(--color-accent-dark);font-size:8px;font-weight:800;letter-spacing:.08em;text-align:center;line-height:1.25; }
.section-heading h1 { font-size:20px;line-height:1.25;letter-spacing:-.025em; }
.section-heading p { margin-top:2px;font-size:11px;color:var(--color-text-muted); }
.profile-card { display:flex;align-items:center;gap:15px;padding:20px 22px;border:1px solid var(--color-border);border-radius:var(--radius-lg);background:#fff;box-shadow:var(--shadow-sm); }
.profile-avatar { display:grid;place-items:center;width:48px;height:48px;flex-shrink:0;border-radius:50%;background:var(--color-primary-light);color:var(--color-accent-dark);font-size:17px;font-weight:800; }
.profile-info { display:flex;flex:1;min-width:0;flex-direction:column; }
.profile-info strong { font-size:16px; }
.profile-info span { font-size:11px;color:var(--color-text-muted);overflow-wrap:anywhere; }
.buyer-badge { padding:5px 11px;border-radius:999px;background:var(--color-primary-light);color:var(--color-accent-dark);font-size:10px;font-weight:700; }

.page-title {
  font-size: 22px;
  font-weight: 700;
  margin-bottom: 24px;
}
.page-head { margin-bottom:24px; }
.page-head>span { font-size:9px; font-weight:800; letter-spacing:.15em; color:var(--color-primary); }
.page-head p { font-size:12px; color:var(--color-text-muted); margin-top:5px; }

.enrollment-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.enrollment-card {
  display: flex;
  align-items: center;
  gap: 16px;
  background: var(--color-bg-primary);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  padding: 16px;
  transition: var(--transition);
}

.enrollment-card:hover {
  box-shadow: var(--shadow-sm);
}

.enroll-info {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.enroll-info > .badge {
  width: fit-content;
  align-self: flex-start;
}

.enroll-title {
  font-size: 15px;
  font-weight: 600;
}

.enroll-instructor {
  font-size: 13px;
  color: var(--color-text-secondary);
}

.enroll-instructor .request-note::before {
  content: '·';
  margin: 0 9px;
  color: var(--color-border-hover);
}

.request-note {
  color: var(--color-text-primary);
}

.enroll-status {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 8px;
}

.status-badge {
  padding: 4px 12px;
  border-radius: 20px;
  font-size: 12px;
  font-weight: 500;
}

.status-active {
  background: #E1F5EE;
  color: #0F6E56;
}

.status-pending {
  background: #FAEEDA;
  color: #854F0B;
}

.btn-sm {
  padding: 7px 14px;
  font-size: 13px;
}

.empty-state {
  text-align: center;
  padding: 80px 0;
  color: var(--color-text-muted);
}

.empty-icon {
  font-size: 48px;
  margin-bottom: 12px;
}

.loading-center {
  display: flex;
  justify-content: center;
  padding: 80px 0;
}

.spinner {
  width: 36px;
  height: 36px;
  border: 3px solid var(--color-border);
  border-top-color: var(--color-primary);
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

.detail-backdrop {
  position: fixed;
  inset: 0;
  z-index: 1000;
  display: grid;
  place-items: center;
  padding: 20px;
  background: rgba(11,31,27,.58);
  backdrop-filter: blur(3px);
  overscroll-behavior: contain;
}

.detail-modal {
  width: min(620px,100%);
  max-height: calc(100vh - 40px);
  overflow-y: auto;
  scrollbar-width: none;
  background: #fff;
  border: 1px solid var(--color-border);
  border-radius: 18px;
  box-shadow: var(--shadow-lg);
  animation: detailIn .2s ease both;
}

.detail-modal::-webkit-scrollbar { display:none; }
.detail-head { display:flex;align-items:flex-start;justify-content:space-between;padding:22px 24px 17px;border-bottom:1px solid var(--color-border); }
.detail-head>div>span { font-size:9px;font-weight:800;letter-spacing:.15em;color:var(--color-primary); }
.detail-head h2 { margin-top:2px;font-size:22px;letter-spacing:-.03em; }
.detail-close { display:grid;place-items:center;width:32px;height:32px;border-radius:50%;background:var(--color-bg-secondary);color:var(--color-text-secondary);font-size:22px;line-height:1; }
.detail-close:hover { background:var(--color-bg-tertiary); }
.detail-product { display:flex;align-items:center;justify-content:space-between;gap:20px;margin:18px 24px;padding:15px 16px;border-radius:11px;background:var(--color-bg-secondary); }
.detail-product>div { min-width:0; }
.detail-product>div>span,.detail-product small { display:block;font-size:10px;color:var(--color-text-muted); }
.detail-product strong { display:block;margin:3px 0;font-size:14px; }
.detail-fields { display:grid;grid-template-columns:1fr 1fr;gap:12px;padding:0 24px; }
.detail-fields article { padding:13px 14px;border:1px solid var(--color-border);border-radius:9px; }
.detail-fields article>span { display:block;margin-bottom:4px;font-size:10px;color:var(--color-text-muted); }
.detail-fields article strong,.detail-fields article p { font-size:13px;line-height:1.55;overflow-wrap:anywhere; }
.detail-wide { grid-column:1/-1; }
.detail-section-title { margin-top:3px;padding-top:14px;border-top:1px solid var(--color-border);font-size:12px;font-weight:800; }
.missing-detail { margin:0 24px;padding:26px;border:1px dashed var(--color-border-hover);border-radius:10px;text-align:center;background:var(--color-bg-secondary); }
.missing-detail b { font-size:13px; }
.missing-detail p { margin-top:5px;font-size:11px;color:var(--color-text-muted); }
.detail-meta { display:flex;gap:14px;margin:17px 24px 0;padding-top:12px;border-top:1px solid var(--color-border);font-size:10px;color:var(--color-text-muted); }
.detail-actions { display:flex;justify-content:flex-end;padding:17px 24px 22px; }
.detail-actions .btn { min-width:100px;justify-content:center; }

@keyframes detailIn { from{opacity:0;transform:translateY(10px) scale(.985)} to{opacity:1;transform:translateY(0) scale(1)} }

@media(max-width:700px) {
  .main-content { padding:28px 16px 60px; }
  .enrollment-card { align-items:flex-start;flex-wrap:wrap; }
  .enroll-status { width:100%;flex-direction:row;align-items:center;justify-content:flex-end; }
  .detail-backdrop { align-items:end;padding:10px; }
  .detail-modal { max-height:calc(100vh - 20px);border-radius:18px 18px 10px 10px; }
  .detail-fields { grid-template-columns:1fr;padding:0 18px; }
  .detail-wide { grid-column:auto; }
  .detail-head,.detail-actions { padding-left:18px;padding-right:18px; }
  .detail-product,.missing-detail,.detail-meta { margin-left:18px;margin-right:18px; }
  .profile-card { align-items:flex-start;flex-wrap:wrap;padding:18px; }
  .profile-info { min-width:calc(100% - 64px); }
  .buyer-badge { margin-left:64px; }
}
.page-head>span,.detail-head>div>span { color:var(--color-accent-dark); }
.spinner { border-top-color:var(--color-accent); }
.detail-backdrop { background:rgba(20,26,31,.56); }
</style>
