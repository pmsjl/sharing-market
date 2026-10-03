<template>
  <div class="auth-market-page">
    <header class="auth-market-header">
      <div class="auth-market-brand">
        <img :src="setting.logo" alt="校园集市标识" />
        <div><strong>校园集市</strong><span>SHARING MARKET</span></div>
      </div>
    </header>
    <section class="auth-market-shell">
      <aside class="auth-market-showcase">
        <div class="showcase-copy">
          <span>好物循环 · 校园日常</span>
          <h1>你的闲置，<br />是另一位同学的刚好。</h1>
          <p>
            从一本旧书，到一段新故事。<br />在这里，遇见好物，也遇见同路的同学。
          </p>
        </div>
        <img
          v-if="!imageFailed"
          src="/generated/carousel-dorm.png"
          alt="充满生活气息的校园宿舍"
          class="campus-scene"
          @error="imageFailed = true"
        />
        <div v-else class="scene-fallback" role="img" aria-label="校园好物循环">
          <span>书本 · 灯光 · 校园生活</span><strong>让喜欢，继续。</strong>
        </div>
        <span class="auth-sticker" aria-hidden="true">下一站，你的书桌</span>
      </aside>
      <main class="auth-market-panel" aria-label="校园集市账号"><slot /></main>
    </section>
  </div>
