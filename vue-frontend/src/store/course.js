import { defineStore } from 'pinia'
import { ref } from 'vue'
import { courseApi } from '@/api/course.js'
import { parseCapability } from '@/utils/procurement.js'
import { getDemoCourses } from '@/data/demo.js'
import { useAuthStore } from '@/store/auth.js'

export const useCourseStore = defineStore('course', () => {
  const courses = ref([])
  const selectedCourse = ref(null)
  const loading = ref(false)
  const error = ref(null)
  const selectedCategory = ref('전체')

  const categories = [
    '전체', '파형강관', '파형강관이음관', '피복강관', '피복강관이음', '스틸파일', '주철관', '주철제관이음', '기타 관류'
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
    BACKEND: '파형강관',
    FRONTEND: '파형강관이음관',
    DEVOPS: '피복강관',
    DATA_SCIENCE: '피복강관이음',
    MOBILE: '스틸파일',
    SECURITY: '주철관',
    DATABASE: '주철제관이음',
    OTHER: '기타 관류'
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
    '파형강관': thumbnailMap.SPRING,
    '파형강관이음관': thumbnailMap.SPRING,
    '피복강관': thumbnailMap.KUBERNETES,
    '피복강관이음': thumbnailMap.DOCKER,
    '스틸파일': thumbnailMap.PYTHON,
    '주철관': thumbnailMap.VUE,
    '주철제관이음': thumbnailMap.AI,
    '기타 관류': thumbnailMap.PYTHON
  }

  function normalizeCategory(category) {
    if (!category) return ''
    return categoryLabelMap[category] || category
  }

  function normalizeCourse(course) {
    if (!course || typeof course !== 'object') return course

    return {
      ...course,
      category: normalizeCategory(course.category),
      specs: course.specs || parseCapability(course.description)
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
      if (useAuthStore().isDemo) {
        courses.value = getDemoCourses().filter(course => course.status === 'ACTIVE').map(normalizeCourse)
        return
      }
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
      error.value = e.message || '공급기업 목록을 불러오지 못했습니다.'
      courses.value = []
    } finally {
      loading.value = false
    }
  }

  async function fetchCoursesByCategory(category) {
    loading.value = true
    error.value = null
    try {
      const res = await courseApi.getByCategory(category)
      const rawCourses = Array.isArray(res.data?.data) ? res.data.data : Array.isArray(res.data) ? res.data : []
      courses.value = rawCourses.map(normalizeCourse)
    } catch (e) {
      error.value = e.message || '품명별 공급기업 목록을 불러오지 못했습니다.'
      courses.value = []
      throw e
    } finally {
      loading.value = false
    }
  }

  async function fetchCourse(id) {
    loading.value = true
    error.value = null

    try {
      if (useAuthStore().isDemo) {
        selectedCourse.value = normalizeCourse(getDemoCourses().find(course => Number(course.id) === Number(id)) || null)
        return
      }
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
      error.value = e.message || '조달 품목 정보를 불러오지 못했습니다.'
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
    fetchCoursesByCategory,
    fetchCourse,
    setCategory
  }
})
