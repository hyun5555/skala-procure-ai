<template>
  <header class="app-header">
    <div class="header-inner">
      <router-link to="/" class="logo">
        <span class="logo-mark" aria-hidden="true">
          <i class="logo-loop logo-loop-left"></i>
          <i class="logo-loop logo-loop-right"></i>
        </span>
        <span class="logo-copy"><strong>MATERIQ</strong><small>MATERIAL INTELLIGENCE</small></span>
      </router-link>

      <!-- 네비게이션 -->
      <nav class="nav-links" v-if="auth.isAuthenticated">
        <template v-if="auth.isInstructor">
          <router-link to="/mypage" class="nav-link" :class="{ active: $route.path === '/mypage' }">품목 대시보드</router-link>
          <router-link to="/courses/new" class="nav-link" :class="{ active: $route.path === '/courses/new' }">조달 품목 등록</router-link>
        </template>
        <template v-else>
          <router-link to="/courses" class="nav-link" :class="{ active: $route.path.startsWith('/courses') }">업체 매칭</router-link>
          <router-link to="/enrollments" class="nav-link" :class="{ active: $route.path === '/enrollments' }">발주 관리</router-link>
        </template>
      </nav>

      <!-- 우측 액션 -->
      <div class="header-actions">
        <template v-if="auth.isAuthenticated">
          <span v-if="auth.isDemo" class="demo-badge">DEMO</span>
          <router-link :to="auth.isInstructor ? '/mypage' : '/enrollments'" class="user-avatar" :title="auth.user?.name">
            {{ auth.user?.name?.charAt(0) || '?' }}
          </router-link>
          <button class="btn btn-ghost btn-sm" @click="handleLogout">로그아웃</button>
        </template>
        <template v-else>
          <router-link to="/login" class="btn btn-ghost btn-sm">로그인</router-link>
          <router-link to="/login" class="btn btn-primary btn-sm">시작하기</router-link>
        </template>
      </div>
    </div>
  </header>
</template>

<script setup>
import { useAuthStore } from '@/store/auth.js'
import { useRouter } from 'vue-router'

const auth = useAuthStore()
const router = useRouter()

function handleLogout() {
  auth.logout()
  router.push('/')
}
</script>

<style scoped>
.app-header {
  position: sticky;
  top: 0;
  z-index: 100;
  background: rgba(255,255,255,0.92);
  backdrop-filter: blur(12px);
  border-bottom: 1px solid var(--color-border);
}
.header-inner {
  max-width: 1200px;
  margin: 0 auto;
  padding: 0 24px;
  height: 64px;
  display: flex;
  align-items: center;
  gap: 32px;
}
.logo {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-shrink: 0;
}
.logo-mark { position:relative; width:38px; height:34px; flex-shrink:0; }
.logo-loop { position:absolute; top:4px; width:22px; height:26px; border:5px solid #70777d; border-radius:9px; }
.logo-loop-left { left:1px; }
.logo-loop-right { right:1px; border-color:#a1a7ac; }
.logo-loop-left:after,.logo-loop-right:after { content:''; position:absolute; top:7px; width:8px; height:4px; background:#fff; }
.logo-loop-left:after { right:-8px; }
.logo-loop-right:after { left:-8px; }
.logo-copy { display:flex; flex-direction:column; line-height:1; gap:4px; }
.logo-copy strong { font-size:17px; letter-spacing:.055em; color:var(--color-text-primary); }
.logo-copy small { font-size:7px; letter-spacing:.13em; color:var(--color-text-muted); }
.nav-links {
  display: flex;
  gap: 4px;
  flex: 1;
}
.nav-link {
  padding: 6px 14px;
  border-radius: var(--radius-md);
  font-size: 14px;
  font-weight: 500;
  color: var(--color-text-secondary);
  transition: var(--transition);
}
.nav-link:hover,
.nav-link.active {
  color: var(--color-accent-dark);
  background: var(--color-primary-light);
}
@media (max-width: 720px) {
  .header-inner { padding: 0 16px; gap: 12px; }
  .logo-copy small, .nav-links { display: none; }
}
.header-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-left: auto;
}
.btn-sm {
  padding: 7px 16px;
  font-size: 13px;
}
.demo-badge { padding:4px 7px; border-radius:5px; background:var(--color-peach-soft); color:#8a634f; font-size:8px; font-weight:800; letter-spacing:.1em; }
.user-avatar {
  width: 34px;
  height: 34px;
  border-radius: 50%;
  background: var(--color-primary-light);
  color: var(--color-accent-dark);
  font-size: 13px;
  font-weight: 600;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: var(--transition);
}
.user-avatar:hover {
  background: var(--color-accent-dark);
  color: #fff;
}
</style>
