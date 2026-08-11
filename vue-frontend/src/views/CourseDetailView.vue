<template>
  <div class="page-wrapper">
    <div class="page-content" :inert="showOrderModal || undefined" :aria-hidden="showOrderModal ? 'true' : undefined">
      <AppHeader />
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
      <AppFooter />
    </div>

    <Transition name="toast">
      <div v-if="orderSuccessMessage" class="success-toast" role="status"><b>✓</b>{{ orderSuccessMessage }}</div>
    </Transition>

    <Teleport to="body">
      <div v-if="showOrderModal" class="modal-backdrop" @click.self="closeOrderModal">
        <section class="order-modal" role="dialog" aria-modal="true" aria-labelledby="order-modal-title">
          <div class="modal-head">
            <div><span>REQUEST FOR QUOTATION</span><h2 id="order-modal-title">견적 요청 및 발주</h2></div>
            <button type="button" class="modal-close" aria-label="모달 닫기" @click="closeOrderModal">×</button>
          </div>

          <div class="product-summary">
            <div><span>발주 품목</span><strong>{{ course?.title }}</strong><small>{{ displayInstructorName }}</small></div>
            <div class="unit-price"><span>등록 단가</span><strong>{{ displayPrice }}원</strong><small>/ {{ specs.unit || '단위' }}</small></div>
          </div>

          <form class="order-form" @submit.prevent="submitOrder">
            <div class="form-grid">
              <label>
                <span>발주 수량 <em>*</em></span>
                <div class="input-with-unit"><input ref="orderQuantityInput" v-model.number="orderForm.quantity" type="number" min="1" step="1" inputmode="numeric" required><b>{{ specs.unit || '개' }}</b></div>
              </label>
              <label>
                <span>희망 납품일 <em>*</em></span>
                <input v-model="orderForm.deliveryDate" type="date" :min="minimumDeliveryDate" required>
              </label>
              <label class="wide-field">
                <span>납품 장소 <em>*</em></span>
                <input v-model.trim="orderForm.deliveryPlace" type="text" maxlength="100" placeholder="예: 서울특별시 강남구 테헤란로 123, 현장 자재창고" required>
              </label>
              <label class="wide-field">
                <span>요청사항</span>
                <textarea v-model.trim="orderForm.notes" rows="3" maxlength="300" placeholder="포장 방식, 하차 방법 등 공급기업이 확인할 내용을 입력해 주세요."></textarea>
                <small class="character-count">{{ orderForm.notes.length }}/300</small>
              </label>

              <div class="wide-field contact-heading"><strong>담당자 정보 <em>*</em></strong><span>필수 입력</span></div>
              <label>
                <span>담당자명 <em>*</em></span>
                <input v-model.trim="orderForm.contactName" type="text" maxlength="30" placeholder="예: 홍길동" required>
              </label>
              <label>
                <span>연락처 <em>*</em></span>
                <input v-model.trim="orderForm.contactPhone" type="tel" maxlength="30" placeholder="예: 010-1234-5678" required>
              </label>
            </div>

            <div class="quote-summary">
              <div><span>예상 견적 금액</span><small>등록 단가 × 발주 수량</small></div>
              <strong>{{ estimatedTotal.toLocaleString() }}원</strong>
            </div>
            <p class="quote-note">실제 계약 금액과 납품 가능 여부는 공급기업 확인 과정에서 달라질 수 있습니다.</p>
            <p v-if="modalError" class="modal-error" role="alert">{{ modalError }}</p>

            <div class="modal-actions">
              <button type="button" class="btn btn-ghost" :disabled="enrolling" @click="closeOrderModal">취소</button>
              <button type="submit" class="btn btn-primary" :disabled="enrolling">{{ enrolling ? '발주 등록 중...' : '발주 요청' }}</button>
            </div>
          </form>
        </section>
      </div>
    </Teleport>
  </div>
</template>

