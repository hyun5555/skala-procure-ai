import { findCity, findSido } from '@/data/legalRegions.js'

export const productOptions = [
  '전체', '파형강관', '파형강관이음관', '피복강관', '피복강관이음', '스틸파일', '주철관', '주철제관이음', '기타 관류'
]

export const deliveryDayOptions = [30, 50, 60, 90, 120]

export function getSpecificationOptions(product) {
  if (['파형강관', '파형강관이음관', '주철관', '주철제관이음'].includes(product)) {
    return ['Φ80mm', 'Φ100mm', 'Φ150mm', 'Φ200mm', 'Φ250mm', 'Φ300mm', 'Φ400mm', 'Φ500mm', 'Φ600mm', 'Φ800mm', 'Φ1000mm']
  }
  if (['피복강관', '피복강관이음'].includes(product)) {
    return ['15A', '20A', '25A', '40A', '50A', '80A', '100A', '150A', '200A', '300A']
  }
  if (product === '스틸파일') {
    return ['Φ318.5mm', 'Φ406.4mm', 'Φ508mm', 'Φ609.6mm', 'Φ711.2mm', 'Φ812.8mm']
  }
  return ['소형', '중형', '대형', '주문 규격']
}

const specAliases = {
  location: ['공급업체소재지', '소재지'],
  locationCodes: ['공급지역코드'],
  companyType: ['기업구분'],
  product: ['품명'],
  detailProduct: ['세부품명'],
  itemName: ['품목명'],
  specification: ['규격'],
  itemId: ['물품식별번호'],
  unit: ['단위'],
  supplyRegion: ['공급지역'],
  deliveryDays: ['납품일수', '평균납기'],
  deliveryPlace: ['납품장소'],
  deliveryTerms: ['인도조건'],
  certification: ['인증정보', '인증'],
  excellent: ['우수제품여부'],
  mas: ['MAS여부'],
  contractPeriod: ['계약기간'],
  mallRegisteredAt: ['쇼핑몰등록일자'],
  defectRate: ['누적 불량률', '불량률'],
  onTimeRate: ['납기 준수율', '납기준수율']
}

function findValue(entries, aliases) {
  const matched = entries.find(([key]) => aliases.includes(key.trim()))
  return matched?.[1]?.trim() || ''
}

function firstNumber(value) {
  const matched = String(value || '').replaceAll(',', '').match(/[\d.]+/)
  return matched ? Number(matched[0]) : null
}

export function parseCapability(description = '') {
  const entries = String(description)
    .split('|')
    .map(part => part.split(/:(.*)/s).slice(0, 2))
    .filter(parts => parts.length === 2)

  const specs = Object.fromEntries(
    Object.entries(specAliases).map(([key, aliases]) => [key, findValue(entries, aliases)])
  )

  return {
    ...specs,
    deliveryDaysValue: firstNumber(specs.deliveryDays),
    defectRateValue: firstNumber(specs.defectRate),
    onTimeRateValue: firstNumber(specs.onTimeRate)
  }
}

export function buildCapabilityDescription(form) {
  return [
    `공급업체소재지: ${form.location}`,
    `공급지역코드: ${JSON.stringify(form.locationCodes || [])}`,
    `품명: ${form.productLabel}`,
    `품목명: ${form.itemName}`,
    `규격: ${form.specification}`,
    `단위: ${form.unit}`,
    `공급지역: ${form.supplyRegion}`,
    `납품일수: ${Number(form.deliveryDays)}일`,
    `인도조건: ${form.deliveryTerms}`,
    `인증정보: ${form.certification || '해당 없음'}`,
    `우수제품여부: ${form.excellent}`,
    `MAS여부: ${form.mas}`,
    `계약기간: ${form.contractStart && form.contractEnd ? `${form.contractStart}~${form.contractEnd}` : '미등록'}`
  ].join(' | ')
}

function clamp(value, min, max) {
  return Math.min(max, Math.max(min, value))
}

function includesNormalized(source, target) {
  return String(source || '').replaceAll(' ', '').toLowerCase().includes(String(target || '').replaceAll(' ', '').toLowerCase())
}

function compactRegionName(name = '') {
  return String(name)
    .replace(/특별자치도|특별자치시|특별시|광역시|도|시$/g, '')
    .replaceAll(' ', '')
}

