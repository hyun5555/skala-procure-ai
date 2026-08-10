import { defineStore } from 'pinia'
import { ref } from 'vue'
import { courseApi } from '@/api/course.js'

export const useCourseStore = defineStore('course', () => {
  const courses = ref([])
  const selectedCourse = ref(null)
  const loading = ref(false)
  const error = ref(null)
  const selectedCategory = ref('전체')

  const categories = [
    '전체', 'SUS304', 'SUS316', 'AL6061', '탄소강', '황동', '티타늄', '엔지니어링플라스틱', '기타'
  ]

  // 백엔드 enum → 화면 표시용 소재 계열
  //
  // 백엔드 Course.Category 는 8칸 고정 enum 이고 값 자체에 의미가 없다.
  // 소재 계열을 이 슬롯에 배정하고 화면 라벨만 여기서 바꾼다.
  // enum 값을 바꾸려면 자바 수정과 재빌드가 필요하므로 그렇게 하지 않는다.
  //
  // **8칸을 빠짐없이 채운다.** 없는 키는 normalizeCategory 가 원본을 그대로
  // 반환해서 화면에 영문 enum 이 노출된다. 원본이 그 상태였다 --
  // DATA/AI 는 백엔드에 없는 키였고 DATA_SCIENCE 등 5개는 매핑이 없었다.
  const categoryLabelMap = {
    BACKEND: 'SUS304',
    FRONTEND: 'SUS316',
    DEVOPS: 'AL6061',
    DATA_SCIENCE: '탄소강',
    MOBILE: '황동',
    SECURITY: '티타늄',
    DATABASE: '엔지니어링플라스틱',
    OTHER: '기타'
  }

  // 썸네일 이미지 매핑
  const thumbnailMap = {
    SPRING: new URL('../assets/images/courses/spring_boot.png', import.meta.url).href,
    VUE: new URL('../assets/images/courses/vue_js.png', import.meta.url).href,
    DOCKER: new URL('../assets/images/courses/docker.png', import.meta.url).href,
    KUBERNETES: new URL('../assets/images/courses/kubernetes.png', import.meta.url).href,
    PYTHON: new URL('../assets/images/courses/python.png', import.meta.url).href,
    AI: new URL('../assets/images/courses/generative_ai.png', import.meta.url).href,
  }

  // 키가 **화면 라벨**이다(enum 아님). categoryLabelMap 의 값과 철자까지 같아야 한다.
  // 이미지가 6개뿐이라 계열끼리 재사용한다.
  const categoryThumbnailMap = {
    'SUS304': thumbnailMap.SPRING,
    'SUS316': thumbnailMap.SPRING,
    'AL6061': thumbnailMap.KUBERNETES,
    '탄소강': thumbnailMap.DOCKER,
    '황동': thumbnailMap.PYTHON,
    '티타늄': thumbnailMap.VUE,
    '엔지니어링플라스틱': thumbnailMap.AI,
    '기타': thumbnailMap.PYTHON
  }

  function normalizeCategory(category) {
    if (!category) return ''
    return categoryLabelMap[category] || category
  }

  function normalizeCourse(course) {
    if (!course || typeof course !== 'object') return course

    return {
      ...course,
      category: normalizeCategory(course.category)
    }
  }

  function getThumbnail(course) {
    const thumbKey = course?.thumbnail?.toUpperCase?.() || ''
    if (thumbKey && thumbnailMap[thumbKey]) {
      return thumbnailMap[thumbKey]
    }

    return categoryThumbnailMap[course?.category] || null
  }

  async function fetchCourses() {
    loading.value = true
    error.value = null

    try {
      const res = await courseApi.getAll()
      console.log('[CourseStore] fetchCourses response =', res.data)

      const rawCourses = Array.isArray(res.data?.data)
        ? res.data.data
        : Array.isArray(res.data)
          ? res.data
          : []

      courses.value = rawCourses.map(normalizeCourse)

      console.log('[CourseStore] normalized courses =', courses.value)
    } catch (e) {
      console.error('[CourseStore] fetchCourses failed:', e)
      error.value = e.message || '강의 목록을 불러오지 못했습니다.'
      courses.value = []
    } finally {
      loading.value = false
    }
  }

  async function fetchCourse(id) {
    loading.value = true
    error.value = null

    try {
      const res = await courseApi.getById(id)
      console.log('[CourseStore] fetchCourse response =', res.data)

      const rawCourse =
        res.data?.data && typeof res.data.data === 'object'
          ? res.data.data
          : res.data

      selectedCourse.value = normalizeCourse(rawCourse)

      console.log('[CourseStore] normalized selectedCourse =', selectedCourse.value)
    } catch (e) {
      console.error('[CourseStore] fetchCourse failed:', e)
      error.value = e.message || '강의 정보를 불러오지 못했습니다.'
      selectedCourse.value = null
    } finally {
      loading.value = false
    }
  }

  function setCategory(cat) {
    selectedCategory.value = cat
  }

  return {
    courses,
    selectedCourse,
    loading,
    error,
    categories,
    selectedCategory,
    thumbnailMap,
    categoryLabelMap,
    normalizeCategory,
    normalizeCourse,
    getThumbnail,
    fetchCourses,
    fetchCourse,
    setCategory
  }
})