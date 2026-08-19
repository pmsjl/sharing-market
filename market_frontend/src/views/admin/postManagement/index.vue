<template>
  <div class="admin-page post-admin">
    <!-- 查询区域 -->
    <el-card style="margin-bottom: 10px">
      <el-row :gutter="10">
        <el-col :span="6">
          <el-form-item label="标题">
            <el-input v-model="queryParams.title" placeholder="请输入标题" />
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="内容">
            <el-input v-model="queryParams.content" placeholder="请输入内容" />
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="标签">
            <el-input-tag
              v-model="queryParams.tags"
              placeholder="请输入标签"
              :max="5"
              :validate="validateTag"
            />
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="用户ID">
            <el-input v-model="queryParams.userId" placeholder="请输入用户ID" />
          </el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item>
            <el-button @click="resetQuery">重置</el-button>
            <el-button type="primary" @click="getPostList">查询</el-button>
            <el-button type="primary" @click="showAddDialog" :icon="Promotion">
              添加新帖子
            </el-button>
          </el-form-item>
        </el-col>
      </el-row>
    </el-card>

    <!-- 帖子列表表格 -->
    <el-card>
      <el-table :data="postList" style="width: 100%" :loading="loading">
        <el-table-column prop="title" label="标题" />
        <el-table-column
          prop="content"
          label="内容"
          width="200px"
          show-overflow-tooltip
        />
        <el-table-column prop="tags" label="标签" width="200px">
          <template #default="{ row }">
            <el-tag
              v-for="tag in row.tags"
              :key="tag"
              style="margin-right: 5px"
            >
              {{ tag }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="userId" label="用户ID" />
        <el-table-column prop="thumbNum" label="点赞数" />
        <el-table-column prop="favourNum" label="收藏数" />
        <el-table-column prop="createTime" label="创建时间" />
        <el-table-column prop="updateTime" label="更新时间" />
        <el-table-column label="操作" width="200px">
          <template #default="{ row }">
            <el-button type="primary" @click="showEditDialog(row.id)"
              >修改
            </el-button>
            <el-popconfirm
              title="你确定要删除该帖子吗？"
              @confirm="deletePost(row)"
              @cancel="cancelEvent"
            >
              <template #reference>
                <el-button type="danger">删除</el-button>
              </template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页 -->
      <el-pagination
        style="margin-top: 20px"
        background
        layout="total, sizes, prev, pager, next, jumper"
        :page-sizes="[5, 10, 15, 20]"
        :current-page="paginationConfig.current"
        :total="paginationConfig.total"
        :page-size="paginationConfig.pageSize"
        @size-change="handleSizeChange"
        @current-change="handleCurrentChange"
      />
    </el-card>

    <!-- 修改帖子：全屏编辑工作区 -->
    <el-dialog
      v-model="editDialogVisible"
      title="修改帖子"
      fullscreen
      class="admin-post-edit-dialog"
      :close-on-click-modal="false"
      @closed="resetEditField(editFormRef)"
    >
      <div class="post-edit-workspace">
        <div class="post-edit-intro">
          <span>POST EDITOR · 校园内容运营</span>
          <p>可在左侧编辑 Markdown，并在右侧实时预览帖子最终效果。</p>
        </div>
        <el-form
          ref="editFormRef"
          :model="editForm"
          label-position="top"
          class="admin-post-edit-form"
        >
          <div class="post-edit-meta">
            <el-form-item label="标题" prop="title">
              <el-input
                v-model="editForm.title"
                maxlength="80"
                show-word-limit
                placeholder="请输入帖子标题"
              />
            </el-form-item>
            <el-form-item label="标签" prop="tagList">
              <el-input-tag
                v-model="editForm.tagList"
                placeholder="请输入标签，最多 5 个"
                :max="5"
                :validate="validateTag"
              />
            </el-form-item>
          </div>
          <el-form-item label="内容" prop="content" class="post-editor-field">
            <MdEditor
              class="admin-post-edit-md"
              :modelValue="editForm.content"
              previewTheme="github"
              showCodeRowNumber
              @on-change="handleEditContentChange"
            />
          </el-form-item>
        </el-form>
      </div>
      <template #footer>
        <div class="post-edit-footer">
          <span>修改完成后点击保存，帖子内容与标签会同步更新。</span>
          <div>
            <el-button @click="editDialogVisible = false">取消</el-button>
            <el-button type="primary" @click="editPost">保存修改</el-button>
          </div>
        </div>
      </template>
    </el-dialog>

    <!-- 添加帖子的对话框 -->
    <el-dialog
      title="添加帖子"
      v-model="addDialogVisible"
      width="50%"
      @close="addDialogClosed"
    >
      <el-form :model="addForm" ref="addFormRef" label-width="100px">
        <el-form-item label="标题" prop="title">
          <el-input v-model="addForm.title" />
        </el-form-item>
        <el-form-item label="内容" prop="content">
          <MdEditor
            :modelValue="addForm.content"
            previewTheme="github"
            showCodeRowNumber
            @on-change="handleContentChange"
          />
        </el-form-item>
        <el-form-item label="标签" prop="tags">
          <el-input-tag
            v-model="addForm.tags"
            placeholder="请输入标签"
            :max="5"
            :validate="validateTag"
          />
        </el-form-item>
      </el-form>
      <span class="dialog-footer" style="margin-left: 100px">
        <slot name="footer">
          <el-button @click="addDialogVisible = false">取消</el-button>
          <el-button type="primary" @click="addPost">添加</el-button>
        </slot>
      </span>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import "md-editor-v3/lib/style.css";
import { onMounted, ref } from "vue";
import {
  ElButton,
  ElDialog,
  ElForm,
  ElFormItem,
  ElInput,
  ElMessage,
  ElTag,
  FormInstance
} from "element-plus";
import { Promotion } from "@element-plus/icons-vue";
import {
  addPostUsingPost,
  deletePostUsingPost,
  getPostVoByIdUsingGet,
  listPostByPageUsingPost,
  updatePostUsingPost
} from "@/api/postController";
import { MdEditor } from "md-editor-v3";

// 查询参数
const queryParams = ref({
  title: "",
  content: "",
  tags: [],
  userId: ""
});

// 帖子列表
const postList = ref([]);
const loading = ref<boolean>(false);
// 处理内容变化
const handleContentChange = (content) => {
  addForm.value.content = content;
};
// 处理编辑内容变化
const handleEditContentChange = (content: string) => {
  editForm.value.content = content;
};
// 分页配置
const paginationConfig = ref({
  pageSize: 10,
  total: 0,
  current: 1
});

// 对话框状态
const editDialogVisible = ref(false);
const addDialogVisible = ref(false);

// 表单引用
const editFormRef = ref<FormInstance>();
const addFormRef = ref<FormInstance>();

// 编辑表单数据
const editForm = ref({
  id: 0,
  title: "",
  content: "",
  tagList: []
});

// 添加表单数据
const addForm = ref({
  title: "",
  content: "",
  tags: []
});

// 获取帖子列表
const getPostList = async () => {
  loading.value = true;
  try {
    const res = await listPostByPageUsingPost({
      ...queryParams.value,
      current: paginationConfig.value.current,
      pageSize: paginationConfig.value.pageSize
    });
    if (res.code === 200) {
      // 处理 tags 数据
      postList.value = res.data.records.map((item) => {
        return {
          ...item,
          tags: JSON.parse(item.tags)
        };
      });
      paginationConfig.value.total = parseInt(res.data.total);
    } else {
      ElMessage.error("获取帖子列表失败");
    }
  } catch (error) {
    ElMessage.error("获取帖子列表失败");
  } finally {
    loading.value = false;
  }
};

// 重置查询条件
const resetQuery = () => {
  queryParams.value = {
    title: "",
    content: "",
    tags: [],
    userId: ""
  };
  getPostList();
};

// 显示修改对话框
const showEditDialog = async (id?: string) => {
  if (!id) return;
  loading.value = true;
  try {
    const res = await getPostVoByIdUsingGet({ id });
    if (res.code === 200) {
      // 处理 tags 数据
      editForm.value = res.data;
      editDialogVisible.value = true;
    } else {
      ElMessage.error("获取帖子信息失败");
    }
  } catch (error) {
    ElMessage.error("获取帖子信息失败");
  } finally {
    loading.value = false;
  }
};

// 修改帖子
const editPost = async () => {
  try {
    const res = await updatePostUsingPost({
      content: editForm.value.content,
      id: editForm.value.id,
      tags: editForm.value.tagList,
      title: editForm.value.title
    });
    if (res.code === 200) {
      ElMessage.success("修改帖子成功");
      editDialogVisible.value = false;
      await getPostList();
    } else {
      ElMessage.error("修改帖子失败");
    }
  } catch (error) {
    ElMessage.error("修改帖子失败");
  }
};

// 添加帖子
const addPost = async () => {
  try {
    const res = await addPostUsingPost(addForm.value);
    if (res.code === 200) {
      ElMessage.success("添加帖子成功");
      addDialogVisible.value = false;
      await getPostList();
    } else {
      ElMessage.error("添加帖子失败");
    }
  } catch (error) {
    ElMessage.error("添加帖子失败");
  }
};

// 删除帖子
const deletePost = async (row) => {
  try {
    const res = await deletePostUsingPost({ id: row.id });
    if (res.code === 200) {
      ElMessage.success("删除帖子成功");
      await getPostList();
    } else {
      ElMessage.error("删除帖子失败");
    }
  } catch (error) {
    ElMessage.error("删除帖子失败");
  }
};

// 关闭添加对话框
const addDialogClosed = () => {
  addForm.value = { title: "", content: "", tags: [] };
};

// 关闭修改对话框
const resetEditField = (formEl: FormInstance | undefined) => {
  if (!formEl) return;
  editDialogVisible.value = false;
  formEl.resetFields();
};

// 分页处理
const handleSizeChange = (size: number) => {
  paginationConfig.value.pageSize = size;
  getPostList();
};

const handleCurrentChange = (page: number) => {
  paginationConfig.value.current = page;
  getPostList();
};

// 标签验证
const validateTag = (tag: string) => {
  if (tag.length > 10) {
    return "标签长度不能超过 10 个字符";
  }
  return true;
};

// 取消删除
const cancelEvent = () => {
  ElMessage.success("取消删除成功");
};

// 显示添加对话框
const showAddDialog = () => {
  addDialogVisible.value = true;
};

// 初始化加载帖子列表
onMounted(() => {
  getPostList();
});
</script>

<style scoped lang="scss">
.post-admin {
  padding: 20px;
}

.post-edit-workspace {
  display: flex;
  height: 100%;
  min-height: 0;
  flex-direction: column;
}

.post-edit-intro {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 18px;
  margin-bottom: 16px;
  padding: 11px 14px;
  border: 1px solid var(--market-line);
  border-radius: 10px 16px 10px 16px;
  background: var(--market-primary-soft);

  span {
    color: var(--market-primary);
    font-family: var(--market-font-display);
    font-size: 13px;
    font-weight: 900;
    letter-spacing: 0.8px;
  }

  p {
    margin: 0;
    color: var(--market-muted);
    font-size: 13px;
  }
}

.admin-post-edit-form {
  display: flex;
  flex: 1;
  min-height: 0;
  flex-direction: column;
}

.post-edit-meta {
  display: grid;
  grid-template-columns: minmax(0, 1.6fr) minmax(280px, 1fr);
  gap: 18px;
}

.post-editor-field {
  display: flex;
  flex: 1;
  min-height: 0;
  flex-direction: column;
  margin-bottom: 0;

  :deep(.el-form-item__content) {
    display: flex;
    flex: 1;
    min-height: 0;
    align-items: stretch;
  }
}

.admin-post-edit-md {
  width: 100%;
  height: 100%;
  min-height: 430px;
  overflow: hidden;
  border: 1px solid var(--market-line);
  border-radius: 12px;
}

.post-edit-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 18px;

  > span {
    color: var(--market-muted);
    font-size: 13px;
  }

  > div {
    display: flex;
    gap: 10px;
  }
}