function matchesSupplyRegion(supplierRegion, selectedRegions = []) {
  if (!selectedRegions.length || selectedRegions.some(item => item.scope === 'ALL')) return true
  const source = String(supplierRegion || '')
  const normalizedSource = source.replaceAll(' ', '')
  const selectedNames = selectedRegions.map(item => {
    const sido = findSido(item.sido)
    const city = item.si === null ? null : findCity(item.sido, item.si)
    return { sido: sido?.name || '', city: city?.name || '' }
  })

  if (normalizedSource.includes('전지역')) {
    const requestsJeju = selectedNames.some(item => compactRegionName(item.sido) === '제주')
    return !(requestsJeju && /제주.{0,12}제외/.test(normalizedSource))
  }

  return selectedNames.some(({ sido, city }) => {
    const targets = [sido, compactRegionName(sido), city, compactRegionName(city)].filter(Boolean)
    return targets.some(target => includesNormalized(source, target))
  })
}

export function evaluateCourse(course, criteria) {
  const specs = parseCapability(course.description)
  const product = specs.product || course.category
  const searchable = `${course.title || ''} ${specs.detailProduct} ${specs.itemName}`
  const productMatch = !criteria.product || criteria.product === '전체' || product === criteria.product || course.category === criteria.product
  const detailMatch = !criteria.detailProduct || includesNormalized(specs.detailProduct, criteria.detailProduct)
  const specificationSearchable = `${specs.specification} ${specs.itemName} ${course.title || ''}`
  const specificationMatch = !criteria.specification || criteria.specification === '전체' || includesNormalized(specificationSearchable, criteria.specification)
  const keywordMatch = !criteria.keyword || includesNormalized(searchable, criteria.keyword)
  const companyTypeMatch = !criteria.companyType || criteria.companyType === '전체' || specs.companyType === criteria.companyType
  const regionMatch = criteria.supplyRegions
    ? matchesSupplyRegion(specs.supplyRegion, criteria.supplyRegions)
    : (!criteria.supplyRegion || includesNormalized(specs.supplyRegion, criteria.supplyRegion))
  const quantity = Number(criteria.quantity || 0)
  const budget = Number(criteria.budget || 0)
  const totalPrice = Number(course.price || 0) * quantity
  const budgetMatch = !budget || totalPrice <= budget
  const maxDeliveryDays = Number(criteria.maxDeliveryDays || 0)
  const deliveryMatch = !maxDeliveryDays || specs.deliveryDaysValue === null || specs.deliveryDaysValue <= maxDeliveryDays
  const requestedCertifications = Array.isArray(criteria.certifications)
    ? criteria.certifications
    : criteria.certification ? [criteria.certification] : []
  const certificationMatch = requestedCertifications.every(certification => includesNormalized(specs.certification, certification))
  const excellentMatch = !criteria.excellentOnly || specs.excellent === 'Y'
  const masMatch = !criteria.masOnly || specs.mas === 'Y'
  const eligible = productMatch && detailMatch && specificationMatch && keywordMatch && companyTypeMatch && regionMatch && budgetMatch && deliveryMatch && certificationMatch && excellentMatch && masMatch

  const unitPrice = Number(course.price || 0)
  const budgetRatio = budget && quantity ? totalPrice / budget : 0.5
  const breakdown = {
    itemFit: (productMatch ? 12 : 0) + (detailMatch ? 6 : 0) + (specificationMatch ? 6 : 0) + (keywordMatch ? 6 : 0),
    price: Math.round(clamp(25 * (1.15 - budgetRatio), 0, 25)),
    delivery: specs.deliveryDaysValue === null ? 0 : Math.round(clamp(20 * (30 / specs.deliveryDaysValue), 0, 20)),
    region: regionMatch ? 10 : 0,
    certification: specs.certification && specs.certification !== '해당 없음' ? 10 : 0,
    procurement: (specs.excellent === 'Y' ? 3 : 0) + (specs.mas === 'Y' ? 2 : 0)
  }
  const score = Object.values(breakdown).reduce((sum, value) => sum + value, 0)
  const reasons = []
  if (specs.deliveryDays) reasons.push(`${specs.deliveryDays} 납품`)
  if (budget && budgetMatch) reasons.push('예산 충족')
  if (specs.certification && specs.certification !== '해당 없음') reasons.push(specs.certification.split(',').slice(0, 2).join('·'))
  if (specs.excellent === 'Y') reasons.push('우수제품')
  if (specs.mas === 'Y') reasons.push('MAS 등록')

  return {
    ...course,
    specs,
    eligible,
    score,
    scoreBreakdown: breakdown,
    reason: reasons.join(' · ') || '품목 및 납품조건 적합',
    totalPrice,
    unitPrice
  }
}
