<template>
  <div class="page-wrapper"><AppHeader />
    <main v-if="course" class="main-content">
      <router-link :to="backTarget" class="back-link">← {{ backLabel }}</router-link>
      <section class="detail-grid">
        <div>
          <div class="title-row"><span class="material-badge">{{ course.category }}</span><span class="verified">✓ 조달 품목 등록</span></div>
          <h1>{{ course.title }}</h1>
          <p class="supplier-name">{{ displayInstructorName }} · 누적 거래 {{ displayEnrollmentCount }}건</p>
          <div class="reason-box"><span>추천 근거</span><p>{{ recommendationReason }}</p></div>

          <section class="capability-section"><div class="section-heading"><h2>품목·납품 정보</h2><small>공급업체 CSV 데이터 기준</small></div>
            <div class="spec-grid">
              <article v-for="spec in specItems" :key="spec.label"><span>{{ spec.label }}</span><strong>{{ spec.value || '미등록' }}</strong></article>
            </div>
          </section>
          <section class="description-section"><h2>인증·조달 정보</h2><p>{{ detailText }}</p></section>
          <section class="notice"><b>데이터 활용 안내</b><p>단가·납품일수·인증정보는 공급업체 등록 데이터 기준입니다. 실제 계약 전 최신 계약기간과 납품조건을 확인해 주세요. 거래 후 품질지표는 별도로 누적됩니다.</p></section>
        </div>

        <aside class="order-card">
          <span class="card-label">REGISTERED UNIT PRICE</span><div class="price"><strong>{{ displayPrice }}</strong>원 <small>/ {{ specs.unit || '단위' }}</small></div>
          <div class="order-summary"><div><span>결제 방식</span><b>발주 접수 후 자동 결제</b></div><div><span>주문 상태</span><b>{{ statusLabel }}</b></div></div>
          <button class="btn btn-primary btn-full" @click="handlePrimaryAction" :disabled="buttonDisabled">{{ enrolling ? '처리 중...' : buttonLabel }}</button>
          <p v-if="enrollError" class="error-msg">{{ enrollError }}</p><p class="helper-text">{{ helperText }}</p>
          <ol class="process-list"><li><b>1</b>견적·발주 요청</li><li><b>2</b>결제 및 주문 확정</li><li><b>3</b>생산·납품</li><li><b>4</b>품질검사 데이터 반영</li></ol>
        </aside>
      </section>
    </main>
    <div v-else-if="loading" class="loading-center"><div class="spinner"></div></div>
    <div v-else class="loading-center">조달 품목 정보를 불러오지 못했습니다.</div>
  </div>
</template>

