<template>
  <div class="rating-component">
    <!-- 左侧评分区域 -->
    <div class="rating-left">
      <p class="rating-text">{{ ratingHint }}</p>
      <div class="star-container">
        <el-icon
          v-for="index in 5"
          :key="index"
          :size="24"
          :color="
            index <= (hoverRating || currentRating) ? '#ffc107' : '#e4e5e9'
          "
          @click="handleRating(index)"
          @mouseover="hoverRating = index"
          @mouseleave="hoverRating = 0"
          :class="{ disabled: ratingDisabled }"
          :aria-disabled="ratingDisabled"
        >
          <StarFilled />
        </el-icon>
      </div>
      <p v-if="hasRated" class="rated-text">您已评分：{{ currentRating }} 分</p>
    </div>

    <!-- 右侧平均分区域 -->
    <div class="rating-right">
      <p class="average-rating">平均 {{ averageRating }} 分</p>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, onMounted } from "vue";
import { StarFilled } from "@element-plus/icons-vue";
import { ElMessage } from "element-plus";
import {
  addCommodityScoreUsingPost,
  getAverageScoreUsingGet,
  listMyCommodityScoreVoByPageUsingPost
} from "@/api/commodityScoreController"; // 根据实际路径导入接口
import { useRoute } from "vue-router";
import eventBus from "@/utils/eventBus";

const props = defineProps<{ disabledReason?: string }>();

// 使用路由获取当前商品 ID
const route = useRoute();
const commodityId = route.params.id as string;

// 当前用户评分
const currentRating = ref(0);
// 鼠标悬停时的评分
const hoverRating = ref(0);
// 平均分
const averageRating = ref(0);
// 用户是否已评分
const hasRated = ref(false);
const submitting = ref(false);
const checkingRating = ref(true);
const ratingDisabled = computed(
  () =>
    hasRated.value ||
    submitting.value ||
    checkingRating.value ||
    !!props.disabledReason
);
const ratingHint = computed(
  () =>
    props.disabledReason ||
    (checkingRating.value
      ? "正在检查评分状态…"
      : submitting.value
      ? "正在提交评分…"
      : hasRated.value
      ? "感谢您的评分"
      : "点此处进行评分")
);

const showRequestError = (error: unknown, fallback: string) => {
  const failure = error as { message?: string; requestMessageShown?: boolean };
  if (!failure?.requestMessageShown) {
    ElMessage.error(failure?.message || fallback);
  }
};

// 获取平均评分
const fetchAverageScore = async () => {
  try {
    const res = await getAverageScoreUsingGet({ commodityId });
    if (res.code === 200) {
      averageRating.value = res.data;
    } else {
      ElMessage.info(res.message);
    }
  } catch (error) {
    showRequestError(error, "获取平均评分失败");
  }
};

// 检查用户是否已评分
const checkUserRating = async () => {
  checkingRating.value = true;
  try {
    const res = await listMyCommodityScoreVoByPageUsingPost({
      commodityId: commodityId,
      pageSize: 1,
      current: 1
    });
    if (res.code !== 200) {
      ElMessage.error(res.message || "检查用户评分失败");
      return;
    }
    if (res.data.records.length > 0) {
      // 用户已评分
      hasRated.value = true;
      currentRating.value = res.data.records[0].score;
    } else {
      // 用户未评分
      hasRated.value = false;
    }
  } catch (error) {
    showRequestError(error, "检查用户评分失败");
  } finally {
    checkingRating.value = false;
  }
};

// 处理评分点击事件
const handleRating = async (rating: number) => {
  if (submitting.value || checkingRating.value) return;
  if (props.disabledReason) {
    ElMessage.warning(props.disabledReason);
    return;
  }
  if (hasRated.value) {
    ElMessage.warning("您已评分，不可重复评分");
    return;
  }
  submitting.value = true;
  try {
    const res = await addCommodityScoreUsingPost({
      commodityId: commodityId,
      score: rating
    });
    if (res.code === 200) {
      currentRating.value = rating;
      hasRated.value = true;
      ElMessage.success("评分成功");
      // 重新获取平均评分
      await fetchAverageScore();
      // 触发事件，通知 CommodityScoreList 刷新数据
      eventBus.emit("refresh-commodity-score-list");
    } else {
      ElMessage.error(res.message || "评分失败，请稍后重试");
      if (res.code === 40900) await checkUserRating();
    }
  } catch (error) {
    showRequestError(error, "评分失败，请稍后重试");
  } finally {
    submitting.value = false;
  }
};

// 组件加载时获取平均评分和用户评分状态
onMounted(() => {
  fetchAverageScore();
  checkUserRating();
});
</script>

<style scoped lang="scss">
.rating-component {
  display: flex;
  align-items: center;
  gap: 20px;
  justify-content: space-between;
  padding: 20px 0;
  border: 0;
  border-radius: 8px;
  color: var(--market-ink);
  background: transparent;
}

.rating-left {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
}

.rating-text {
  margin: 0;
  font-size: 14px;
  color: var(--market-muted);
  font-weight: 800;
}

.star-container {
  display: flex;
  gap: 4px;
  cursor: pointer;
  padding: 7px 12px;
  border-radius: 0;
  background: transparent;
}

.rating-right {
  display: grid;
  width: 96px;
  height: 72px;
  place-items: center;
  border: 0;
  border-radius: 0;
  font-size: 16px;
  font-weight: bold;
  color: var(--market-green);
  font-family: var(--market-font-display);
}

.average-rating {
  margin: 0;
}

.disabled {
  cursor: not-allowed;
  opacity: 0.6;
}

.rated-text {
  margin: 0;
  font-size: 14px;
  color: var(--market-green);
  font-weight: 800;
}

@media (max-width: 520px) {
  .rating-component {
    align-items: stretch;
    flex-direction: column;
  }

  .rating-right {
    align-self: center;
  }
}
</style>
