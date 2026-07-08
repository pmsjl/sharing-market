<template>
  <div class="my-posts">
    <div class="my-posts-toolbar">
      <el-input
        v-model="searchText"
        clearable
        placeholder="搜索我的攻略"
        @clear="handleSearch"
        @keyup.enter="handleSearch"
      >
        <template #append>
          <el-button :icon="Search" @click="handleSearch" />
        </template>
      </el-input>
      <el-button class="toolbar-button" @click="loadMyPosts">刷新</el-button>
    </div>

    <el-empty
      v-if="!loading && postList.length === 0"
      description="还没有发布攻略"
    />

    <div v-else class="post-list" v-loading="loading">
      <article v-for="post in postList" :key="post.id" class="post-item">
        <div class="post-main" @click="goToPostDetail(post.id)">
          <div class="post-title-row">
            <h3>{{ post.title }}</h3>
            <span>{{ post.createTime || "-" }}</span>
          </div>
          <p>{{ truncateContent(post.content || "", 120) }}</p>
          <div class="post-footer">
            <div class="post-tags">
              <el-tag
                v-for="tag in post.tagList || []"
                :key="tag"
                size="small"
                type="info"
              >
                {{ tag }}
              </el-tag>
            </div>
            <div class="post-stats">
              <span>点赞 {{ post.thumbNum || 0 }}</span>
              <span>收藏 {{ post.favourNum || 0 }}</span>
            </div>
          </div>
        </div>

        <div class="post-actions">
          <el-button
            class="action-button"
            size="small"
            @click="goToPostDetail(post.id)"
            >查看</el-button
          >
          <el-button
            class="action-button"
            size="small"
            type="primary"
            @click="openEditDialog(post)"
          >
            编辑
          </el-button>
          <el-popconfirm
            title="确定删除这篇攻略吗？"
            @confirm="deleteMyPost(post.id)"
          >
            <template #reference>
              <el-button class="action-button" size="small" type="danger">
                删除
              </el-button>
            </template>
          </el-popconfirm>
        </div>
      </article>
    </div>

    <div class="market-pagination">
      <el-pagination
        small
        v-model:current-page="queryParams.current"
        v-model:page-size="queryParams.pageSize"
        :total="total"
        layout="total, prev, pager, next, jumper"
        @current-change="loadMyPosts"
        @size-change="loadMyPosts"
      />
    </div>

    <el-dialog
      v-model="editDialogVisible"
      title="编辑我的攻略"
      fullscreen
      class="post-edit-dialog"
      @closed="resetEditForm"
    >
      <el-form :model="editForm" label-width="80px" class="post-edit-form">
        <el-form-item label="标题">
          <el-input v-model="editForm.title" maxlength="80" show-word-limit />
        </el-form-item>
        <el-form-item label="标签">
          <el-input-tag
            v-model="editForm.tags"
            :max="5"
            :validate="validateTag"
            placeholder="请输入标签"
          />
        </el-form-item>
        <el-form-item label="内容">
          <MdEditor
            class="post-edit-md"
            :modelValue="editForm.content"
            previewTheme="github"
            showCodeRowNumber
            @on-change="handleContentChange"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitEdit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import "md-editor-v3/lib/style.css";
import { onMounted, ref } from "vue";
import { useRouter } from "vue-router";
import { Search } from "@element-plus/icons-vue";
import { ElMessage } from "element-plus";
import { MdEditor } from "md-editor-v3";
import {
  deletePostUsingPost,
  editPostUsingPost,
  listMyPostVoByPageUsingPost
} from "@/api/postController";

const router = useRouter();
const loading = ref(false);
const postList = ref<API.PostVO[]>([]);
const total = ref(0);
const searchText = ref("");
const editDialogVisible = ref(false);

const queryParams = ref({
  current: 1,
  pageSize: 10
});

const editForm = ref<API.PostEditRequest>({
  id: undefined,
  title: "",
  content: "",
  tags: []
});

const loadMyPosts = async () => {
  loading.value = true;
  try {
    const res = await listMyPostVoByPageUsingPost({
      searchText: searchText.value,
      current: queryParams.value.current,
      pageSize: queryParams.value.pageSize
    });
    if (res.code === 200 && res.data) {
      postList.value = res.data.records || [];
      total.value = Number(res.data.total || 0);
      return;
    }
    postList.value = [];
    total.value = 0;
    ElMessage.error("获取我的攻略失败");
  } catch (error) {
    ElMessage.error("获取我的攻略失败");
  } finally {
    loading.value = false;
  }
};