</template>
<script setup lang="ts">
import { ref } from "vue";
import setting from "@/setting";
const imageFailed = ref(false);
</script>
<style scoped lang="scss">
.auth-market-page {
  min-height: 100dvh;
  padding: 30px clamp(20px, 5vw, 80px);
  background: var(--market-canvas);
  color: var(--market-ink);
}
.auth-market-header {
  max-width: 1320px;
  margin: 0 auto;
}
.auth-market-brand {
  display: flex;
  gap: 12px;
  align-items: center;
  img {
    width: 42px;
    height: 42px;
    object-fit: contain;
  }
  strong {
    display: block;
    font-size: 20px;
    font-weight: 750;
  }
  span {
    display: block;
    font-size: 9px;
    letter-spacing: 2px;
    margin-top: 6px;
    color: var(--market-muted);
  }
}
.auth-market-shell {
  display: grid;
  grid-template-columns: minmax(0, 1.15fr) minmax(0, 0.85fr);
  align-items: center;
  gap: clamp(40px, 7vw, 110px);
  max-width: 1320px;
  margin: 48px auto;
}
.auth-market-showcase {
  position: relative;
  min-width: 0;
}
.showcase-copy {
  span {
    font-size: 12px;
    color: var(--market-muted);
    letter-spacing: 2px;
  }
  h1 {
    font-size: clamp(30px, 3.5vw, 49px);
    line-height: 1.5;
    font-weight: 750;
    margin: 18px 0;
    letter-spacing: -1px;
  }
  p {
    color: var(--market-muted);
    line-height: 1.9;
    font-size: 14px;
    margin-bottom: 28px;
  }
}
.campus-scene {
  display: block;
  width: 100%;
  max-height: 330px;
  object-fit: cover;
  border-radius: 12px;
}
.scene-fallback {
  min-height: 250px;
  background: var(--market-surface-soft);
  display: grid;
  place-content: center;
  gap: 18px;
  color: var(--market-muted);
  strong {
    font-size: 30px;
    color: var(--market-ink);
  }
}
.auth-sticker {
  position: absolute;
  right: 12px;
  bottom: -12px;
  padding: 10px 16px;
  background: var(--market-sticker-yellow, #ffe58b);
  color: #493919;
  transform: rotate(-4deg);
  font-family: var(--market-font-handwritten, inherit);
}
.auth-market-panel {
  display: flex;
  justify-content: center;
  padding: 24px 0;
  min-width: 0;
}
:deep(.auth-entry-form) {
  width: min(410px, 100%);
}

:deep(.auth-form-kicker) {
  display: inline-flex;
  min-height: 30px;
  margin-bottom: 18px;
  padding: 0;
  align-items: center;
  gap: 7px;
  border-radius: 0;
  color: var(--market-primary);
  font-size: 13px;
  font-weight: 800;
  background: transparent;
}

:deep(.auth-form-kicker::before) {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: currentColor;
  content: "";
}

:deep(.auth-form-heading) {
  margin-bottom: 28px;
}

:deep(.auth-form-heading h2) {
  margin: 0;
  color: var(--market-ink);
  font-family: var(--market-font-display);
  font-size: clamp(29px, 3vw, 36px);
  font-weight: 750;
  letter-spacing: -0.025em;
  line-height: 1.2;
}

:deep(.auth-form-heading p) {
  margin: 9px 0 0;
  color: var(--market-muted);
  font-size: 14px;
  line-height: 1.7;
}

:deep(.auth-entry-form .el-form-item) {
  margin-bottom: 20px;
}

:deep(.auth-entry-form .el-form-item__label) {
  padding-bottom: 8px;
  color: var(--market-ink);
  font-weight: 800;
  line-height: 1.3;
}

:deep(.auth-entry-form .el-input__wrapper) {
  min-height: 50px;
  padding: 1px 15px;
  border-radius: 12px;
  box-shadow: 0 0 0 1px var(--market-line) inset;
  transition: box-shadow var(--market-dur-fast) var(--market-ease-standard),
    background var(--market-dur-fast) var(--market-ease-standard);
}

:deep(.auth-entry-form .el-input__wrapper:hover) {
  box-shadow: 0 0 0 1px var(--market-line-strong) inset;
}

:deep(.auth-entry-form .el-input__wrapper.is-focus) {
  box-shadow: 0 0 0 1px var(--market-primary) inset, var(--market-focus);
}

:deep(.auth-entry-form .el-input__inner) {
  min-width: 0;
  font-size: 15px;
}

:deep(.auth-submit) {
  width: 100%;
  min-height: 50px;
  margin-top: 4px;
  border-radius: 12px;
  font-size: 15px;
  font-weight: 750;
  box-shadow: 0 12px 24px rgba(37, 99, 235, 0.22);
}

:deep(.auth-switch) {
  margin: 18px 0 0;
  color: var(--market-muted);
  font-size: 14px;
  text-align: center;
}

:deep(.auth-switch-link) {
  min-height: 44px;
  padding: 0 6px;
  border: 0;
  color: var(--market-primary);
  font-weight: 800;
  background: transparent;
  cursor: pointer;
}

:deep(.auth-switch-link:hover) {
  color: var(--market-primary-hover);
  text-decoration: underline;
  text-underline-offset: 3px;
}

:deep(.auth-switch-link:disabled) {
  opacity: 0.45;
  cursor: not-allowed;
}

:deep(.auth-security) {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 18px;
  color: var(--market-faint);
  font-size: 12px;
}

:deep(.auth-security::before),
:deep(.auth-security::after) {
  height: 1px;
  flex: 1;
  background: var(--market-line);
  content: "";
}

@media (max-width: 900px) {
  .auth-market-shell {
    gap: 36px;
  }
  .showcase-copy h1 {
    font-size: 30px;
  }
}
@media (max-width: 700px) {
  .auth-market-page {
    padding: 24px;
  }
  .auth-market-shell {
    display: flex;
    flex-direction: column;
    margin: 30px auto;
    gap: 12px;
  }
  .auth-market-showcase {
    width: 100%;
  }
  .showcase-copy {
    h1 {
      font-size: 26px;
      margin: 8px 0;
      br {
        display: none;
      }
    }
    > span,
    p {
      display: none;
    }
  }
  .campus-scene,
  .scene-fallback,
  .auth-sticker {
    display: none;
  }
  .auth-market-panel {
    width: 100%;
  }
}
</style>
