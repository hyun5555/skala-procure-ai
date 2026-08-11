<template>
  <div ref="root" class="region-select">
    <button type="button" class="select-trigger" :class="{ open }" :aria-label="ariaLabel" :aria-expanded="open" @click="open = !open">
      <span :class="{ placeholder: !selectionSummary }">{{ selectionSummary || placeholder }}</span>
      <b>{{ selectedCount ? `${selectedCount}개` : '' }}</b>
      <i>⌄</i>
    </button>

    <div v-if="open" class="region-panel">
      <div class="search-wrap">
        <span>⌕</span>
        <input v-model.trim="query" type="search" placeholder="시·도 또는 시 검색" aria-label="지역 검색" />
      </div>

      <div v-if="query" class="search-results">
        <p class="panel-label">검색 결과 <b>{{ searchResults.length }}</b></p>
        <template v-if="searchResults.length">
          <label v-for="result in searchResults" :key="result.key" class="check-row search-row">
            <input
              type="checkbox"
              :checked="result.city ? isCityChecked(result.sido, result.city) : isSidoChecked(result.sido)"
              :disabled="allSelected"
              @change="result.city ? toggleCity(result.sido, result.city) : toggleSido(result.sido)"
            />
            <span><strong>{{ result.city ? result.city.name : result.sido.name }}</strong><small v-if="result.city">{{ result.sido.name }}</small></span>
            <em>{{ result.city ? '시 단위' : '도 단위' }}</em>
          </label>
        </template>
        <div v-else class="empty-state">일치하는 지역이 없습니다.</div>
      </div>

      <template v-else>
        <label class="all-row">
          <input type="checkbox" :checked="allSelected" @change="toggleAll" />
          <span><strong>전체 구역</strong><small>전국을 하나의 값으로 등록합니다.</small></span>
        </label>
        <div class="region-columns" :class="{ disabled: allSelected }">
          <div class="sido-list">
            <p class="panel-label">시·도</p>
            <div
              v-for="sido in legalRegions"
              :key="sido.code"
              class="sido-row"
              :class="{ active: activeSido?.code === sido.code }"
            >
              <input
                type="checkbox"
                :checked="isSidoChecked(sido)"
                :indeterminate.prop="isSidoPartial(sido)"
                :disabled="allSelected"
                :aria-label="`${sido.name} 전체 선택`"
                @change="toggleSido(sido)"
              />
              <button type="button" :disabled="allSelected" @click="activeSidoCode = sido.code">
                <span>{{ sido.name }}</span>
                <b v-if="sidoBadge(sido)">{{ sidoBadge(sido) }}</b>
                <i>›</i>
              </button>
            </div>
          </div>

          <div class="city-list">
            <p class="panel-label">{{ activeSido?.name }} · 시</p>
            <template v-if="activeSido?.cities.length">
              <label class="check-row city-all">
                <input
                  type="checkbox"
                  :checked="isSidoChecked(activeSido)"
                  :indeterminate.prop="isSidoPartial(activeSido)"
                  :disabled="allSelected"
                  @change="toggleSido(activeSido)"
                />
                <span><strong>전체 시</strong><small>{{ activeSido.name }} 전체</small></span>
              </label>
              <label v-for="city in activeSido.cities" :key="city.code" class="check-row">
                <input
                  type="checkbox"
                  :checked="isCityChecked(activeSido, city)"
                  :disabled="allSelected"
                  @change="toggleCity(activeSido, city)"
                />
                <span>{{ city.name }}</span>
              </label>
            </template>
            <div v-else class="empty-state">
              <b>하위 시가 없습니다.</b>
              <span>특별시·광역시·특별자치시는<br />시·도 단위로 선택해 주세요.</span>
            </div>
          </div>
        </div>
      </template>

      <div class="selected-area">
        <div class="selected-head"><p>선택된 지역 <b>{{ selectedCount }}</b></p><button v-if="modelValue.length" type="button" @click="clearAll">전체 해제</button></div>
        <div v-if="selectedItems.length" class="selected-items">
          <div v-for="item in selectedItems" :key="item.key" class="selected-chip">
            <span><strong>{{ item.label }}</strong><small>{{ item.unit }}</small></span>
            <button type="button" :aria-label="`${item.label} 삭제`" @click="removeItem(item)">×</button>
          </div>
        </div>
        <p v-else class="selected-empty">선택된 지역이 없습니다.</p>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { legalRegions, findCity, findSido } from '@/data/legalRegions.js'

const props = defineProps({
  modelValue: { type: Array, default: () => [] },
  placeholder: { type: String, default: '공급업체 소재지 선택' },
  ariaLabel: { type: String, default: '공급업체 소재지 선택' }
})
const emit = defineEmits(['update:modelValue'])
const root = ref(null)
const open = ref(false)
const query = ref('')
const activeSidoCode = ref(legalRegions.find(region => region.cities.length)?.code || legalRegions[0].code)

