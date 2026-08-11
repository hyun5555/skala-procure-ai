<template>
  <div class="page-wrapper">
    <AppHeader />
    <main class="main-content">
      <router-link to="/mypage" class="back-link">← 조달 품목 목록</router-link>
      <section class="heading"><span>CATALOG MANAGEMENT</span><h1>조달 품목 수정</h1><p>원본 조달 명세는 보존하고 품목명, 품명, 단가와 계약 종료일만 수정합니다.</p></section>
      <div v-if="loading" class="form-card loading">품목 정보를 불러오는 중입니다.</div>
      <form v-else class="form-card" @submit.prevent="submit">
        <label><span>품목명</span><input v-model.trim="form.title" maxlength="255" required></label>
        <label><span>품명</span><select v-model="form.category" required><option v-for="option in categoryOptions" :key="option.value" :value="option.value">{{ option.label }}</option></select></label>
        <label><span>기준 단가</span><div class="input-unit"><input v-model.number="form.price" type="number" min="0" required><em>원</em></div></label>
        <label><span>계약 종료일</span><input v-model="form.contractEnd" type="date"><small>비우면 서버가 기존 조달 명세의 계약기간을 기준으로 판단합니다.</small></label>
        <div class="preserved"><b>원본 명세 보존</b><p>{{ course?.description || '등록된 상세 명세가 없습니다.' }}</p></div>
        <p v-if="error" class="error" role="alert">{{ error }}</p>
        <div class="actions"><router-link to="/mypage" class="btn btn-ghost">취소</router-link><button class="btn btn-primary" :disabled="saving">{{ saving ? '저장 중...' : '변경사항 저장' }}</button></div>
      </form>
    </main>
    <AppFooter />
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AppHeader from '@/components/AppHeader.vue'
import AppFooter from '@/components/AppFooter.vue'
import { courseApi } from '@/api/course.js'
import { useCourseStore } from '@/store/course.js'
import { useAuthStore } from '@/store/auth.js'
import { updateDemoCourse } from '@/data/demo.js'

const route = useRoute(), router = useRouter(), courseStore = useCourseStore(), auth = useAuthStore()
const loading = ref(true), saving = ref(false), error = ref(''), course = ref(null)
const form = reactive({ title: '', category: '', price: 0, contractEnd: '' })
const categoryOptions = Object.entries(courseStore.categoryLabelMap).map(([value, label]) => ({ value, label }))

onMounted(async () => {
  try {
    await courseStore.fetchCourse(route.params.id)
    course.value = courseStore.selectedCourse
    if (!course.value) throw new Error('품목을 찾을 수 없습니다.')
    if (Number(course.value.instructorId) !== Number(auth.user?.id)) throw new Error('자신이 등록한 품목만 수정할 수 있습니다.')
    form.title = course.value.title || ''
    form.category = categoryOptions.find(option => option.label === course.value.category)?.value || course.value.category
    form.price = Number(course.value.price || 0)
    form.contractEnd = course.value.contractEnd || ''
  } catch (e) {
    error.value = e.response?.data?.message || e.message || '품목 정보를 불러오지 못했습니다.'
  } finally { loading.value = false }
})

async function submit() {
  if (!form.title || !form.category || Number(form.price) < 0) return
  saving.value = true; error.value = ''
  const payload = { title: form.title, category: form.category, price: Number(form.price), contractEnd: form.contractEnd || null }
  try {
    if (auth.isDemo) updateDemoCourse(route.params.id, payload)
    else await courseApi.update(route.params.id, payload)
    await router.push('/mypage')
  } catch (e) {
    error.value = e.response?.data?.message || e.message || '품목 수정에 실패했습니다.'
  } finally { saving.value = false }
}
</script>

<style scoped>
.page-wrapper{min-height:100vh;background:var(--color-bg-secondary)}.main-content{max-width:760px;margin:0 auto;padding:36px 24px 80px}.back-link{font-size:12px;color:var(--color-text-secondary)}.heading{margin:24px 0}.heading span{font-size:9px;font-weight:800;letter-spacing:.15em;color:var(--color-accent-dark)}.heading h1{font-size:28px;margin-top:5px}.heading p{font-size:13px;color:var(--color-text-muted);margin-top:6px}.form-card{display:grid;grid-template-columns:1fr 1fr;gap:18px;padding:28px;border:1px solid var(--color-border);border-radius:18px;background:#fff;box-shadow:var(--shadow-sm)}.form-card.loading{display:block;color:var(--color-text-muted)}label{display:flex;flex-direction:column;gap:7px}label>span{font-size:11px;font-weight:700;color:var(--color-text-secondary)}input,select{width:100%;padding:11px 12px;border:1px solid var(--color-border);border-radius:9px;background:#fff}label small{font-size:9px;color:var(--color-text-muted)}.input-unit{position:relative}.input-unit input{padding-right:42px}.input-unit em{position:absolute;right:12px;top:50%;transform:translateY(-50%);font-size:10px;font-style:normal;color:var(--color-text-muted)}.preserved,.error,.actions{grid-column:1/-1}.preserved{padding:14px;border-radius:10px;background:var(--color-bg-secondary)}.preserved b{font-size:11px}.preserved p{margin-top:5px;font-size:10px;line-height:1.55;color:var(--color-text-muted)}.error{padding:10px;border-radius:8px;background:#fff1f1;color:#a53b3b;font-size:11px}.actions{display:flex;justify-content:flex-end;gap:8px}@media(max-width:620px){.form-card{grid-template-columns:1fr}.main-content{padding:28px 16px}}
</style>
