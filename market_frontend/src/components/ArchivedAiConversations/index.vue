<template>
  <div class="archive-manager">
    <header class="archive-heading">
      <div>
        <span class="market-eyebrow">AI ARCHIVE</span>
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
              <span class="archive-tag">ARCHIVED</span>
              <time :datetime="item.lastMessageTime">
                {{ formatTime(item.lastMessageTime) }}
              </time>
            </div>
            <h3>{{ item.title || "未命名咨询" }}</h3>
            <p>{{ item.lastMessagePreview || "这段对话还没有消息摘要" }}</p>
          </div>
          <div class="archive-actions">
            <el-button
              type="primary"
              :loading="actionId === item.id && actionType === 'restore'"
              :disabled="Boolean(actionId)"
              @click="restoreConversation(item)"
            >
              恢复
            </el-button>
            <el-button
              type="danger"
              plain
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
          layout="total, sizes, prev, pager, next"
          @current-change="loadArchivedConversations"
          @size-change="handlePageSizeChange"
        />
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import {
  AiConversationVO,
  deleteAiConversation,
  listAiConversations,
  restoreAiConversation
} from "@/api/aiController";

const records = ref<AiConversationVO[]>([]);
const current = ref(1);
const pageSize = ref(10);
const total = ref(0);
const loading = ref(false);
const loadFailed = ref(false);
const actionId = ref<string | null>(null);
const actionType = ref<"restore" | "delete" | null>(null);

const formatTime = (value?: string) => {
  if (!value) return "暂无时间";
  const date = new Date(value.replace(/-/g, "/"));
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
  loading.value = true;
  try {
    const res = await listAiConversations(
      current.value,
      pageSize.value,
      "lastMessageTime",
      "desc",
      "ARCHIVED"
    );
    if (res.code !== 200 || !res.data) {
      throw new Error(res.message || "加载归档记录失败");
    }
    records.value = res.data.records;
    total.value = Number(res.data.total || 0);
    loadFailed.value = false;
  } catch (error: any) {
    records.value = [];
    total.value = 0;
    loadFailed.value = true;
    if (error?.message) ElMessage.error(error.message);
  } finally {
    loading.value = false;
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
.archive-manager {
  display: grid;
  gap: 22px;
}

.archive-heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 18px;
  padding-bottom: 18px;
  border-bottom: 1px solid var(--market-line);
}

.archive-heading h2 {
  margin: 4px 0 7px;
  color: var(--market-ink);
  font-family: var(--market-font-display);
}

.archive-heading p,
.archive-state p {
  margin: 0;
  color: var(--market-muted);
  line-height: 1.7;
}

.archive-content {
  min-height: 220px;
}

.archive-list {
  display: grid;
  gap: 12px;
  padding: 0;
  margin: 0;
  list-style: none;
}

.archive-card {
  position: relative;
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 20px;
  align-items: center;
  padding: 20px 22px 20px 26px;
  overflow: hidden;
  border: 1px solid var(--market-line);
  border-radius: 10px;
  background: var(--market-card-bg);
  transition: border-color 0.2s ease, box-shadow 0.2s ease;
}

.archive-card::before {
  position: absolute;
  inset: 0 auto 0 0;
  width: 5px;
  background: repeating-linear-gradient(
    0deg,
    var(--market-green) 0 8px,
    transparent 8px 13px
  );
  content: "";
}

.archive-card:hover {
  border-color: rgba(47, 125, 92, 0.32);
  box-shadow: var(--market-shadow-soft);
}

.archive-card-copy {
  min-width: 0;
}

.archive-title-row {
  display: flex;
  align-items: center;
  gap: 10px;
  color: var(--market-muted);
  font-family: var(--market-font-mono);
  font-size: 11px;
}

.archive-tag {
  padding: 2px 7px;
  border: 1px solid rgba(47, 125, 92, 0.3);
  border-radius: 3px;
  color: var(--market-green);
  font-weight: 800;
  letter-spacing: 0.7px;
}

.archive-card h3 {
  margin: 10px 0 6px;
  overflow: hidden;
  color: var(--market-ink);
  font-size: 17px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.archive-card-copy > p {
  margin: 0;
  overflow: hidden;
  color: var(--market-muted);
  font-size: 13px;
  line-height: 1.6;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.archive-actions {
  display: flex;
  gap: 8px;
}

.archive-state {
  display: grid;
  justify-items: center;
  gap: 10px;
  padding: 58px 20px;
  border: 1px dashed var(--market-line);
  border-radius: 10px;
  text-align: center;
}

.archive-empty-mark {
  padding: 7px 12px;
  border: 1px solid rgba(47, 125, 92, 0.32);
  border-radius: 4px;
  color: var(--market-green);
  font-family: var(--market-font-display);
  font-size: 12px;
  font-weight: 900;
  letter-spacing: 2px;
  transform: rotate(-3deg);
}

.archive-error {
  background: var(--market-note-yellow-bg);
}

.archive-pagination {
  display: flex;
  justify-content: flex-end;
  padding-top: 20px;
}

@media (max-width: 720px) {
  .archive-heading,
  .archive-card {
    grid-template-columns: 1fr;
  }

  .archive-heading {
    align-items: stretch;
  }

  .archive-heading > .el-button {
    align-self: flex-start;
  }

  .archive-card {
    gap: 16px;
    padding: 18px 16px 18px 21px;
  }

  .archive-actions {
    justify-content: flex-end;
  }

  .archive-pagination {
    justify-content: center;
    overflow-x: auto;
  }
}

@media (prefers-reduced-motion: reduce) {
  .archive-card {
    transition: none;
  }
}
</style>
