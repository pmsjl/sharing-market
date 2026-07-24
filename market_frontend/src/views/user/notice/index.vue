<template>
  <div class="notice-container">
    <section
      class="broadcast-bar"
      tabindex="0"
      :aria-label="`校园广播：${text}`"
    >
      <div class="broadcast-label" aria-hidden="true">
        <svg viewBox="0 0 24 24">
          <path d="M4 10v4l3 1.5 8 3V5.5l-8 3L4 10Z" />
          <path
            d="M15 9.2c2.2.6 3.4 1.5 3.4 2.8s-1.2 2.2-3.4 2.8M7 15.5 8.4 20h3"
          />
        </svg>
        <span>
          <small>CAMPUS RADIO</small>
          校园广播
        </span>
      </div>
      <div class="broadcast-viewport" aria-hidden="true">
        <div class="broadcast-track">
          <span class="broadcast-copy"><i></i>{{ text }}</span>
          <span class="broadcast-copy"><i></i>{{ text }}</span>
        </div>
      </div>
      <span class="broadcast-status" aria-hidden="true">播送中</span>
    </section>

    <section ref="boardRef" class="notice-board">
      <span class="board-lamp board-lamp-left" aria-hidden="true"></span>
      <span class="board-lamp board-lamp-right" aria-hidden="true"></span>

      <header class="board-heading">
        <div>
          <span>STUDENT AFFAIRS · 学院市集</span>
          <h1>近期公告</h1>
        </div>
        <p>{{ noticeList.length }} 则在栏</p>
      </header>

      <div
        class="notice-stack"
        v-loading="loading"
        element-loading-text="正在整理公告"
      >
        <article
          v-for="(item, index) in noticeList"
          :key="item.id"
          class="notice-paper"
          :class="{ 'is-pinned': index === 0 }"
        >
          <span
            v-if="index === 0"
            class="gold-pin"
            aria-label="置顶公告"
          ></span>
          <div class="notice-paper-header">
            <div class="notice-heading">
              <span class="notice-index"
                >NO. {{ String(index + 1).padStart(2, "0") }}</span
              >
              <h2>{{ item.noticeTitle }}</h2>
              <time>{{ item.createTime }}</time>
            </div>
            <div class="notice-publisher">
              <el-avatar :size="24" :src="getNoticePublisherAvatar(item)">
                {{ getNoticePublisherInitial(item) }}
              </el-avatar>
              <span>
                <small>发布人</small>
                {{ getNoticePublisherName(item) }}
              </span>
            </div>
          </div>
          <div class="notice-paper-content">
            <p>{{ item.noticeContent }}</p>
          </div>
          <span class="notice-stamp" aria-hidden="true">公告</span>
        </article>

        <div v-if="!loading && !noticeList.length" class="notice-empty">
          <span aria-hidden="true">○</span>
          <strong>公告栏正在留白</strong>
          <p>目前没有新的校园公告，之后再来看看。</p>
        </div>
      </div>
    </section>
  </div>
</template>

<script setup>
import { nextTick, onMounted, ref } from "vue";
import { ElMessage } from "element-plus";
import { listNoticeVoByPageUsingPost } from "@/api/noticeController";
import { stampIn } from "@/utils/motion";

const text = ref("智能 AI 校园二手交易平台公告栏 · 记得查收最新公告");
const noticeList = ref([]);
const loading = ref(true);
const boardRef = ref(null);

const getNoticePublisherName = (item) => {
  return (
    item?.user?.userName ||
    (item?.noticeAdminId ? `管理员 ${item.noticeAdminId}` : "管理员")
  );
};

const getNoticePublisherAvatar = (item) => item?.user?.userAvatar || "";
const getNoticePublisherInitial = (item) =>
  getNoticePublisherName(item).slice(0, 1);

const animateLatestNotice = async () => {
  await nextTick();
  const firstStamp = boardRef.value?.querySelector(
    ".notice-paper:first-child .notice-stamp"
  );
  if (firstStamp) stampIn(firstStamp, 0.12);
};

const getNoticeList = async () => {
  loading.value = true;
  try {
    const res = await listNoticeVoByPageUsingPost({
      current: 1,
      pageSize: 15
    });
    noticeList.value = res.data.records || [];
    await animateLatestNotice();
  } catch (error) {
    ElMessage.error("获取公告列表失败，" + error.message);
  } finally {
    loading.value = false;
  }
};

