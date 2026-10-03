<template>
  <div class="archive-manager">
    <header class="archive-heading">
      <div>
        <h2>已归档对话</h2>
        <p>归档会话不会出现在智能导购侧栏。恢复后可以继续咨询。</p>
      </div>
      <el-button :loading="loading" @click="loadArchivedConversations">
        刷新
      </el-button>
    </header>

    <div v-if="loadFailed" class="archive-state archive-error">
      <strong>暂时无法加载归档记录</strong>
      <p>请检查网络连接后重新加载。</p>
      <el-button type="primary" @click="loadArchivedConversations">
        重新加载
      </el-button>
    </div>

    <div v-else v-loading="loading" class="archive-content">
      <div v-if="!loading && !records.length" class="archive-state">
        <span class="archive-empty-mark" aria-hidden="true">归档夹</span>
        <strong>还没有已归档对话</strong>
        <p>在智能导购的会话侧栏中选择“归档”，记录会保存在这里。</p>
      </div>

      <ul v-else class="archive-list" aria-label="已归档 AI 对话">
        <li v-for="item in records" :key="item.id" class="archive-card">
          <div class="archive-card-copy">
            <div class="archive-title-row">
              <span class="archive-tag">已归档</span>
              <time :datetime="item.lastMessageTime">
                {{ formatTime(item.lastMessageTime) }}
              </time>
            </div>
            <h3>{{ item.title || "未命名咨询" }}</h3>
            <p>{{ item.lastMessagePreview || "这段对话还没有消息摘要" }}</p>
          </div>
          <div class="archive-actions">
            <el-button
              link
              type="primary"
              :loading="actionId === item.id && actionType === 'restore'"
              :disabled="Boolean(actionId)"
              @click="restoreConversation(item)"
            >
              恢复
            </el-button>
            <el-button
              type="danger"
              link
              :loading="actionId === item.id && actionType === 'delete'"
              :disabled="Boolean(actionId)"
              @click="deleteConversation(item)"
            >
              删除
            </el-button>
          </div>
        </li>
      </ul>

      <div v-if="total > pageSize" class="archive-pagination">
        <el-pagination
          v-model:current-page="current"
          v-model:page-size="pageSize"
          :page-sizes="[10, 20]"
          :total="total"
          layout="prev, pager, next"
          :pager-count="5"
          @current-change="loadArchivedConversations"
          @size-change="handlePageSizeChange"
        />
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, onBeforeUnmount, ref } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import {
  AiConversationVO,
  deleteAiConversation,
  listAiConversations,
  restoreAiConversation
} from "@/api/aiController";

const emit = defineEmits<{ (event: "restored"): void }>();
const records = ref<AiConversationVO[]>([]);
const current = ref(1);
const pageSize = ref(10);
const total = ref(0);
const loading = ref(false);
let loadSequence = 0;
onBeforeUnmount(() => {
  loadSequence++;
});
const loadFailed = ref(false);
const actionId = ref<string | null>(null);
const actionType = ref<"restore" | "delete" | null>(null);

const formatTime = (value?: string) => {
  if (!value) return "暂无时间";
  const date = new Date(value.replace(" ", "T"));
  if (Number.isNaN(date.getTime())) return value;
  return new Intl.DateTimeFormat("zh-CN", {
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
    hour12: false
  }).format(date);
};

const loadArchivedConversations = async () => {
  const sequence = ++loadSequence;
  loading.value = true;
  try {
    const res = await listAiConversations(
      current.value,
      pageSize.value,
      "lastMessageTime",
      "desc",
      "ARCHIVED"
    );
    if (sequence !== loadSequence) return;
    if (res.code !== 200 || !res.data) {
      throw new Error(res.message || "加载归档记录失败");
    }
    records.value = res.data.records;
    total.value = Number(res.data.total || 0);
    loadFailed.value = false;
  } catch (error: any) {
    if (sequence !== loadSequence) return;
    records.value = [];
    total.value = 0;
    loadFailed.value = true;
  } finally {
    if (sequence === loadSequence) loading.value = false;
  }
};

const handlePageSizeChange = () => {
  current.value = 1;
  loadArchivedConversations();
};

const removeRecordAndRefill = async (conversationId: string) => {
  records.value = records.value.filter((item) => item.id !== conversationId);
  total.value = Math.max(0, total.value - 1);
  if (!records.value.length && current.value > 1) {
    current.value -= 1;
  }
  await loadArchivedConversations();
};

const restoreConversation = async (item: AiConversationVO) => {
  if (actionId.value) return;
  actionId.value = item.id;
  actionType.value = "restore";
  try {
    const res = await restoreAiConversation(item.id);
    if (res.code !== 200 || res.data !== true) {
      throw new Error(res.message || "恢复失败");
    }
    await removeRecordAndRefill(item.id);
    emit("restored");
    ElMessage.success("会话已恢复，可在智能导购中继续咨询");
  } catch (error: any) {
    ElMessage.error(error?.message || "恢复失败");
  } finally {
    actionId.value = null;
    actionType.value = null;
  }
};

const deleteConversation = async (item: AiConversationVO) => {
  if (actionId.value) return;
  actionId.value = item.id;
  actionType.value = "delete";
  try {
    await ElMessageBox.confirm(
      `删除「${item.title || "未命名咨询"}」后将无法恢复。`,
      "删除归档对话",
      {
        confirmButtonText: "删除",
        cancelButtonText: "取消",
        type: "warning"
      }
    );
    actionId.value = item.id;
    actionType.value = "delete";
    const res = await deleteAiConversation(item.id);
    if (res.code !== 200 || res.data !== true) {
      throw new Error(res.message || "删除失败");
    }
    await removeRecordAndRefill(item.id);
    ElMessage.success("归档对话已删除");
  } catch (error: any) {
    if (error === "cancel" || error === "close") return;
    ElMessage.error(error?.message || "删除失败");
  } finally {
    actionId.value = null;
    actionType.value = null;
  }
};

onMounted(loadArchivedConversations);
</script>

<style scoped lang="scss">
.archive-heading {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 20px;
  h2 {
    font-size: 20px;
    margin: 0 0 10px;
  }
  p {
    line-height: 1.7;
    color: var(--market-muted);
    font-size: 13px;
  }
}
.archive-list {
  padding: 0;
  list-style: none;
}
.archive-card {
  padding: 22px 0;
  border-bottom: 1px solid var(--market-line);
}
.archive-title-row {
  display: flex;
  justify-content: space-between;
  gap: 10px;
  font-size: 11px;
  color: var(--market-muted);
}
.archive-card h3 {
  margin: 12px 0 8px;
  font-size: 16px;
  overflow-wrap: anywhere;
}
.archive-card-copy > p {
  color: var(--market-muted);
  font-size: 13px;
  line-height: 1.7;
  overflow-wrap: anywhere;
}
.archive-actions {
  display: flex;
  justify-content: flex-end;
  gap: 14px;
  margin-top: 12px;
  .el-button {
    min-height: 40px;
    margin: 0;
  }
}
.archive-state {
  display: grid;
  gap: 14px;
  padding: 36px 0;
  color: var(--market-muted);
  line-height: 1.8;
}
.archive-empty-mark {
  font-size: 24px;
}
.archive-pagination {
  display: flex;
  justify-content: center;
  margin-top: 24px;
}
</style>