<script setup>
import { computed,onMounted,ref } from 'vue'
import { useRoute,useRouter } from 'vue-router'
import AppHeader from '@/components/AppHeader.vue'
import { useCourseStore } from '@/store/course.js'
import { enrollmentApi } from '@/api/enrollment.js'
import { useAuthStore } from '@/store/auth.js'
import { parseCapability } from '@/utils/procurement.js'
import { addDemoEnrollment, getDemoEnrollments } from '@/data/demo.js'
const route=useRoute(),router=useRouter(),courseStore=useCourseStore(),auth=useAuthStore()
const enrolling=ref(false),enrollError=ref(''),enrollmentStatus=ref('NONE')
const course=computed(()=>courseStore.selectedCourse),loading=computed(()=>courseStore.loading),isInstructor=computed(()=>auth.user?.role==='INSTRUCTOR')
const backTarget=computed(()=>isInstructor.value?'/mypage':'/courses')
const backLabel=computed(()=>isInstructor.value?'품목 대시보드로 돌아가기':'매칭 결과로 돌아가기')
const specs=computed(()=>parseCapability(course.value?.description))
const displayInstructorName=computed(()=>course.value?.instructorName||course.value?.instructor?.name||'공급기업')
const displayEnrollmentCount=computed(()=>Number(course.value?.enrollmentCount??course.value?.enrollment_count??0).toLocaleString())
const displayPrice=computed(()=>Number(course.value?.price??0).toLocaleString())
const specItems=computed(()=>[
  {label:'품목명',value:specs.value.itemName},{label:'규격',value:specs.value.specification},{label:'단위',value:specs.value.unit},
  {label:'공급업체 소재지',value:specs.value.location},{label:'납품일수',value:specs.value.deliveryDays},{label:'공급지역',value:specs.value.supplyRegion}
])
const detailText=computed(()=>[specs.value.certification&&`인증: ${specs.value.certification}`,`우수제품: ${specs.value.excellent==='Y'?'해당':'해당 없음'}`,`MAS: ${specs.value.mas==='Y'?'등록':'미등록'}`,specs.value.contractPeriod&&`계약기간: ${specs.value.contractPeriod}`,specs.value.deliveryTerms&&`인도조건: ${specs.value.deliveryTerms}`].filter(Boolean).join(' · '))
const recommendationReason=computed(()=>[specs.value.deliveryDays&&`${specs.value.deliveryDays} 납품`,specs.value.supplyRegion,specs.value.certification&&specs.value.certification.split(',').slice(0,2).join('·'),specs.value.excellent==='Y'&&'우수제품',specs.value.mas==='Y'&&'MAS 등록'].filter(Boolean).join(' · ')||'등록된 품목과 납품조건을 기준으로 비교할 수 있습니다.')
const statusLabel=computed(()=>enrollmentStatus.value==='ACTIVE'?'주문 확정':enrollmentStatus.value==='PENDING'?'결제 처리 중':'요청 전')
const buttonLabel=computed(()=>isInstructor.value?'공급기업 계정은 발주 불가':enrollmentStatus.value==='ACTIVE'?'내 발주 내역 보기':enrollmentStatus.value==='PENDING'?'발주 접수 완료':'견적 요청 및 발주')
const buttonDisabled=computed(()=>enrolling.value||isInstructor.value||enrollmentStatus.value==='PENDING')
const helperText=computed(()=>isInstructor.value?'공급기업 계정에서는 구매 발주를 생성할 수 없습니다.':enrollmentStatus.value==='ACTIVE'?'결제가 완료되어 주문이 확정되었습니다.':enrollmentStatus.value==='PENDING'?'발주가 접수되어 결제를 처리하고 있습니다.':'요청 시 발주 접수와 결제가 연속으로 처리됩니다.')
async function loadStatus(){if(!auth.user?.id||!course.value?.id||isInstructor.value)return;if(auth.isDemo){const found=getDemoEnrollments().find(v=>Number(v.courseId)===Number(course.value.id));enrollmentStatus.value=found?'ACTIVE':'NONE';return}try{const res=await enrollmentApi.getMyEnrollments();const list=Array.isArray(res.data?.data)?res.data.data:Array.isArray(res.data)?res.data:[];const found=list.find(v=>Number(v.courseId)===Number(course.value.id));enrollmentStatus.value=found?(found.status==='ACTIVE'?'ACTIVE':'PENDING'):'NONE'}catch{enrollmentStatus.value='NONE'}}
async function handlePrimaryAction(){enrollError.value='';if(enrollmentStatus.value==='ACTIVE')return router.push('/enrollments');if(!course.value?.id||isInstructor.value)return;enrolling.value=true;try{if(auth.isDemo){addDemoEnrollment(course.value);enrollmentStatus.value='ACTIVE'}else{await enrollmentApi.enroll(course.value.id);enrollmentStatus.value='PENDING'}}catch(e){enrollError.value=e.response?.data?.message||'견적·발주 요청에 실패했습니다.'}finally{enrolling.value=false}}
onMounted(async()=>{await courseStore.fetchCourse(route.params.id);await loadStatus()})
</script>

