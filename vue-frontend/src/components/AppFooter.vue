<template>
  <footer class="app-footer">
    <div class="footer-inner">
      <b>PROCURIX</b>
      <span>Quality data, better sourcing.</span>
      <small>© 2026 SKALA Procurement Platform</small>
    </div>
  </footer>
</template>

<script setup>
/*
  전 화면 공통 푸터.

  LandingView 에 인라인으로만 있던 것을 컴포넌트로 뺐다.
  화면마다 각자 만들면 배경색과 문구가 갈라진다 -- 레이아웃 클래스가
  실제로 그렇게 어긋나 있었다(.main-content 가 1200px 과 980px 로 나뉨).

  사용법: page-wrapper 안, main 뒤에 둔다.

    <div class="page-wrapper">
      <AppHeader />
      <main class="main-content">…</main>
      <AppFooter />
    </div>
*/
</script>

<style scoped>
.app-footer {
  padding: 28px 0;
  background: #122a27;
  color: #fff;
  margin-top: auto;   /* page-wrapper 가 flex column 일 때 바닥에 붙는다 */
}
.footer-inner {
  max-width: 1180px;
  margin: 0 auto;
  padding: 0 24px;
  display: flex;
  align-items: center;
  gap: 18px;
}
.app-footer b { letter-spacing: .12em; }
.app-footer span { font-size: 11px; color: #93aaa6; }
.app-footer small { margin-left: auto; color: #627b76; font-size: 9px; }

@media (max-width: 600px) {
  .footer-inner {
    flex-direction: column;
    align-items: flex-start;
    padding: 0 16px;
  }
  .app-footer small { margin-left: 0; }
}
</style>