const allSelected = computed(() => props.modelValue.some(item => item.scope === 'ALL'))
const activeSido = computed(() => findSido(activeSidoCode.value) || legalRegions[0])
const selectedCount = computed(() => allSelected.value ? 1 : props.modelValue.length)
const selectionSummary = computed(() => {
  if (allSelected.value) return '전체 구역'
  if (!props.modelValue.length) return ''
  const first = selectedItems.value[0]?.label || ''
  return props.modelValue.length === 1 ? first : `${first} 외 ${props.modelValue.length - 1}개`
})
const selectedItems = computed(() => {
  if (allSelected.value) return [{ key: 'ALL', label: '전체 구역', unit: '전국 단위', scope: 'ALL' }]
  return props.modelValue.map(item => {
    const sido = findSido(item.sido)
    const city = item.si === null ? null : findCity(item.sido, item.si)
    return {
      key: `${item.sido}-${item.si ?? 'ALL'}`,
      label: city ? `${sido?.name} ${city.name}` : sido?.name || item.sido,
      unit: city ? '시 단위' : '도 단위',
      ...item
    }
  })
})
const searchResults = computed(() => {
  const keyword = query.value.replaceAll(' ', '').toLowerCase()
  if (!keyword) return []
  return legalRegions.flatMap(sido => {
    const result = []
    if (sido.name.replaceAll(' ', '').toLowerCase().includes(keyword)) result.push({ key: sido.code, sido })
    sido.cities.forEach(city => {
      const fullName = `${sido.name}${city.name}`.replaceAll(' ', '').toLowerCase()
      if (city.name.toLowerCase().includes(keyword) || fullName.includes(keyword)) result.push({ key: city.code, sido, city })
    })
    return result
  })
})

function emitSelection(selection) {
  emit('update:modelValue', selection)
}
function isSidoChecked(sido) {
  return !allSelected.value && props.modelValue.some(item => item.sido === sido.code && item.si === null)
}
function selectedCityCodes(sido) {
  return props.modelValue.filter(item => item.sido === sido.code && item.si !== null).map(item => item.si)
}
function isSidoPartial(sido) {
  return !isSidoChecked(sido) && selectedCityCodes(sido).length > 0
}
function isCityChecked(sido, city) {
  return isSidoChecked(sido) || props.modelValue.some(item => item.sido === sido.code && item.si === city.code)
}
function withoutSido(sido) {
  return props.modelValue.filter(item => item.sido !== sido.code && item.scope !== 'ALL')
}
function toggleAll() {
  emitSelection(allSelected.value ? [] : [{ scope: 'ALL' }])
}
function toggleSido(sido) {
  if (allSelected.value) return
  const rest = withoutSido(sido)
  emitSelection(isSidoChecked(sido) ? rest : [...rest, { sido: sido.code, si: null }])
  activeSidoCode.value = sido.code
}
function toggleCity(sido, city) {
  if (allSelected.value) return
  const otherRegions = withoutSido(sido)
  let cityCodes
  if (isSidoChecked(sido)) {
    cityCodes = sido.cities.filter(item => item.code !== city.code).map(item => item.code)
  } else {
    const current = selectedCityCodes(sido)
    cityCodes = current.includes(city.code) ? current.filter(code => code !== city.code) : [...current, city.code]
  }
  if (cityCodes.length === sido.cities.length) emitSelection([...otherRegions, { sido: sido.code, si: null }])
  else emitSelection([...otherRegions, ...cityCodes.map(code => ({ sido: sido.code, si: code }))])
  activeSidoCode.value = sido.code
}
function sidoBadge(sido) {
  if (isSidoChecked(sido)) return '전체'
  const count = selectedCityCodes(sido).length
  return count ? `${count}/${sido.cities.length}` : ''
}
function removeItem(item) {
  if (item.scope === 'ALL') return clearAll()
  emitSelection(props.modelValue.filter(selected => !(selected.sido === item.sido && selected.si === item.si)))
}
function clearAll() {
  emitSelection([])
}
function handleOutsideClick(event) {
  if (root.value && !root.value.contains(event.target)) open.value = false
}
onMounted(() => document.addEventListener('click', handleOutsideClick))
onBeforeUnmount(() => document.removeEventListener('click', handleOutsideClick))
</script>

