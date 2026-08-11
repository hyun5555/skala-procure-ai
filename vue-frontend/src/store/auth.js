import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { authApi } from '@/api/auth.js'

const AUTH_SERVER_URL = import.meta.env.VITE_AUTH_SERVER_URL || 'http://localhost:8080'

export const useAuthStore = defineStore('auth', () => {
  const accessToken = ref(sessionStorage.getItem('access_token') || null)
  const user = ref(JSON.parse(sessionStorage.getItem('user') || 'null'))
  const isDemo = ref(sessionStorage.getItem('demo_mode') === 'true')

  const isAuthenticated = computed(() => !!accessToken.value || isDemo.value)
  const isInstructor = computed(() => user.value?.role === 'INSTRUCTOR')

  function setToken(token) {
    accessToken.value = token
    sessionStorage.setItem('access_token', token)
  }

  function setUser(userData) {
    user.value = userData
    sessionStorage.setItem('user', JSON.stringify(userData))
  }

  async function fetchUser() {
    try {
      const res = await authApi.getMe()
      console.log('[AuthStore] /me response =', res.data)

      const userData = res?.data?.data ?? res?.data

      if (!userData || typeof userData !== 'object') {
        throw new Error('사용자 정보 형식이 올바르지 않습니다.')
      }

      setUser(userData)
    } catch (error) {
      console.error('[AuthStore] 사용자 정보 조회 실패:', error)
      logout(false)
    }
  }

  function logout(redirect = true) {
    accessToken.value = null
    user.value = null
    sessionStorage.removeItem('access_token')
    sessionStorage.removeItem('user')
    sessionStorage.removeItem('demo_mode')
    sessionStorage.removeItem('demo_courses')
    sessionStorage.removeItem('demo_course_statuses')
    sessionStorage.removeItem('demo_enrollments')
    sessionStorage.removeItem('order_requests')
    isDemo.value = false

    if (redirect) {
      window.location.href = '/login'
    }
  }

  function enterDemo(role = 'STUDENT') {
    isDemo.value = true
    sessionStorage.setItem('demo_mode', 'true')
    setUser(role === 'INSTRUCTOR'
      ? { id: 9002, email: 'supplier@demo.materiq.kr', name: '남강철강 주식회사', role: 'INSTRUCTOR' }
      : { id: 9001, email: 'buyer@demo.materiq.kr', name: '대한건설 구매팀', role: 'STUDENT' })
  }

  // OAuth2 Authorization Code Flow
  function redirectToLogin() {
    const params = new URLSearchParams({
      response_type: 'code',
      client_id: import.meta.env.VITE_CLIENT_ID,
      redirect_uri: import.meta.env.VITE_REDIRECT_URI,
      scope: 'openid profile read write'
    })

    window.location.href = `${AUTH_SERVER_URL}/oauth2/authorize?${params.toString()}`
  }

  async function handleCallback(code) {
    const res = await authApi.exchangeCode(code)
    console.log('[AuthStore] token response =', res.data)

    const token = res?.data?.access_token

    if (!token) {
      throw new Error('액세스 토큰을 받지 못했습니다.')
    }

    setToken(token)
    await fetchUser()
  }

  return {
    accessToken,
    user,
    isAuthenticated,
    isInstructor,
    isDemo,
    setToken,
    setUser,
    fetchUser,
    logout,
    redirectToLogin,
    handleCallback,
    enterDemo
  }
})
