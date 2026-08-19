<template>
  <div class="market-page notice-container">
    <section class="campus-newsflash" aria-label="校园快讯">
      <div class="newsflash-mark" aria-hidden="true">
        <svg viewBox="0 0 24 24">
          <path d="M4 10v4l3 1.5 8 3V5.5l-8 3L4 10Z" />
          <path
            d="M15 9.2c2.2.6 3.4 1.5 3.4 2.8s-1.2 2.2-3.4 2.8M7 15.5 8.4 20h3"
          />
        </svg>
      </div>
      <div class="newsflash-copy">
        <span>CAMPUS BULLETIN · 校园快讯</span>
        <strong>课间路过公告墙，看看市集最近发生了什么</strong>
        <p>平台通知、交易提醒和校园市集动态都集中在这里。</p>
      </div>
      <div class="newsflash-count">
        <b>{{ noticeList.length }}</b>
        <span>则公告</span>
      </div>
    </section>

    <section class="notice-board">
      <header class="board-heading">
        <div>
          <span class="board-route">MARKET INFO DESK / 市集信息处</span>
          <h1>校园公告墙</h1>
          <p>按发布时间陈列，第一张是目前最新的公告。</p>
        </div>
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
          :class="{ 'is-latest': index === 0 }"
          :style="{ '--notice-order': index }"
        >
          <div class="paper-pin" aria-hidden="true"></div>
          <div class="paper-meta">
            <span class="notice-number"
              >NOTICE {{ String(index + 1).padStart(2, "0") }}</span
            >
            <span v-if="index === 0" class="latest-stamp">最新</span>
            <time>{{ item.createTime }}</time>
          </div>
          <h2>{{ item.noticeTitle }}</h2>
          <p class="notice-content">{{ item.noticeContent }}</p>
          <footer class="notice-publisher">
            <el-avatar :size="30" :src="getNoticePublisherAvatar(item)">
              {{ getNoticePublisherInitial(item) }}
            </el-avatar>
            <span>
              <small>发布管理员</small>
              <b>{{ getNoticePublisherName(item) }}</b>
            </span>
            <em aria-hidden="true">校园市集</em>
          </footer>
        </article>

        <div v-if="!loading && !noticeList.length" class="notice-empty">
          <div aria-hidden="true" class="empty-board-icon"><span></span></div>
          <strong>公告墙暂时留白</strong>
          <p>目前没有新的校园公告，稍后课间再来看看。</p>
        </div>
      </div>
    </section>
  </div>
</template>

<script setup>
import { onMounted, ref } from "vue";
import { ElMessage } from "element-plus";
import { listNoticeVoByPageUsingPost } from "@/api/noticeController";

const noticeList = ref([]);
const loading = ref(true);
const getNoticePublisherName = (item) =>
  item?.user?.userName ||
  (item?.noticeAdminId ? `管理员 ${item.noticeAdminId}` : "管理员");
const getNoticePublisherAvatar = (item) => item?.user?.userAvatar || "";
const getNoticePublisherInitial = (item) =>
  getNoticePublisherName(item).slice(0, 1);
