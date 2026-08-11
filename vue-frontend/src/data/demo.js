const DEMO_COURSES = [
  {
    id: 9001,
    title: '파형강관, Φ300mm, 1.6mm, 6m',
    description: '공급업체소재지: 경상남도 진주시 | 기업구분: 중소기업 | 품명: 파형강관 | 세부품명: 파형강관 | 품목명: 파형강관, Φ300mm, 1.6mm, 6m | 물품식별번호: 25190001 | 단위: m | 공급지역: 전지역(제주 및 도서지역은 해상운임비 별도) | 납품일수: 30일 | 납품장소: 수요기관 지정장소 | 인도조건: 납품장소 하차도 | 인증정보: 장애인기업제품, 창업기업제품 | 우수제품여부: N | MAS여부: Y | 계약기간: 20250101~20261231 | 쇼핑몰등록일자: 20250122',
    category: 'BACKEND', price: 208424, instructorId: 9002, instructorName: '남강철강 주식회사', enrollmentCount: 128, status: 'ACTIVE'
  },
  {
    id: 9002,
    title: '파형강관이음관, Φ300mm, 연결관',
    description: '공급업체소재지: 경상남도 진주시 | 기업구분: 중소기업 | 품명: 파형강관이음관 | 세부품명: 파형강관이음관 | 품목명: 파형강관이음관, Φ300mm, 연결관 | 물품식별번호: 25190002 | 단위: 개 | 공급지역: 전지역(도서지역 제외) | 납품일수: 30일 | 납품장소: 수요기관 지정장소 | 인도조건: 현장설치도 | 인증정보: 장애인기업제품, 창업기업제품 | 우수제품여부: N | MAS여부: Y | 계약기간: 20250101~20261231 | 쇼핑몰등록일자: 20250122',
    category: 'FRONTEND', price: 156800, instructorId: 9002, instructorName: '남강철강 주식회사', enrollmentCount: 94, status: 'ACTIVE'
  },
  {
    id: 9003,
    title: '파형강관, Φ600mm, 2.0mm, 6m',
    description: '공급업체소재지: 경상북도 경주시 | 기업구분: 중소기업 | 품명: 파형강관 | 세부품명: 파형강관 | 품목명: 파형강관, Φ600mm, 2.0mm, 6m | 물품식별번호: 25190003 | 단위: m | 공급지역: 전지역(도서지역 제외) | 납품일수: 30일 | 납품장소: 수요기관 지정장소 | 인도조건: 납품장소 하차도 | 인증정보: KS, G-PASS기업, 소기업 | 우수제품여부: N | MAS여부: Y | 계약기간: 20250201~20270131 | 쇼핑몰등록일자: 20250210',
    category: 'BACKEND', price: 387500, instructorId: 9010, instructorName: '영남산업 주식회사', enrollmentCount: 113, status: 'ACTIVE'
  },
  {
    id: 9004,
    title: '폴리에틸렌피복강관, 100A, 6m',
    description: '공급업체소재지: 경상남도 함안군 | 기업구분: 중소기업 | 품명: 피복강관 | 세부품명: 폴리에틸렌피복강관 | 품목명: 폴리에틸렌피복강관, 100A, 6m | 물품식별번호: 25190004 | 단위: M | 공급지역: 전지역(제주,도서지역 제외) | 납품일수: 60일 | 납품장소: 수요기관 지정장소 | 인도조건: 기타사항참조 | 인증정보: KS, 소기업, 가족친화인증기업 | 우수제품여부: Y | MAS여부: Y | 계약기간: 20250301~20270228 | 쇼핑몰등록일자: 20250312',
    category: 'DEVOPS', price: 94500, instructorId: 9011, instructorName: '대한강관 주식회사', enrollmentCount: 86, status: 'ACTIVE'
  },
  {
    id: 9005,
    title: '강관파일, Φ508mm, 12m',
    description: '공급업체소재지: 경기도 평택시 | 기업구분: 중견기업 | 품명: 스틸파일 | 세부품명: 강관파일 | 품목명: 강관파일, Φ508mm, 12m | 물품식별번호: 25190005 | 단위: 본 | 공급지역: 전지역 | 납품일수: 90일 | 납품장소: 공사현장 | 인도조건: 현장하차도 | 인증정보: KS, 품질보증조달물품 | 우수제품여부: Y | MAS여부: N | 계약기간: 20250101~20261231 | 쇼핑몰등록일자: 20250115',
    category: 'MOBILE', price: 2450000, instructorId: 9012, instructorName: '신한스틸파이프주식회사', enrollmentCount: 41, status: 'ACTIVE'
  },
  {
    id: 9006,
    title: '수도용덕타일주철관, Φ300mm×6m, 2종',
    description: '공급업체소재지: 충청북도 영동군 | 기업구분: 중소기업 | 품명: 주철관 | 세부품명: 수도용덕타일주철관 | 품목명: 수도용덕타일주철관, Φ300mm×6m, 2종 | 물품식별번호: 10062465 | 단위: 본 | 공급지역: 전지역 | 납품일수: 30일 | 납품장소: 수요기관 지정장소 | 인도조건: 기타사항참조 | 인증정보: KS, 소기업 | 우수제품여부: N | MAS여부: Y | 계약기간: 20240131~20250331 | 쇼핑몰등록일자: 20250122',
    category: 'SECURITY', price: 620740, instructorId: 9013, instructorName: '(주)신안주철', enrollmentCount: 72, status: 'ACTIVE'
  }
]

export function getDemoCourses() {
  const added = JSON.parse(sessionStorage.getItem('demo_courses') || '[]')
  const statuses = JSON.parse(sessionStorage.getItem('demo_course_statuses') || '{}')
  return [...added, ...DEMO_COURSES].map(course => ({ ...course, status: statuses[String(course.id)] || course.status }))
}

export function addDemoCourse(course) {
  const added = JSON.parse(sessionStorage.getItem('demo_courses') || '[]')
  added.unshift(course)
  sessionStorage.setItem('demo_courses', JSON.stringify(added))
}

export function updateDemoCourseStatus(courseId, status) {
  const statuses = JSON.parse(sessionStorage.getItem('demo_course_statuses') || '{}')
  statuses[String(courseId)] = status
  sessionStorage.setItem('demo_course_statuses', JSON.stringify(statuses))
  return status
}

export function getDemoEnrollments() {
  return JSON.parse(sessionStorage.getItem('demo_enrollments') || '[]')
}

export function saveOrderRequest(userId, courseId, orderRequest) {
  const requests = JSON.parse(sessionStorage.getItem('order_requests') || '{}')
  requests[`${userId}:${courseId}`] = orderRequest
  sessionStorage.setItem('order_requests', JSON.stringify(requests))
}

export function getSavedOrderRequest(userId, courseId) {
  const requests = JSON.parse(sessionStorage.getItem('order_requests') || '{}')
  return requests[`${userId}:${courseId}`] || null
}

export function addDemoEnrollment(course, orderRequest = null) {
  const list = getDemoEnrollments()
  const existing = list.find(item => Number(item.courseId) === Number(course.id))
  if (existing) return existing
  const enrollment = { id: Date.now(), userId: 9001, courseId: course.id, status: 'ACTIVE', createdAt: new Date().toISOString(), course, orderRequest }
  list.unshift(enrollment)
  sessionStorage.setItem('demo_enrollments', JSON.stringify(list))
  if (orderRequest) saveOrderRequest(9001, course.id, orderRequest)
  return enrollment
}
