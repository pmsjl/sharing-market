<template>
  <main class="not-found-page">
    <section class="classroom-board">
      <img :src="blackboard404" alt="黑板上的 404 手绘插画" />
      <div class="board-copy">
        <span>404 · 页面未找到</span>
        <h1>好像走错教室了</h1>
        <p>这间教室没有开放，沿着走廊回到校园集市吧。</p>
        <el-button type="primary" @click="goHome">返回首页</el-button>
      </div>
    </section>
  </main>
</template>

<script setup lang="ts">
import { useRouter } from "vue-router";
import blackboard404 from "@/assets/illustrations/blackboard-404.svg";

import { GET_ROLE } from "@/utils/token";
import { getRoleHomePath } from "@/utils/roleHome";
const $router = useRouter();
const goHome = () => {
  $router.push(getRoleHomePath(GET_ROLE()));
};
</script>

<style scoped lang="scss">
.not-found-page {
  display: grid;
  width: 100%;
  min-height: 100vh;
  padding: 24px;
  place-items: center;
  color: var(--market-ink);
  background: var(--market-canvas);
}

.classroom-board {
  display: grid;
  grid-template-columns: minmax(260px, 1fr) minmax(280px, 0.9fr);
  gap: clamp(24px, 5vw, 64px);
  align-items: center;
  width: min(920px, 100%);
  padding: clamp(28px, 5vw, 58px);
  border: 0;
  border-radius: 8px;
  color: var(--market-ink);
  background: var(--market-surface);
  box-shadow: none;

  img {
    width: 100%;
    max-height: 360px;
    object-fit: contain;
    color: var(--market-chalk);
  }
}

.board-copy {
  span {
    color: var(--market-muted);
    font-family: var(--market-font-mono);
    font-size: 12px;
    font-weight: 800;
    letter-spacing: 2px;
  }

  h1 {
    margin: 12px 0;
    font-family: var(--market-font-display);
    font-size: clamp(34px, 6vw, 64px);
    line-height: 1.3;
  }

  p {
    margin: 0 0 24px;
    color: var(--market-muted);
    line-height: 1.8;
  }
}

@media (max-width: 700px) {
  .not-found-page {
    padding: 12px;
  }

  .classroom-board {
    grid-template-columns: 1fr;
    padding: 24px 16px;
    text-align: center;
  }
}
</style>