onMounted(getNoticeList);
</script>

<style scoped lang="scss">
.notice-container {
  width: 100%;
  min-width: 0;
  overflow: hidden;
}

.broadcast-bar {
  position: relative;
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  align-items: stretch;
  min-height: 64px;
  overflow: hidden;
  border: 1px solid var(--market-line);
  border-radius: 10px;
  color: var(--market-ink);
  background: linear-gradient(90deg, rgba(224, 101, 31, 0.06), transparent 24%),
    var(--market-paper-deep);
  box-shadow: var(--market-shadow-soft);
  outline: none;

  &::before,
  &::after {
    position: absolute;
    top: -3px;
    width: 54px;
    height: 13px;
    background: rgba(217, 173, 101, 0.48);
    content: "";
  }

  &::before {
    left: 20%;
    transform: rotate(-2deg);
  }

  &::after {
    right: 19%;
    transform: rotate(2deg);
  }
}

.broadcast-bar:focus-visible {
  box-shadow: var(--market-focus);
}

.broadcast-label {
  position: relative;
  z-index: 2;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 20px 9px 17px;
  border-right: 1px solid rgba(253, 246, 227, 0.18);
  color: var(--market-chalk);
  background: #264f40;
  clip-path: polygon(0 0, 100% 0, calc(100% - 12px) 100%, 0 100%);

  svg {
    width: 27px;
    height: 27px;
    fill: none;
    stroke: currentColor;
    stroke-linecap: round;
    stroke-linejoin: round;
    stroke-width: 1.7;
  }

  span {
    display: grid;
    gap: 1px;
    min-width: 74px;
    font-family: var(--market-font-display);
    font-size: 16px;
    font-weight: 800;
    line-height: 1.1;
  }

  small {
    color: rgba(253, 246, 227, 0.62);
    font-family: var(--market-font-mono);
    font-size: 8px;
    font-weight: 700;
    letter-spacing: 0.9px;
  }
}

.broadcast-viewport {
  display: flex;
  min-width: 0;
  align-items: center;
  overflow: hidden;
  mask-image: linear-gradient(
    90deg,
    transparent,
    #000 5%,
    #000 95%,
    transparent
  );
}

.broadcast-track {
  display: flex;
  width: max-content;
  align-items: center;
  gap: 44px;
  padding-left: 24px;
  animation: broadcast-scroll 18s linear infinite;
  will-change: transform;
}

.broadcast-copy {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  flex-shrink: 0;
  color: var(--market-ink);
  font-size: 14px;
  font-weight: 650;
  letter-spacing: 0.4px;
  white-space: nowrap;

  i {
    width: 7px;
    height: 7px;
    border-radius: 50%;
    background: var(--market-orange);
    box-shadow: 0 0 0 4px rgba(224, 101, 31, 0.12);
  }
}

.broadcast-status {
  align-self: center;
  margin-right: 16px;
  padding: 4px 8px;
  border: 1px solid rgba(47, 125, 92, 0.24);
  border-radius: 999px;
  color: var(--market-green);
  font-size: 10px;
  font-weight: 800;
  background: var(--market-note-green-bg);
}

.broadcast-bar:hover .broadcast-track,
.broadcast-bar:focus .broadcast-track {
  animation-play-state: paused;
}

@keyframes broadcast-scroll {
  to {
    transform: translateX(calc(-50% - 22px));
  }
}

.notice-board {
  position: relative;
  margin-top: 24px;
  padding: 44px clamp(20px, 4vw, 58px) 42px;
  border: 11px solid #65452f;
  border-radius: 9px;
  background: radial-gradient(
      circle at 15% -8%,
      rgba(255, 214, 121, 0.35),
      transparent 25%
    ),
    radial-gradient(
      circle at 85% -8%,
      rgba(255, 214, 121, 0.35),
      transparent 25%
    ),
    linear-gradient(rgba(255, 255, 255, 0.035), rgba(85, 55, 32, 0.05)),
    var(--market-board-overlay);
  box-shadow: inset 0 0 0 3px rgba(255, 246, 227, 0.15),
    inset 0 0 35px rgba(75, 47, 27, 0.2), var(--market-shadow);

  &::before {
    position: absolute;
    inset: -7px;
    border: 2px solid rgba(255, 238, 204, 0.16);
    border-radius: 5px;
    content: "";
    pointer-events: none;
  }
}

