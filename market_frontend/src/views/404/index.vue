<template>
  <main class="not-found-page">
    <section class="classroom-board">
      <img :src="blackboard404" alt="黑板上的 404 手绘插画" />
      <div class="board-copy">
        <span>ROOM NOT FOUND</span>
        <h1>好像走错教室了</h1>
        <p>这间教室没有开放，沿着走廊回到校园集市吧。</p>
        <button type="button" @click="goHome">回到集市</button>
      </div>
    </section>
  </main>
</template>

<script setup lang="ts">
import { useRouter } from "vue-router";
import blackboard404 from "@/assets/illustrations/blackboard-404.svg";

let $router = useRouter();
const goHome = () => {
  $router.push("/user/home");
};
</script>

<style scoped lang="scss">
.not-found-page {
  display: grid;
  width: 100vw;
  min-height: 100vh;
  padding: 24px;
  place-items: center;
  color: var(--market-ink);
  background: var(--market-body-bg);
}

.classroom-board {
  display: grid;
  grid-template-columns: minmax(260px, 1fr) minmax(280px, 0.9fr);
  gap: clamp(24px, 5vw, 64px);
  align-items: center;
  width: min(920px, 100%);
  padding: clamp(28px, 5vw, 58px);
  border: 12px solid #63452f;
  border-radius: 8px;
  color: var(--market-chalk);
  background: #244b3c;
  box-shadow: inset 0 0 0 2px rgba(253, 246, 227, 0.16), var(--market-shadow);

  img {
    width: 100%;
    max-height: 360px;
    object-fit: contain;
    color: var(--market-chalk);
  }
}

.board-copy {
  span {
    color: var(--market-ticket-pink);
    font-family: var(--market-font-mono);
    font-size: 12px;
    font-weight: 800;
    letter-spacing: 2px;
  }

  h1 {
    margin: 12px 0;
    font-family: var(--market-font-display);
    font-size: clamp(34px, 6vw, 64px);
    line-height: 1.05;
  }

  p {
    margin: 0 0 24px;
    color: rgba(253, 246, 227, 0.76);
    line-height: 1.8;
  }

  button {
    min-height: 44px;
    padding: 0 22px;
    border: 2px solid var(--market-ticket-pink);
    border-radius: 5px;
    color: var(--market-chalk);
    font-family: var(--market-font-display);
    font-weight: 900;
    background: transparent;
    cursor: pointer;
    transform: rotate(-2deg);
  }
}

@media (max-width: 700px) {
  .not-found-page {
    padding: 12px;
  }

  .classroom-board {
    grid-template-columns: 1fr;
    border-width: 8px;
    text-align: center;
  }
}
</style>
