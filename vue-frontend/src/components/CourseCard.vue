<template>
  <router-link :to="`/courses/${course.id}`" class="course-card">
    <div class="card-thumb" :class="thumbBg">
      <span class="material-code">{{ course.category }}</span>
      <span v-if="course.score !== undefined" class="match-score">{{ course.score }}<small>점</small></span>
      <div class="factory-lines" aria-hidden="true"><i></i><i></i><i></i></div>
    </div>

    <!-- 내용 -->
    <div class="card-body">
      <div class="card-kicker"><span class="badge" :class="badgeClass">{{ course.category }}</span><span>{{ course.specs?.companyType || '공급업체' }}</span></div>
      <h3 class="card-title">{{ course.title }}</h3>
      <p class="item-subtitle">{{ course.specs?.specification || course.specs?.itemName || course.specs?.location || '조달 등록 품목' }}</p>
      <div class="card-meta">
        <span class="instructor">{{ course.instructorName || '공급기업' }}</span>
        <span class="price">{{ Number(course.price).toLocaleString() }}원<small>/{{ course.specs?.unit || '단위' }}</small></span>
      </div>
      <div v-if="course.specs" class="data-pills"><span>{{ course.specs.deliveryDays || '납기 미등록' }}</span><span v-if="course.specs.mas === 'Y'">MAS</span><span v-if="course.specs.excellent === 'Y'">우수제품</span></div>
      <p v-if="course.reason" class="recommend-reason">{{ course.reason }}</p>
      <div class="card-footer">
        <span class="enrolled">누적 거래 {{ course.enrollmentCount?.toLocaleString() || 0 }}건</span>
        <span class="detail-link">상세 비교 →</span>
      </div>
    </div>
  </router-link>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  course: { type: Object, required: true }
})

// 키가 **화면 라벨**이다(enum 아님). store/course.js 의 categoryLabelMap 값과
// 철자까지 같아야 한다. 빠지면 회색 배지 + 썸네일 없음으로 떨어진다.
const categoryConfig = {
  '파형강관':       { bg: 'thumb-teal',   badge: 'badge-teal' },
  '파형강관이음관': { bg: 'thumb-teal',   badge: 'badge-teal' },
  '피복강관':       { bg: 'thumb-blue',   badge: 'badge-blue' },
  '피복강관이음':   { bg: 'thumb-blue',   badge: 'badge-blue' },
  '스틸파일':       { bg: 'thumb-amber',  badge: 'badge-purple' },
  '주철관':         { bg: 'thumb-purple', badge: 'badge-purple' },
  '주철제관이음':   { bg: 'thumb-pink',   badge: 'badge-pink' },
  '기타 관류':      { bg: 'thumb-gray',   badge: 'badge-gray' },
}

const config = computed(() => categoryConfig[props.course.category] || { bg: 'thumb-gray', badge: 'badge-gray' })
const thumbBg = computed(() => config.value.bg)
const badgeClass = computed(() => config.value.badge)

</script>

<style scoped>
.course-card {
  display: flex;
  flex-direction: column;
  background: var(--color-bg-primary);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  overflow: hidden;
  transition: var(--transition);
  cursor: pointer;
}
.course-card:hover {
  transform: translateY(-3px);
  box-shadow: var(--shadow-md);
  border-color: var(--color-border-hover);
}
.card-thumb {
  height: 112px;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  position: relative;
  justify-content: space-between;
  padding: 20px;
}
.thumb-teal   { background: #E5F1E9; }
.thumb-blue   { background: #EAF6FD; }
.thumb-amber  { background: #F8E2DA; }
.thumb-purple { background: #DDEFFA; }
.thumb-pink   { background: #F2ECE9; }
.thumb-gray   { background: #EDF1F3; }
.material-code { font-size: 24px; font-weight: 800; letter-spacing: -.04em; color: rgba(23,37,35,.8); z-index:1; }
.match-score { z-index:1; align-self:flex-start; background:#fff; color:var(--color-primary); border-radius:999px; padding:6px 10px; font-size:18px; font-weight:800; box-shadow:var(--shadow-sm); }
.match-score small { font-size:10px; margin-left:2px; }
.factory-lines { position:absolute; right:18px; bottom:-4px; display:flex; align-items:flex-end; gap:5px; opacity:.14; }
.factory-lines i { width:20px; height:52px; background:currentColor; border-radius:4px 4px 0 0; }
.factory-lines i:nth-child(2) { height:72px; }
.factory-lines i:nth-child(3) { height:43px; }
.card-body {
  padding: 14px 16px;
  display: flex;
  flex-direction: column;
  gap: 6px;
  flex: 1;
}
.card-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--color-text-primary);
  line-height: 1.4;
}
.item-subtitle { font-size:11px; color:var(--color-text-muted); white-space:nowrap; overflow:hidden; text-overflow:ellipsis; }
.card-kicker { display:flex; align-items:center; justify-content:space-between; gap:8px; font-size:11px; color:var(--color-text-muted); }
.card-meta {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.instructor {
  font-size: 12px;
  color: var(--color-text-secondary);
}
.price {
  font-size: 14px;
  font-weight: 600;
  color: var(--color-primary);
}
.price small { font-size:10px; font-weight:500; color:var(--color-text-muted); }
.recommend-reason { padding:9px 10px; border-radius:8px; background:var(--color-primary-light); color:var(--color-primary-dark); font-size:11px; line-height:1.45; }
.data-pills { display:flex; gap:5px; flex-wrap:wrap; }
.data-pills span { padding:3px 7px; border-radius:5px; background:var(--color-bg-tertiary); color:var(--color-text-secondary); font-size:9px; font-weight:600; }
.card-footer {
  margin-top: 2px;
  padding-top:8px;
  border-top:1px solid var(--color-border);
  display:flex;
  justify-content:space-between;
}
.enrolled {
  font-size: 11px;
  color: var(--color-text-muted);
}
.detail-link { font-size:11px; color:var(--color-primary); font-weight:600; }
.match-score,.detail-link { color:var(--color-accent-dark); }
.recommend-reason { color:#315f79; }
</style>