:global(.admin-post-edit-dialog) {
  display: flex;
  height: 100dvh;
  margin: 0;
  flex-direction: column;
  border: 0;
  border-radius: 0;
  background: var(--market-canvas);
}

:global(.admin-post-edit-dialog .el-dialog__header) {
  flex: none;
  margin: 0;
  padding: 17px 24px;
  border-bottom: 1px solid var(--market-line);
  background: var(--market-surface);
}

:global(.admin-post-edit-dialog .el-dialog__body) {
  flex: 1;
  min-height: 0;
  padding: 18px 24px;
  overflow: hidden;
  color: var(--market-ink);
}

:global(.admin-post-edit-dialog .el-dialog__footer) {
  flex: none;
  padding: 14px 24px;
  border-top: 1px solid var(--market-line);
  background: var(--market-surface);
}

@media (max-width: 760px) {
  .post-admin {
    padding: 12px;
  }

  .post-edit-intro {
    align-items: flex-start;
    flex-direction: column;
    gap: 3px;
  }

  .post-edit-meta {
    grid-template-columns: 1fr;
    gap: 0;
  }

  .admin-post-edit-md {
    min-height: 340px;
  }

  .post-edit-footer {
    align-items: stretch;
    flex-direction: column;

    > span {
      display: none;
    }

    > div {
      justify-content: flex-end;
    }
  }

  :global(.admin-post-edit-dialog .el-dialog__header),
  :global(.admin-post-edit-dialog .el-dialog__body),
  :global(.admin-post-edit-dialog .el-dialog__footer) {
    padding-right: 14px;
    padding-left: 14px;
  }
}
</style>