<style scoped>
.page-wrapper{min-height:100vh;background:var(--color-bg-secondary)}.main-content{max-width:1100px;margin:0 auto;padding:34px 24px 75px}.back-link{font-size:12px;color:var(--color-text-secondary)}.detail-grid{display:grid;grid-template-columns:1fr 330px;gap:42px;margin-top:30px}.title-row{display:flex;gap:8px;align-items:center}.material-badge,.verified{font-size:10px;font-weight:700;padding:5px 9px;border-radius:20px}.material-badge{background:var(--color-primary);color:#fff}.verified{background:var(--color-primary-light);color:var(--color-primary)}h1{font-size:34px;letter-spacing:-.04em;margin:13px 0 5px}.supplier-name{font-size:13px;color:var(--color-text-muted)}.reason-box{margin-top:24px;padding:16px 18px;border-left:3px solid var(--color-secondary);background:#fff8f1}.reason-box span{font-size:9px;font-weight:800;letter-spacing:.12em;color:#ad5c19}.reason-box p{margin-top:5px;font-size:13px;font-weight:600}.capability-section,.description-section{margin-top:34px}.section-heading{display:flex;align-items:center;justify-content:space-between;border-bottom:1px solid var(--color-border);padding-bottom:10px}.section-heading h2,.description-section h2{font-size:17px}.section-heading small{font-size:9px;color:var(--color-text-muted)}.spec-grid{display:grid;grid-template-columns:repeat(3,1fr);border:1px solid var(--color-border);border-radius:12px;overflow:hidden;margin-top:14px}.spec-grid article{padding:17px;border-right:1px solid var(--color-border);border-bottom:1px solid var(--color-border)}.spec-grid article:nth-child(3n){border-right:0}.spec-grid article:nth-child(n+4){border-bottom:0}.spec-grid span{display:block;font-size:10px;color:var(--color-text-muted);margin-bottom:6px}.spec-grid strong{font-size:13px}.description-section p{font-size:13px;line-height:1.7;color:var(--color-text-secondary);margin-top:11px}.notice{margin-top:28px;padding:14px 16px;border:1px solid var(--color-border);border-radius:10px}.notice b{font-size:10px}.notice p{font-size:10px;color:var(--color-text-muted);margin-top:4px}.order-card{position:sticky;top:94px;align-self:start;background:#fff;border:1px solid var(--color-border);border-radius:16px;padding:24px;box-shadow:var(--shadow-md)}.card-label{font-size:9px;letter-spacing:.14em;color:var(--color-text-muted)}.price{margin:8px 0 20px}.price strong{font-size:30px;color:var(--color-primary)}.price small{font-size:10px;color:var(--color-text-muted)}.order-summary{padding:13px;background:var(--color-bg-secondary);border-radius:9px;margin-bottom:14px}.order-summary div{display:flex;justify-content:space-between;font-size:10px;padding:4px}.order-summary span{color:var(--color-text-muted)}.btn-full{width:100%;justify-content:center;padding:13px}.helper-text,.error-msg{font-size:10px;line-height:1.5;margin-top:9px;color:var(--color-text-muted)}.error-msg{color:#b91c1c}.process-list{list-style:none;border-top:1px solid var(--color-border);margin-top:18px;padding-top:13px}.process-list li{display:flex;align-items:center;gap:8px;font-size:10px;color:var(--color-text-secondary);padding:5px}.process-list b{display:grid;place-items:center;width:18px;height:18px;border-radius:50%;background:var(--color-bg-tertiary);color:var(--color-primary);font-size:8px}.loading-center{display:flex;justify-content:center;padding:100px;color:var(--color-text-muted)}.spinner{width:36px;height:36px;border:3px solid var(--color-border);border-top-color:var(--color-primary);border-radius:50%;animation:spin .8s linear infinite}@keyframes spin{to{transform:rotate(360deg)}}@media(max-width:800px){.detail-grid{grid-template-columns:1fr}.order-card{position:static}.spec-grid{grid-template-columns:1fr 1fr}.spec-grid article:nth-child(n){border-right:1px solid var(--color-border);border-bottom:1px solid var(--color-border)}h1{font-size:28px}}
</style>