const getNoticeList = async () => {
  loading.value = true;
  try {
    const res = await listNoticeVoByPageUsingPost({ current: 1, pageSize: 15 });
    noticeList.value = res.data.records || [];
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
  display: grid;
  gap: 22px;
  min-width: 0;
}
.campus-newsflash {
  position: relative;
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  align-items: center;
  gap: 18px;
  min-height: 112px;
  padding: 18px 22px;
  overflow: hidden;
  border: 1px solid var(--market-line);
  border-radius: 16px;
  color: var(--market-ink);
  background: linear-gradient(
      112deg,
      var(--market-primary-soft),
      transparent 54%
    ),
    var(--market-surface);
  box-shadow: var(--market-shadow-soft);
  animation: flash-arrive 0.42s var(--market-ease-standard) both;
  &::after {
    position: absolute;
    right: -22px;
    bottom: -45px;
    width: 154px;
    height: 104px;
    border: 15px solid rgba(37, 99, 235, 0.08);
    border-radius: 50%;
    content: "";
    transform: rotate(-12deg);
  }
}
.newsflash-mark {
  display: grid;
  width: 64px;
  height: 64px;
  place-items: center;
  border: 1px solid rgba(37, 99, 235, 0.2);
  border-radius: 12px 20px 12px 20px;
  color: #fff;
  background: var(--market-primary);
  box-shadow: 0 12px 24px rgba(37, 99, 235, 0.2);
  transform: rotate(-3deg);
  svg {
    width: 34px;
    height: 34px;
    fill: none;
    stroke: currentColor;
    stroke-linecap: round;
    stroke-linejoin: round;
    stroke-width: 1.7;
  }
}
.newsflash-copy {
  min-width: 0;
  > span {
    color: var(--market-orange);
    font-family: var(--market-font-mono);
    font-size: 10px;
    font-weight: 800;
    letter-spacing: 1.5px;
  }
  strong {
    display: block;
    margin-top: 4px;
    font-family: var(--market-font-display);
    font-size: clamp(19px, 2.2vw, 25px);
    line-height: 1.25;
  }
  p {
    margin: 4px 0 0;
    color: var(--market-muted);
    font-size: 13px;
  }
}
.newsflash-count {
  position: relative;
  z-index: 1;
  display: grid;
  min-width: 76px;
  padding: 9px 13px;
  place-items: center;
  border: 1px dashed var(--market-line-strong);
  border-radius: 9px;
  background: var(--market-yellow-soft);
  transform: rotate(2deg);
  b {
    color: var(--market-primary);
    font-family: var(--market-font-mono);
    font-size: 25px;
    line-height: 1;
  }
  span {
    margin-top: 4px;
    color: var(--market-muted);
    font-size: 11px;
    font-weight: 700;
  }
}
.notice-board {
  position: relative;
  padding: clamp(20px, 3vw, 34px);
  overflow: hidden;
  border: 1px solid var(--market-line);
  border-radius: 18px;
  background: linear-gradient(
      rgba(255, 255, 255, 0.72),
      rgba(255, 255, 255, 0.72)
    ),
    repeating-linear-gradient(
      0deg,
      transparent 0 31px,
      rgba(37, 99, 235, 0.07) 31px 32px
    ),
    var(--market-surface-soft);
  box-shadow: var(--market-shadow);
  &::before {
    position: absolute;
    top: 0;
    right: 0;
    left: 0;
    height: 8px;
    background: linear-gradient(
      90deg,
      var(--market-primary) 0 67%,
      var(--market-orange) 67% 83%,
      var(--market-yellow) 83%
    );
    content: "";
  }
}
.board-heading {
  position: relative;
  z-index: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 18px;
  margin-bottom: 23px;
  padding: 7px 2px 18px;
  border-bottom: 1px dashed var(--market-line-strong);

  > div {
    width: 100%;
    text-align: center;
  }
  h1 {
    margin: 5px 0 0;
    font-family: var(--market-font-display);
    font-size: clamp(28px, 4vw, 40px);
    line-height: 1.1;
  }
  p {
    margin: 8px 0 0;
    color: var(--market-muted);
  }
}
.board-route {
  color: var(--market-primary);
  font-family: var(--market-font-mono);
  font-size: 10px;
  font-weight: 800;
  letter-spacing: 1.3px;
}
.notice-stack {
  position: relative;
  z-index: 1;
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 18px;
  min-height: 180px;
}
.notice-paper {
  --paper-tilt: -0.35deg;
  position: relative;
  display: flex;
  min-width: 0;
  min-height: 260px;
  padding: 25px 24px 20px;
  flex-direction: column;
  border: 1px solid var(--market-line);
  border-radius: 6px 14px 8px 12px;
  color: var(--market-ink);
  background: var(--market-surface);
  box-shadow: 0 12px 25px rgba(30, 64, 109, 0.1);
  transform: rotate(var(--paper-tilt));
  animation: note-pin 0.46s var(--market-ease-spring) both;
  animation-delay: calc(min(var(--notice-order), 6) * 45ms);
  &:nth-child(2n) {
    --paper-tilt: 0.35deg;
  }
  &:hover {
    border-color: rgba(37, 99, 235, 0.35);
    box-shadow: var(--market-shadow-lift);
    transform: translateY(-4px) rotate(0);
  }
  &.is-latest {
    border-color: rgba(249, 115, 22, 0.34);
    background: linear-gradient(
      145deg,
      var(--market-yellow-soft),
      var(--market-surface) 38%
    );
  }
  h2 {
    margin: 15px 0 10px;
    font-family: var(--market-font-display);
    font-size: 22px;
    line-height: 1.35;
    overflow-wrap: anywhere;
  }
}
.paper-pin {
  position: absolute;
  top: -7px;
  left: 50%;
  width: 17px;
  height: 17px;
  border: 3px solid rgba(255, 255, 255, 0.72);
  border-radius: 50%;
  background: var(--market-primary);
  box-shadow: 0 5px 9px rgba(30, 64, 109, 0.22);
  transform: translateX(-50%);
}
.notice-paper.is-latest .paper-pin {
  background: var(--market-orange);
}
.paper-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--market-muted);
  font-size: 11px;
}
.paper-meta time {
  margin-left: auto;
}
.notice-number {
  color: var(--market-primary);
  font-family: var(--market-font-mono);
  font-weight: 800;
  letter-spacing: 0.8px;
}
.latest-stamp {
  padding: 2px 7px;
  border: 2px solid var(--market-orange);
  border-radius: 5px;
  color: var(--market-orange);
  font-family: var(--market-font-display);
  font-weight: 900;
  transform: rotate(-4deg);
}
.notice-content {
  flex: 1;
  margin: 0;
  color: var(--market-ink);
  font-size: 16px;
  line-height: 1.78;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}
