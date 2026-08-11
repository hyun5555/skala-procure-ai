<template>
  <div class="page-wrapper"><AppHeader />
    <main class="main-content">
      <router-link to="/mypage" class="back-link">← 조달 품목 목록</router-link>
      <div class="page-heading"><div><span>SUPPLIER CATALOG</span><h1>공급 품목 등록</h1><p>나라장터 공급업체 데이터와 같은 기준으로 품목과 납품조건을 등록합니다.</p></div><div class="role-chip">공급기업 전용</div></div>
      <form class="form-card" @submit.prevent="handleSubmit">
        <section>
          <div class="section-title"><b>01</b><div><h2>기업·품목 정보</h2><p>공급업체 소재지와 조달 품목의 분류·규격을 입력합니다.</p></div></div>
          <div class="form-grid three">
            <div class="field span-two"><span>공급업체 소재지</span><RegionMultiSelect v-model="form.locations" /></div>
            <label><span>품명</span><select v-model="form.category"><option disabled value="">품명 선택</option><option v-for="option in categoryOptions" :key="option.value" :value="option.value">{{ option.label }}</option></select></label>
            <label class="span-two"><span>품목명</span><input v-model.trim="form.itemName" placeholder="예: 파형강관이음관 연결관" /></label>
            <label><span>규격</span><select v-model="form.specification"><option disabled value="">규격 선택</option><option v-for="spec in specificationOptions" :key="spec">{{ spec }}</option></select></label>
          </div>
        </section>
        <section>
          <div class="section-title"><b>02</b><div><h2>가격·납품 조건</h2><p>추천 필터와 예산 적합도 계산에 직접 반영됩니다.</p></div></div>
          <div class="form-grid three">
            <label><span>단가</span><div class="input-unit"><input v-model.number="form.price" type="number" min="0" placeholder="208424" /><em>원</em></div></label>
            <label><span>단위</span><select v-model="form.unit"><option v-for="unit in unitOptions" :key="unit">{{ unit }}</option></select></label>
            <label><span>납품일수</span><select v-model.number="form.deliveryDays"><option v-for="day in deliveryDayOptions" :key="day" :value="day">{{ day }}일</option></select></label>
            <label class="span-two"><span>공급지역</span><input v-model.trim="form.supplyRegion" placeholder="예: 전지역(도서지역 제외)" /></label>
            <label><span>인도조건</span><input v-model.trim="form.deliveryTerms" placeholder="예: 광주광역시 소촌대로 제1공장 인도" /></label>
          </div>
        </section>
        <section>
          <div class="section-title"><b>03</b><div><h2>인증·조달 등록</h2><p>공공조달 적격성과 가점 요소로 활용되는 정보입니다.</p></div></div>
          <div class="form-grid three">
            <label class="span-two"><span>인증정보</span><input v-model.trim="form.certification" placeholder="예: KS, 여성기업제품, 품질보증조달물품" /></label>
            <label><span>우수제품 여부</span><select v-model="form.excellent"><option value="N">해당 없음</option><option value="Y">우수제품</option></select></label>
            <label><span>MAS 여부</span><select v-model="form.mas"><option value="Y">MAS 등록</option><option value="N">미등록</option></select></label>
            <label><span>계약 시작일</span><input v-model="form.contractStart" type="date" /></label>
            <label><span>계약 종료일</span><input v-model="form.contractEnd" type="date" :min="form.contractStart" /></label>
          </div>
        </section>
        <div v-if="validationError || submitError" class="error-box">{{ validationError || submitError }}</div><div v-if="submitSuccess" class="success-box">{{ submitSuccess }}</div>
        <div class="form-actions"><router-link to="/mypage" class="btn btn-ghost">취소</router-link><button class="btn btn-primary" :disabled="submitting">{{ submitting ? '등록 중...' : '조달 품목 등록' }}</button></div>
      </form>
    </main>

    <AppFooter />
  </div>
</template>