const handleSearch = () => {
  queryParams.value.current = 1;
  loadMyPosts();
};

const goToPostDetail = (postId?: string) => {
  if (!postId) return;
  router.push({ name: "PostDetail", params: { id: postId } });
};

const openEditDialog = (post: API.PostVO) => {
  editForm.value = {
    id: post.id,
    title: post.title || "",
    content: post.content || "",
    tags: [...(post.tagList || [])]
  };
  editDialogVisible.value = true;
};

const handleContentChange = (content: string) => {
  editForm.value.content = content;
};

const submitEdit = async () => {
  if (!editForm.value.title || !editForm.value.content) {
    ElMessage.warning("标题和内容不能为空");
    return;
  }
  try {
    const res = await editPostUsingPost({
      id: editForm.value.id,
      title: editForm.value.title,
      content: editForm.value.content,
      tags: editForm.value.tags
    });
    if (res.code !== 200) {
      ElMessage.error("编辑攻略失败");
      return;
    }
    ElMessage.success("编辑攻略成功");
    editDialogVisible.value = false;
    await loadMyPosts();
  } catch (error) {
    ElMessage.error("编辑攻略失败");
  }
};

const deleteMyPost = async (postId?: string) => {
  if (!postId) return;
  try {
    const res = await deletePostUsingPost({ id: postId });
    if (res.code !== 200) {
      ElMessage.error("删除攻略失败");
      return;
    }
    ElMessage.success("删除攻略成功");
    if (postList.value.length === 1 && queryParams.value.current > 1) {
      queryParams.value.current -= 1;
    }
    await loadMyPosts();
  } catch (error) {
    ElMessage.error("删除攻略失败");
  }
};

const resetEditForm = () => {
  editForm.value = {
    id: undefined,
    title: "",
    content: "",
    tags: []
  };
};

const validateTag = (tag: string) => {
  if (tag.length > 10) {
    return "标签长度不能超过 10 个字符";
  }
  return true;
};

const truncateContent = (text: string, length: number) => {
  if (text.length > length) {
    return `${text.slice(0, length)}...`;
  }
  return text;
};

onMounted(() => {
  loadMyPosts();
});
</script>

<style scoped lang="scss">
.my-posts {
  display: grid;
  gap: 16px;
}

.my-posts-toolbar {
  display: grid;
  grid-template-columns: minmax(220px, 420px) auto;
  gap: 12px;
  align-items: center;
  justify-content: start;
}

.toolbar-button,
.action-button {
  min-height: 34px;
  padding: 7px 14px;
}

.post-list {
  display: grid;
  gap: 14px;
  min-height: 120px;
}

.post-item {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 16px;
  padding: 18px;
  border: 1px solid var(--market-line);
  border-radius: 8px;
  background: var(--market-surface);
}

.post-main {
  min-width: 0;
  cursor: pointer;

  p {
    margin: 10px 0;
    color: var(--market-muted);
    line-height: 1.7;
    word-break: break-word;
  }
}

.post-title-row {
  display: flex;
  gap: 12px;
  align-items: baseline;
  justify-content: space-between;

  h3 {
    margin: 0;
    font-size: 18px;
    font-weight: 900;
  }

  span {
    flex: none;
    color: var(--market-muted);
    font-size: 13px;
  }
}

.post-footer {
  display: flex;
  gap: 12px;
  align-items: center;
  justify-content: space-between;
}

.post-tags,
.post-stats,
.post-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
}

.post-stats {
  color: var(--market-muted);
  font-size: 13px;
  font-weight: 800;
}

.post-actions {
  align-content: start;
  justify-content: flex-end;
  min-width: 190px;
}

:deep(.post-edit-dialog) {
  display: flex;
  flex-direction: column;
}

:deep(.post-edit-dialog .el-dialog__body) {
  flex: 1;
  min-height: 0;
  padding: 18px 24px;
}

.post-edit-form {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
}

.post-edit-md {
  height: calc(100vh - 260px);
  min-height: 420px;
  width: 100%;
}

@media (max-width: 760px) {
  .my-posts-toolbar,
  .post-item {
    grid-template-columns: 1fr;
  }

  .post-title-row,
  .post-footer {
    align-items: flex-start;
    flex-direction: column;
  }

  .post-actions {
    justify-content: flex-start;
    min-width: 0;
  }

  .post-edit-md {
    height: calc(100vh - 300px);
    min-height: 320px;
  }
}
</style>
