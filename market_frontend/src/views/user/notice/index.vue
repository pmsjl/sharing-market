<template>
  <div class="notice-container">
    <div class="header">
      <div class="scroll-text" ref="scrollText">
        <i class="el-icon-s-opportunity"></i> {{ text }}
        <i class="el-icon-s-opportunity"></i>
      </div>
    </div>
    <div class="banner">
      <div class="banner-header"><p>近期公告</p></div>
      <div
        class="banner-main"
        v-loading="loading"
        element-loading-text="拼命加载中"
        element-loading-spinner="el-icon-loading"
        element-loading-background="rgba(0, 0, 0, 0.8)"
      >
        <div class="banner-main-item" v-for="item in noticeList" :key="item.id">
          <div class="banner-main-item-header">
            <div class="notice-heading">
              <p>{{ item.noticeTitle }}</p>
              <span>{{ item.createTime }}</span>
            </div>
            <div class="notice-publisher">
              <el-avatar :size="26" :src="getNoticePublisherAvatar(item)">
                {{ getNoticePublisherInitial(item) }}
              </el-avatar>
              <span>{{ getNoticePublisherName(item) }}</span>
            </div>
          </div>
          <div class="banner-main-item-main">
            <p>{{ item.noticeContent }}</p>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { onMounted, ref } from "vue";
import { listNoticeVoByPageUsingPost } from "@/api/noticeController";
import { ElMessage } from "element-plus";

// 初始化数据
const text = ref("智能 AI 校园二手交易平台公告栏,记得查收公告呀!谢谢");
const noticeList = ref([]);
const loading = ref(true);
// 获取公告数据
const getNoticePublisherName = (item) => {
  return (
    item?.user?.userName ||
    (item?.noticeAdminId ? `管理员 ${item.noticeAdminId}` : "管理员")
  );
};
const getNoticePublisherAvatar = (item) => {
  return item?.user?.userAvatar || "";
};
const getNoticePublisherInitial = (item) => {
  return getNoticePublisherName(item).slice(0, 1);
};
const getNoticeList = async () => {
  loading.value = true;
  try {
    const res = await listNoticeVoByPageUsingPost({
      current: 1,
      pageSize: 15
    });
    noticeList.value = res.data.records;
    loading.value = false;
  } catch (error) {
    loading.value = false;
    ElMessage.error("获取公告列表失败，" + error.message);
  }
};
// 页面加载时获取数据
onMounted(() => {
  getNoticeList();
  const containerWidth = document.querySelector(".scroll-text")?.offsetWidth;
  const textWidth = document.querySelector(".scroll-text")?.scrollWidth;

  // 如果文本宽度大于容器宽度，则启动动画
  if (textWidth && textWidth > containerWidth) {
    document
      .querySelector(".scroll-text")
      ?.style.setProperty("animation", "scroll 10s linear infinite");
  }
});
</script>

<style scoped lang="scss">
.notice-container {
  overflow: hidden;
}

.header {
  width: 100%;
  height: 50px;
  background-color: rgb(242, 242, 242);
  border-radius: 15px;
  color: black;
  text-align: center;
  line-height: 50px;
  font-size: 24px;
}

.scroll-text {
  white-space: nowrap;
  animation: scroll 10s linear infinite;
}

@keyframes scroll {
  from {
    transform: translateX(100%);
  }
  to {
    transform: translateX(-100%);
  }
}

.banner {
  width: 100%;
  height: 100%;
  margin-top: 30px;
}

.banner-header {
  width: 100%;
  height: 80px;

  p {
    color: black;
    font-size: 30px;
    text-align: center;
    line-height: 80px;
  }
}

.banner-main {
  width: 100%;
  height: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  color: skyblue;
}

.banner-main-item:nth-child(n + 2) {
  margin-top: 30px;
}

.banner-main-item:nth-child(n + 2) {
  background-color: #d1eeee;
}

.banner-main-item:nth-child(1) {
  background-color: pink;
}

.banner-main-item {
  width: 80%;
  min-height: 132px;
  overflow: hidden;
  border: 1px solid rgba(94, 160, 181, 0.35);
  border-radius: 8px;
  box-sizing: border-box;
  box-shadow: 0 10px 28px rgba(35, 49, 63, 0.08);

  .banner-main-item-header {
    width: 100%;
    min-height: 58px;
    padding: 10px 16px;
    border-bottom: 1px solid rgba(94, 160, 181, 0.35);
    box-sizing: border-box;
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 14px;

    p {
      margin: 0;
      color: rgb(175, 129, 143);
      font-size: 16px;
      font-weight: 700;
      line-height: 1.4;
    }

    span {
      color: rgba(35, 49, 63, 0.62);
      font-size: 13px;
      line-height: 1.4;
    }
  }

  .banner-main-item-main {
    width: 100%;
    min-height: 74px;
    padding: 14px 18px;
    background-color: white;
    box-sizing: border-box;
    text-align: left;

    p {
      margin: 0;
      color: rgba(35, 49, 63, 0.82);
      line-height: 1.7;
      word-break: break-word;
    }
  }
}

.notice-heading {
  min-width: 0;
  display: grid;
  gap: 4px;
}

.notice-publisher {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
  color: rgba(35, 49, 63, 0.72);
  font-size: 13px;
  font-weight: 700;
}

@media (max-width: 720px) {
  .banner-main-item {
    width: 94%;
  }

  .banner-main-item .banner-main-item-header {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>