.board-lamp {
  position: absolute;
  top: -4px;
  width: 120px;
  height: 78px;
  background: radial-gradient(
    ellipse at 50% 0,
    rgba(255, 225, 151, 0.46),
    transparent 68%
  );
  pointer-events: none;
}

.board-lamp-left {
  left: 13%;
}

.board-lamp-right {
  right: 13%;
}

.board-heading {
  position: relative;
  z-index: 1;
  display: flex;
  width: min(100%, 980px);
  align-items: end;
  justify-content: space-between;
  gap: 24px;
  margin: 0 auto 28px;
  padding: 13px 18px 12px;
  border: 1px solid rgba(253, 246, 227, 0.19);
  border-radius: 4px;
  color: var(--market-chalk);
  background: #284d3e;
  box-shadow: 0 8px 20px rgba(47, 31, 19, 0.18);
  transform: rotate(-0.35deg);

  &::before,
  &::after {
    position: absolute;
    top: -12px;
    width: 2px;
    height: 16px;
    background: rgba(253, 246, 227, 0.54);
    content: "";
  }

  &::before {
    left: 44px;
  }

  &::after {
    right: 44px;
  }

  span {
    color: rgba(253, 246, 227, 0.58);
    font-family: var(--market-font-mono);
    font-size: 9px;
    letter-spacing: 1.5px;
  }

  h1 {
    margin: 2px 0 0;
    font-family: var(--market-font-display);
    font-size: clamp(24px, 3vw, 32px);
    line-height: 1.1;
  }

  p {
    margin: 0 0 2px;
    color: rgba(253, 246, 227, 0.72);
    font-size: 12px;
  }
}

.notice-stack {
  display: grid;
  min-height: 142px;
  justify-items: center;
  gap: 24px;
}

.notice-paper {
  position: relative;
  width: min(100%, 980px);
  overflow: visible;
  border: 1px solid rgba(35, 49, 63, 0.14);
  border-radius: 3px 7px 7px 3px;
  color: var(--market-ink);
  background: linear-gradient(
      90deg,
      transparent 0 46px,
      rgba(192, 57, 43, 0.2) 46px 47px,
      transparent 47px
    ),
    repeating-linear-gradient(
      0deg,
      transparent 0 27px,
      rgba(94, 160, 181, 0.16) 27px 28px
    ),
    var(--market-surface);
  box-shadow: 0 13px 27px rgba(54, 36, 23, 0.16);
  transform: rotate(-0.18deg);

  &::before {
    position: absolute;
    top: 18px;
    bottom: 18px;
    left: 12px;
    width: 13px;
    background: radial-gradient(
      circle,
      rgba(74, 54, 40, 0.18) 0 3px,
      rgba(255, 255, 255, 0.78) 3.5px 5px,
      transparent 5.5px
    );
    background-size: 13px 28px;
    content: "";
  }
}

.notice-paper:nth-child(even) {
  transform: rotate(0.14deg);
}

.notice-paper-header {
  display: flex;
  min-height: 70px;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  padding: 15px 22px 11px 59px;
  border-bottom: 1px dashed rgba(35, 49, 63, 0.14);
}

.notice-heading {
  display: grid;
  min-width: 0;
  gap: 3px;

  h2 {
    margin: 0;
    color: var(--market-stamp-red);
    font-family: var(--market-font-display);
    font-size: 19px;
    line-height: 1.35;
    overflow-wrap: anywhere;
  }

  time {
    color: var(--market-muted);
    font-family: var(--market-font-mono);
    font-size: 11px;
  }
}

.notice-index {
  color: var(--market-orange);
  font-family: var(--market-font-mono);
  font-size: 9px;
  font-weight: 800;
  letter-spacing: 1.2px;
}

.notice-publisher {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
  color: var(--market-ink);
  font-size: 12px;
  font-weight: 750;

  > span {
    display: grid;
    gap: 1px;
  }

  small {
    color: var(--market-muted);
    font-size: 9px;
    font-weight: 500;
  }
}