<script setup>
import { computed, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import AppHeader from '@/components/AppHeader.vue'
import AppFooter from '@/components/AppFooter.vue'
import RegionMultiSelect from '@/components/RegionMultiSelect.vue'
import { courseApi } from '@/api/course.js'
import { useAuthStore } from '@/store/auth.js'
import { buildCapabilityDescription, deliveryDayOptions, getSpecificationOptions } from '@/utils/procurement.js'
import { addDemoCourse } from '@/data/demo.js'
import { formatRegionSelection } from '@/data/legalRegions.js'
const router=useRouter(),auth=useAuthStore()
const form=reactive({locations:[],category:'',itemName:'',specification:'',price:null,unit:'m',supplyRegion:'전지역',deliveryDays:30,deliveryTerms:'',certification:'',excellent:'N',mas:'Y',contractStart:'',contractEnd:''})
const submitting=ref(false),validationError=ref(''),submitError=ref(''),submitSuccess=ref('')
const categoryOptions=[{label:'파형강관',value:'BACKEND'},{label:'파형강관이음관',value:'FRONTEND'},{label:'피복강관',value:'DEVOPS'},{label:'피복강관이음',value:'DATA_SCIENCE'},{label:'스틸파일',value:'MOBILE'},{label:'주철관',value:'SECURITY'},{label:'주철제관이음',value:'DATABASE'},{label:'기타 관류',value:'OTHER'}]
const unitOptions=['m','개','EA','M','본','KG','조','kg','식']
const productLabel=computed(()=>categoryOptions.find(v=>v.value===form.category)?.label||'')
const specificationOptions=computed(()=>getSpecificationOptions(productLabel.value))
watch(()=>form.category,()=>{form.specification=''})
function validate(){validationError.value='';if(auth.user?.role!=='INSTRUCTOR')return validationError.value='공급기업 계정만 품목을 등록할 수 있습니다.',false;if(!form.locations.length)return validationError.value='공급업체 소재지를 선택해 주세요.',false;const required=[['category','품명'],['itemName','품목명'],['specification','규격'],['price','단가'],['supplyRegion','공급지역'],['deliveryDays','납품일수'],['deliveryTerms','인도조건'],['contractStart','계약 시작일'],['contractEnd','계약 종료일']];const missing=required.find(([key])=>form[key]===null||form[key]===undefined||form[key]==='');if(missing)return validationError.value=`${missing[1]}을(를) 입력해 주세요.`,false;if(form.contractEnd<form.contractStart)return validationError.value='계약 종료일은 시작일 이후로 선택해 주세요.',false;return true}
async function handleSubmit(){submitError.value='';submitSuccess.value='';if(!validate())return;submitting.value=true;try{const payload={...form,location:formatRegionSelection(form.locations),locationCodes:form.locations,productLabel:productLabel.value};const title=`${form.itemName}, ${form.specification}`;if(auth.isDemo){const id=Date.now();addDemoCourse({id,title,description:buildCapabilityDescription(payload),category:form.category,price:Number(form.price),contractEnd:form.contractEnd,instructorId:auth.user.id,instructorName:auth.user.name,enrollmentCount:0,status:'ACTIVE'});submitSuccess.value='데모 조달 품목이 등록되었습니다.';setTimeout(()=>router.push(`/courses/${id}`),300);return}const res=await courseApi.create({title,description:buildCapabilityDescription(payload),category:form.category,price:Number(form.price),contractEnd:form.contractEnd});submitSuccess.value='조달 품목이 등록되었습니다.';const id=res.data?.data?.id??res.data?.id;setTimeout(()=>router.push(id?`/courses/${id}`:'/mypage'),500)}catch(error){submitError.value=error.response?.data?.message||'조달 품목 등록에 실패했습니다.'}finally{submitting.value=false}}
</script>

<style scoped>
.page-wrapper{min-height:100vh;background:var(--color-bg-secondary)}.main-content{max-width:980px;margin:0 auto;padding:36px 24px 70px}.back-link{font-size:12px;color:var(--color-text-secondary)}.page-heading{display:flex;justify-content:space-between;align-items:flex-end;margin:22px 0}.page-heading span{font-size:9px;font-weight:800;letter-spacing:.16em;color:var(--color-primary)}.page-heading h1{font-size:28px;margin-top:5px}.page-heading p{font-size:13px;color:var(--color-text-muted);margin-top:5px}.role-chip{padding:7px 11px;border-radius:20px;background:var(--color-primary-light);color:var(--color-primary);font-size:10px;font-weight:700}.form-card{background:#fff;border:1px solid var(--color-border);border-radius:18px;overflow:hidden;box-shadow:var(--shadow-sm)}.form-card section{padding:26px 28px;border-bottom:1px solid var(--color-border)}.section-title{display:flex;align-items:center;gap:12px;margin-bottom:20px}.section-title>b{width:34px;height:34px;border-radius:9px;display:grid;place-items:center;background:var(--color-primary-light);color:var(--color-primary);font-size:11px}.section-title h2{font-size:16px}.section-title p{font-size:11px;color:var(--color-text-muted);margin-top:2px}.form-grid{display:grid;gap:16px}.form-grid.three{grid-template-columns:repeat(3,1fr)}label,.field{display:flex;flex-direction:column;gap:7px}label>span,.field>span{font-size:11px;font-weight:700;color:var(--color-text-secondary)}.span-two{grid-column:span 2}input,select{width:100%;padding:11px 12px;border:1px solid var(--color-border);border-radius:9px;outline:none;color:var(--color-text-primary);background:#fff}input:focus,select:focus{border-color:var(--color-primary);box-shadow:0 0 0 3px var(--color-primary-light)}.input-unit{position:relative}.input-unit input{padding-right:45px}.input-unit em{position:absolute;right:11px;top:50%;transform:translateY(-50%);font-size:10px;color:var(--color-text-muted);font-style:normal}.form-actions{display:flex;justify-content:flex-end;gap:8px;padding:20px 28px}.error-box,.success-box{margin:18px 28px 0;padding:11px 13px;border-radius:8px;font-size:12px}.error-box{background:#fef2f2;color:#b91c1c}.success-box{background:var(--color-primary-light);color:var(--color-primary-dark)}@media(max-width:680px){.main-content{padding:26px 16px}.page-heading{align-items:flex-start;flex-direction:column;gap:15px}.form-grid.three{grid-template-columns:1fr}.span-two{grid-column:auto}.form-card section{padding:22px 18px}.form-actions{padding:18px}}
.page-heading span,.role-chip,.section-title>b{color:var(--color-accent-dark)}
input:focus,select:focus{border-color:var(--color-accent);box-shadow:0 0 0 3px var(--color-primary-light)}
</style>