<script setup>
import { computed,nextTick,onBeforeUnmount,onMounted,reactive,ref } from 'vue'
import { useRoute,useRouter } from 'vue-router'
import AppHeader from '@/components/AppHeader.vue'
import AppFooter from '@/components/AppFooter.vue'
import { useCourseStore } from '@/store/course.js'
import { enrollmentApi } from '@/api/enrollment.js'
import { useAuthStore } from '@/store/auth.js'
import { parseCapability } from '@/utils/procurement.js'
import { addDemoEnrollment, getDemoEnrollments } from '@/data/demo.js'
const route=useRoute(),router=useRouter(),courseStore=useCourseStore(),auth=useAuthStore()
const enrolling=ref(false),enrollError=ref(''),modalError=ref(''),enrollmentStatus=ref('NONE'),showOrderModal=ref(false),orderSuccessMessage=ref('')
const orderQuantityInput=ref(null)
const orderForm=reactive({quantity:1,deliveryDate:'',deliveryPlace:'',notes:'',contactName:'',contactPhone:''})
let bodyOverflowBeforeModal='',focusedElementBeforeModal=null,successTimer=null
const course=computed(()=>courseStore.selectedCourse),loading=computed(()=>courseStore.loading),isInstructor=computed(()=>auth.user?.role==='INSTRUCTOR')
const backTarget=computed(()=>isInstructor.value?'/mypage':'/courses')
const backLabel=computed(()=>isInstructor.value?'품목 대시보드로 돌아가기':'매칭 결과로 돌아가기')
const specs=computed(()=>parseCapability(course.value?.description))
const displayInstructorName=computed(()=>course.value?.instructorName||course.value?.instructor?.name||'공급기업')
const displayEnrollmentCount=computed(()=>Number(course.value?.enrollmentCount??course.value?.enrollment_count??0).toLocaleString())
const displayPrice=computed(()=>Number(course.value?.price??0).toLocaleString())
const estimatedTotal=computed(()=>Math.max(0,Number(course.value?.price??0))*Math.max(0,Number(orderForm.quantity)||0))
const minimumDeliveryDate=computed(()=>formatLocalDate(new Date()))
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
function formatLocalDate(date){const year=date.getFullYear(),month=String(date.getMonth()+1).padStart(2,'0'),day=String(date.getDate()).padStart(2,'0');return `${year}-${month}-${day}`}
function recommendedDeliveryDate(){const days=Number.parseInt(specs.value.deliveryDays,10)||0;const date=new Date();date.setDate(date.getDate()+days);return formatLocalDate(date)}
function lockPage(){bodyOverflowBeforeModal=document.body.style.overflow;document.body.style.overflow='hidden'}
function unlockPage(){document.body.style.overflow=bodyOverflowBeforeModal}
function openOrderModal(){modalError.value='';enrollError.value='';orderForm.quantity=1;orderForm.deliveryDate=recommendedDeliveryDate();orderForm.deliveryPlace=specs.value.deliveryPlace||'';orderForm.notes='';orderForm.contactName='';orderForm.contactPhone='';focusedElementBeforeModal=document.activeElement;lockPage();showOrderModal.value=true;nextTick(()=>orderQuantityInput.value?.focus())}
function closeOrderModal(){if(enrolling.value)return;showOrderModal.value=false;modalError.value='';unlockPage();nextTick(()=>focusedElementBeforeModal?.focus())}
function handleEscape(event){if(event.key==='Escape'&&showOrderModal.value)closeOrderModal()}
function validateOrder(){if(!Number.isInteger(Number(orderForm.quantity))||Number(orderForm.quantity)<1)return '발주 수량은 1 이상인 정수로 입력해 주세요.';if(!orderForm.deliveryDate)return '희망 납품일을 선택해 주세요.';if(orderForm.deliveryDate<minimumDeliveryDate.value)return '희망 납품일은 오늘 이후로 선택해 주세요.';if(!orderForm.deliveryPlace.trim())return '납품 장소를 입력해 주세요.';if(!orderForm.contactName.trim())return '담당자명을 입력해 주세요.';if(!orderForm.contactPhone.trim())return '담당자 연락처를 입력해 주세요.';return ''}
function showOrderSuccess(){orderSuccessMessage.value='발주가 완료되었습니다.';clearTimeout(successTimer);successTimer=setTimeout(()=>{orderSuccessMessage.value=''},3500)}
async function handlePrimaryAction(){enrollError.value='';if(enrollmentStatus.value==='ACTIVE')return router.push('/enrollments');if(!course.value?.id||isInstructor.value)return;openOrderModal()}
async function submitOrder(){modalError.value=validateOrder();if(modalError.value||!course.value?.id)return;enrolling.value=true;const orderRequest={courseId:course.value.id,quantity:Number(orderForm.quantity),unit:specs.value.unit||'개',deliveryDate:orderForm.deliveryDate,deliveryPlace:orderForm.deliveryPlace.trim(),notes:orderForm.notes.trim(),contactName:orderForm.contactName.trim(),contactPhone:orderForm.contactPhone.trim()};try{if(auth.isDemo){addDemoEnrollment(course.value,{...orderRequest,estimatedTotal:estimatedTotal.value});enrollmentStatus.value='ACTIVE'}else{await enrollmentApi.enroll(orderRequest);enrollmentStatus.value='PENDING'}showOrderModal.value=false;unlockPage();showOrderSuccess()}catch(e){modalError.value=e.response?.data?.message||'견적·발주 요청에 실패했습니다.'}finally{enrolling.value=false}}
onMounted(async()=>{document.addEventListener('keydown',handleEscape);await courseStore.fetchCourse(route.params.id);await loadStatus()})
onBeforeUnmount(()=>{document.removeEventListener('keydown',handleEscape);clearTimeout(successTimer);if(showOrderModal.value)unlockPage()})
</script>