.notice-paper-content {
  min-height: 82px;
  padding: 16px 92px 20px 59px;

  p {
    margin: 0;
    color: var(--market-ink);
    font-size: 15px;
    line-height: 1.9;
    overflow-wrap: anywhere;
    white-space: pre-wrap;
  }
}

.gold-pin {
  position: absolute;
  z-index: 3;
  top: -11px;
  left: 50%;
  width: 18px;
  height: 18px;
  border: 3px solid rgba(255, 255, 255, 0.72);
  border-radius: 50%;
  background: var(--market-yellow);
  box-shadow: 0 6px 8px rgba(62, 45, 24, 0.28);
  transform: translateX(-50%);
}

.notice-stamp {
  position: absolute;
  right: 20px;
  bottom: 16px;
  display: grid;
  width: 48px;
  height: 42px;
  place-items: center;
  border: 2px solid currentColor;
  border-radius: 4px;
  color: var(--market-stamp-red);
  font-family: var(--market-font-display);
  font-size: 16px;
  font-weight: 900;
  letter-spacing: 2px;
  opacity: 0.72;
  box-shadow: inset 0 0 0 2px var(--market-surface);
  transform: rotate(-8deg);
}

.notice-empty {
  display: grid;
  width: min(100%, 680px);
  justify-items: center;
  padding: 48px 24px;
  border: 1px dashed rgba(253, 246, 227, 0.35);
  border-radius: 6px;
  color: var(--market-chalk);
  text-align: center;
  background: rgba(40, 77, 62, 0.42);

  > span {
    display: grid;
    width: 38px;
    height: 38px;
    place-items: center;
    margin-bottom: 9px;
    border: 1px solid rgba(253, 246, 227, 0.48);
    border-radius: 50%;
    font-size: 22px;
  }

  p {
    margin: 5px 0 0;
    color: rgba(253, 246, 227, 0.68);
    font-size: 13px;
  }
}

.notice-stack :deep(.el-loading-mask) {
  color: var(--market-chalk);
  background: rgba(40, 77, 62, 0.68);
  backdrop-filter: blur(2px);
}

@media (prefers-reduced-motion: reduce) {
  .broadcast-track {
    width: 100%;
    padding-right: 18px;
    animation: none;
  }

  .broadcast-copy:first-child {
    min-width: 0;
    white-space: normal;
  }

  .broadcast-copy:last-child {
    display: none;
  }
}

@media (max-width: 720px) {
  .broadcast-bar {
    grid-template-columns: auto minmax(0, 1fr);
    min-height: 58px;
  }

  .broadcast-label {
    padding-right: 16px;

    small,
    span > :not(small) {
      display: none;
    }

    span {
      min-width: auto;
    }
  }

  .broadcast-status {
    display: none;
  }

  .notice-board {
    margin-top: 18px;
    padding: 34px 12px 24px;
    border-width: 8px;
  }

  .board-heading {
    align-items: flex-start;
    margin-bottom: 22px;
    padding: 11px 13px;

    p {
      font-size: 10px;
      white-space: nowrap;
    }
  }

  .notice-paper-header {
    align-items: flex-start;
    flex-direction: column;
    gap: 9px;
    padding: 15px 16px 12px 42px;
  }

  .notice-paper-content {
    padding: 13px 62px 18px 42px;

    p {
      font-size: 14px;
      line-height: 1.8;
    }
  }

  .notice-paper {
    background: linear-gradient(
        90deg,
        transparent 0 32px,
        rgba(192, 57, 43, 0.18) 32px 33px,
        transparent 33px
      ),
      repeating-linear-gradient(
        0deg,
        transparent 0 27px,
        rgba(94, 160, 181, 0.14) 27px 28px
      ),
      var(--market-surface);

    &::before {
      left: 7px;
    }
  }

  .notice-stamp {
    right: 12px;
    bottom: 13px;
    width: 42px;
    height: 37px;
    font-size: 14px;
  }
}

@media (max-width: 430px) {
  .broadcast-label span {
    display: none;
  }

  .board-heading span {
    display: none;
  }

  .board-heading h1 {
    margin: 0;
  }
}
</style>