<style scoped>
.region-select{position:relative;width:100%}.select-trigger{width:100%;height:42px;display:flex;align-items:center;gap:8px;padding:0 12px;border:1px solid var(--color-border);border-radius:9px;background:#fff;color:var(--color-text-primary);text-align:left}.select-trigger.open{border-color:var(--color-primary);box-shadow:0 0 0 3px var(--color-primary-light)}.select-trigger>span{flex:1;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.select-trigger .placeholder{color:var(--color-text-muted)}.select-trigger>b{padding:3px 6px;border-radius:10px;background:var(--color-primary-light);color:var(--color-primary);font-size:9px}.select-trigger>i{font-size:16px;font-style:normal;color:var(--color-text-muted)}.region-panel{position:absolute;z-index:30;top:48px;left:0;width:min(700px,calc(100vw - 80px));background:#fff;border:1px solid var(--color-border);border-radius:14px;box-shadow:0 18px 42px rgba(21,57,50,.16);overflow:hidden}.search-wrap{display:flex;align-items:center;gap:8px;margin:14px;padding:0 11px;border:1px solid var(--color-border);border-radius:9px}.search-wrap:focus-within{border-color:var(--color-primary)}.search-wrap>span{color:var(--color-text-muted)}.search-wrap input{height:38px;padding:0;border:0;box-shadow:none!important}.all-row,.check-row{display:flex;flex-direction:row;align-items:center;gap:9px}.all-row{margin:0 14px 12px;padding:11px 12px;border-radius:9px;background:var(--color-primary-light)}input[type=checkbox]{width:15px;height:15px;padding:0;accent-color:var(--color-primary);flex:none}.all-row span,.check-row>span{display:flex;flex:1;flex-direction:column;gap:1px}.all-row strong,.check-row strong{font-size:11px}.all-row small,.check-row small{font-size:9px;color:var(--color-text-muted)}.region-columns{display:grid;grid-template-columns:1fr 1fr;border-top:1px solid var(--color-border);border-bottom:1px solid var(--color-border);height:300px}.region-columns.disabled{opacity:.45;pointer-events:none}.sido-list,.city-list{padding:12px;overflow:auto}.sido-list{border-right:1px solid var(--color-border)}.panel-label{padding:0 5px 8px;font-size:10px;font-weight:700;color:var(--color-text-muted)}.panel-label b{color:var(--color-primary)}.sido-row{display:flex;align-items:center;gap:8px;padding-left:7px;border-radius:8px}.sido-row:hover,.sido-row.active{background:#f2f8f6}.sido-row>button{display:flex;align-items:center;gap:6px;flex:1;padding:8px 7px;border:0;background:transparent;text-align:left}.sido-row>button span{flex:1;font-size:11px}.sido-row>button b{padding:2px 5px;border-radius:8px;background:var(--color-primary-light);color:var(--color-primary);font-size:8px}.sido-row>button i{font-size:15px;font-style:normal;color:var(--color-text-muted)}.check-row{padding:8px 7px;border-radius:7px;font-size:11px}.check-row:hover{background:#f7faf9}.city-all{margin-bottom:4px;padding-bottom:10px;border-bottom:1px solid var(--color-border)}.empty-state{height:220px;display:flex;flex-direction:column;align-items:center;justify-content:center;gap:7px;color:var(--color-text-muted);font-size:10px;text-align:center;line-height:1.6}.empty-state b{color:var(--color-text-secondary)}.search-results{max-height:300px;overflow:auto;padding:0 14px 12px}.search-row em{padding:3px 6px;border-radius:8px;background:#f2f5f4;color:var(--color-text-muted);font-size:8px;font-style:normal}.search-results>.empty-state{height:130px}.selected-area{padding:12px 14px}.selected-head{display:flex;align-items:center;justify-content:space-between;margin-bottom:9px}.selected-head p{font-size:10px;font-weight:700}.selected-head p b{color:var(--color-primary)}.selected-head button{border:0;background:transparent;color:var(--color-primary);font-size:9px}.selected-items{display:flex;flex-wrap:wrap;gap:6px;max-height:94px;overflow:auto}.selected-chip{display:flex;align-items:center;gap:6px;padding:6px 7px 6px 9px;border:1px solid #cfe5df;border-radius:8px;background:#f5fbf9}.selected-chip span{display:flex;align-items:center;gap:5px}.selected-chip strong{font-size:9px}.selected-chip small{padding:2px 4px;border-radius:6px;background:var(--color-primary-light);color:var(--color-primary);font-size:7px}.selected-chip button{border:0;background:transparent;color:var(--color-text-muted);font-size:14px}.selected-empty{padding:8px;border-radius:7px;background:#f7f9f8;color:var(--color-text-muted);font-size:9px;text-align:center}@media(max-width:680px){.region-panel{position:fixed;top:80px;left:12px;width:calc(100vw - 24px);max-height:calc(100vh - 100px);overflow:auto}.region-columns{grid-template-columns:44% 56%;height:280px}}
.select-trigger.open,.search-wrap:focus-within{border-color:var(--color-accent);box-shadow:0 0 0 3px var(--color-primary-light)}
.select-trigger>b,.panel-label b,.selected-head p b,.selected-head button{color:var(--color-accent-dark)}
.region-panel{box-shadow:0 18px 42px rgba(23,27,32,.14)}
input[type=checkbox]{accent-color:var(--color-accent-dark)}
.sido-row:hover,.sido-row.active,.check-row:hover{background:#f2f9fd}
.search-row em,.selected-empty{background:#f3f5f6}
.selected-chip{border-color:#d3e9f5;background:#f3faff}
</style>