<style scoped>
.page-wrapper,.page-content{min-height:100vh;background:var(--color-bg-secondary)}.main-content{max-width:1100px;margin:0 auto;padding:34px 24px 75px}.back-link{font-size:12px;color:var(--color-text-secondary)}.detail-grid{display:grid;grid-template-columns:1fr 330px;gap:42px;margin-top:30px}.title-row{display:flex;gap:8px;align-items:center}.material-badge,.verified{font-size:10px;font-weight:700;padding:5px 9px;border-radius:20px}.material-badge{background:var(--color-primary);color:#fff}.verified{background:var(--color-primary-light);color:var(--color-primary)}h1{font-size:34px;letter-spacing:-.04em;margin:13px 0 5px}.supplier-name{font-size:13px;color:var(--color-text-muted)}.reason-box{margin-top:24px;padding:16px 18px;border-left:3px solid var(--color-secondary);background:#fff8f1}.reason-box span{font-size:9px;font-weight:800;letter-spacing:.12em;color:#ad5c19}.reason-box p{margin-top:5px;font-size:13px;font-weight:600}.capability-section,.description-section{margin-top:34px}.section-heading{display:flex;align-items:center;justify-content:space-between;border-bottom:1px solid var(--color-border);padding-bottom:10px}.section-heading h2,.description-section h2{font-size:17px}.section-heading small{font-size:9px;color:var(--color-text-muted)}.spec-grid{display:grid;grid-template-columns:repeat(3,1fr);border:1px solid var(--color-border);border-radius:12px;overflow:hidden;margin-top:14px}.spec-grid article{padding:17px;border-right:1px solid var(--color-border);border-bottom:1px solid var(--color-border)}.spec-grid article:nth-child(3n){border-right:0}.spec-grid article:nth-child(n+4){border-bottom:0}.spec-grid span{display:block;font-size:10px;color:var(--color-text-muted);margin-bottom:6px}.spec-grid strong{font-size:13px}.description-section p{font-size:13px;line-height:1.7;color:var(--color-text-secondary);margin-top:11px}.notice{margin-top:28px;padding:14px 16px;border:1px solid var(--color-border);border-radius:10px}.notice b{font-size:10px}.notice p{font-size:10px;color:var(--color-text-muted);margin-top:4px}.order-card{position:sticky;top:94px;align-self:start;background:#fff;border:1px solid var(--color-border);border-radius:16px;padding:24px;box-shadow:var(--shadow-md)}.card-label{font-size:9px;letter-spacing:.14em;color:var(--color-text-muted)}.price{margin:8px 0 20px}.price strong{font-size:30px;color:var(--color-primary)}.price small{font-size:10px;color:var(--color-text-muted)}.order-summary{padding:13px;background:var(--color-bg-secondary);border-radius:9px;margin-bottom:14px}.order-summary div{display:flex;justify-content:space-between;font-size:10px;padding:4px}.order-summary span{color:var(--color-text-muted)}.btn-full{width:100%;justify-content:center;padding:13px}.helper-text,.error-msg{font-size:10px;line-height:1.5;margin-top:9px;color:var(--color-text-muted)}.error-msg{color:#b91c1c}.process-list{list-style:none;border-top:1px solid var(--color-border);margin-top:18px;padding-top:13px}.process-list li{display:flex;align-items:center;gap:8px;font-size:10px;color:var(--color-text-secondary);padding:5px}.process-list b{display:grid;place-items:center;width:18px;height:18px;border-radius:50%;background:var(--color-bg-tertiary);color:var(--color-primary);font-size:8px}.loading-center{display:flex;justify-content:center;padding:100px;color:var(--color-text-muted)}.spinner{width:36px;height:36px;border:3px solid var(--color-border);border-top-color:var(--color-primary);border-radius:50%;animation:spin .8s linear infinite}@keyframes spin{to{transform:rotate(360deg)}}@media(max-width:800px){.detail-grid{grid-template-columns:1fr}.order-card{position:static}.spec-grid{grid-template-columns:1fr 1fr}.spec-grid article:nth-child(n){border-right:1px solid var(--color-border);border-bottom:1px solid var(--color-border)}h1{font-size:28px}}
.modal-backdrop{position:fixed;inset:0;z-index:1000;display:grid;place-items:center;padding:20px;background:rgba(11,31,27,.58);backdrop-filter:blur(3px);overscroll-behavior:contain}.order-modal{width:min(560px,100%);max-height:calc(100vh - 40px);overflow-y:auto;scrollbar-width:none;-ms-overflow-style:none;background:#fff;border:1px solid var(--color-border);border-radius:18px;box-shadow:0 24px 70px rgba(6,34,29,.24);animation:modalIn .2s ease both}.order-modal::-webkit-scrollbar{display:none}.modal-head{display:flex;align-items:flex-start;justify-content:space-between;padding:22px 24px 17px;border-bottom:1px solid var(--color-border)}.modal-head span{font-size:9px;font-weight:700;letter-spacing:.15em;color:var(--color-primary)}.modal-head h2{margin-top:2px;font-size:22px;letter-spacing:-.03em}.modal-close{display:grid;place-items:center;width:32px;height:32px;border-radius:50%;background:var(--color-bg-secondary);color:var(--color-text-secondary);font-size:22px;line-height:1}.modal-close:hover{background:var(--color-bg-tertiary)}.product-summary{display:flex;justify-content:space-between;gap:18px;margin:18px 24px 0;padding:14px 16px;border-radius:11px;background:var(--color-bg-secondary)}.product-summary>div{min-width:0}.product-summary span,.product-summary small{display:block;font-size:10px;color:var(--color-text-muted)}.product-summary strong{display:block;margin:3px 0;font-size:13px}.product-summary .unit-price{text-align:right;flex:none}.product-summary .unit-price strong{font-size:16px;color:var(--color-primary)}.order-form{padding:20px 24px 24px}.form-grid{display:grid;grid-template-columns:1fr 1fr;gap:15px}.form-grid label{position:relative;display:block}.form-grid label>span{display:block;margin-bottom:6px;font-size:11px;font-weight:700;color:var(--color-text-secondary)}.form-grid em{font-style:normal;color:var(--color-secondary)}.form-grid input,.form-grid textarea{width:100%;padding:10px 12px;border:1px solid var(--color-border);border-radius:8px;background:#fff;color:var(--color-text-primary);font-size:13px;outline:none;transition:var(--transition)}.form-grid textarea{resize:none;min-height:78px;padding-right:48px;scrollbar-width:none;-ms-overflow-style:none}.form-grid textarea::-webkit-scrollbar{display:none}.form-grid input:focus,.form-grid textarea:focus{border-color:var(--color-primary);box-shadow:0 0 0 3px rgba(18,107,91,.1)}.wide-field{grid-column:1/-1}.contact-heading{display:flex;align-items:center;gap:8px;margin-top:2px;padding-top:14px;border-top:1px solid var(--color-border)}.contact-heading strong{font-size:12px}.contact-heading span{padding:2px 7px;border-radius:20px;background:var(--color-bg-tertiary);font-size:9px;color:var(--color-text-muted)}.input-with-unit{position:relative}.input-with-unit input{padding-right:42px}.input-with-unit b{position:absolute;right:12px;top:50%;transform:translateY(-50%);font-size:11px;color:var(--color-text-muted)}.character-count{position:absolute;right:10px;bottom:8px;font-size:9px;color:var(--color-text-muted)}.quote-summary{display:flex;align-items:center;justify-content:space-between;margin-top:18px;padding:14px 16px;border:1px solid #cce5de;border-radius:10px;background:#f3fbf8}.quote-summary span,.quote-summary small{display:block}.quote-summary span{font-size:12px;font-weight:700}.quote-summary small{font-size:9px;color:var(--color-text-muted)}.quote-summary strong{font-size:20px;color:var(--color-primary)}.quote-note,.modal-error{margin-top:7px;font-size:10px;color:var(--color-text-muted)}.modal-error{padding:9px 11px;border-radius:7px;background:#fef2f2;color:#b91c1c}.modal-actions{display:flex;justify-content:flex-end;gap:8px;margin-top:18px}.modal-actions .btn{justify-content:center;min-width:100px}.modal-actions button:disabled{cursor:not-allowed;opacity:.6;transform:none;box-shadow:none}@keyframes modalIn{from{opacity:0;transform:translateY(10px) scale(.985)}to{opacity:1;transform:translateY(0) scale(1)}}@media(max-width:600px){.modal-backdrop{padding:10px;align-items:end}.order-modal{max-height:calc(100vh - 20px);border-radius:18px 18px 10px 10px}.modal-head,.order-form{padding-left:18px;padding-right:18px}.product-summary{margin-left:18px;margin-right:18px}.form-grid{grid-template-columns:1fr}.wide-field{grid-column:auto}.contact-heading{grid-column:1/-1}.modal-actions .btn{flex:1}}
.success-toast{position:fixed;z-index:1200;top:90px;left:50%;display:flex;align-items:center;gap:9px;transform:translateX(-50%);padding:12px 18px;border:1px solid #b9ded4;border-radius:12px;background:#effaf6;color:var(--color-primary-dark);box-shadow:var(--shadow-lg);font-size:13px;font-weight:700}.success-toast b{display:grid;place-items:center;width:22px;height:22px;border-radius:50%;background:var(--color-primary);color:#fff;font-size:11px}.toast-enter-active,.toast-leave-active{transition:opacity .2s ease,transform .2s ease}.toast-enter-from,.toast-leave-to{opacity:0;transform:translate(-50%,-8px)}
</style>