.notice-publisher {
  display: flex;
  align-items: center;
  gap: 9px;
  margin-top: 20px;
  padding-top: 13px;
  border-top: 1px dashed var(--market-line);
  > span {
    display: grid;
    gap: 1px;
  }
  small {
    color: var(--market-muted);
    font-size: 10px;
  }
  b {
    font-size: 13px;
  }
  em {
    margin-left: auto;
    color: var(--market-faint);
    font-family: var(--market-font-display);
    font-size: 11px;
    font-style: normal;
  }
}
.notice-empty {
  grid-column: 1/-1;
  display: grid;
  min-height: 300px;
  place-content: center;
  justify-items: center;
  color: var(--market-muted);
  text-align: center;
}
.notice-empty strong {
  margin-top: 12px;
  color: var(--market-ink);
  font-family: var(--market-font-display);
  font-size: 23px;
}
.notice-empty p {
  margin: 4px 0 0;
}
.empty-board-icon {
  position: relative;
  width: 76px;
  height: 54px;
  border: 4px solid var(--market-primary);
  border-radius: 6px;
  transform: rotate(-2deg);
}
.empty-board-icon::before,
.empty-board-icon::after {
  position: absolute;
  top: 10px;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--market-orange);
  content: "";
}
.empty-board-icon::before {
  left: 12px;
}
.empty-board-icon::after {
  right: 12px;
}
.empty-board-icon span {
  position: absolute;
  right: 12px;
  bottom: 10px;
  left: 12px;
  height: 2px;
  background: var(--market-line-strong);
}
html.dark .notice-board {
  background: linear-gradient(rgba(20, 31, 51, 0.88), rgba(20, 31, 51, 0.88)),
    repeating-linear-gradient(
      0deg,
      transparent 0 31px,
      rgba(96, 165, 250, 0.07) 31px 32px
    ),
    var(--market-surface);
}
@keyframes flash-arrive {
  from {
    opacity: 0;
    transform: translateY(-10px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}
@keyframes note-pin {
  from {
    opacity: 0;
    transform: translateY(18px) rotate(var(--paper-tilt));
  }
  to {
    opacity: 1;
    transform: rotate(var(--paper-tilt));
  }
}
@media (max-width: 760px) {
  .campus-newsflash {
    grid-template-columns: auto 1fr;
    padding: 16px;
  }
  .newsflash-count {
    display: none;
  }
  .notice-stack {
    grid-template-columns: 1fr;
  }
  .board-heading {
    align-items: center;
    flex-direction: column;
  }
  .notice-paper {
    min-height: 230px;
    padding-inline: 19px;
  }
}
@media (max-width: 480px) {
  .newsflash-mark {
    width: 52px;
    height: 52px;
  }
  .newsflash-copy p {
    display: none;
  }
  .notice-board {
    padding: 18px 13px;
  }
  .paper-meta {
    flex-wrap: wrap;
  }
  .paper-meta time {
    width: 100%;
    margin-left: 0;
  }
}
</style>
