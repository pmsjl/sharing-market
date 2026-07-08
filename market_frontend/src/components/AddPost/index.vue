<template>
  <div class="post-editor">
    <el-form :model="editForm" label-width="80px">
      <el-form-item label="标题">
        <el-input v-model="editForm.title" placeholder="请输入标题"></el-input>
      </el-form-item>
      <el-form-item label="标签">
        <el-input-tag
          v-model="editForm.tags"
          placeholder="请输入标签"
          :max="5"
          :validate="validateTag"
        />
      </el-form-item>
      <el-form-item label="内容">
        <MdEditor
          :modelValue="editForm.content"
          previewTheme="github"
          showCodeRowNumber
          @on-change="handleContentChange"
        />
      </el-form-item>
      <el-form-item>
        <div class="editor-actions">
          <el-button @click="handleReset" class="small-button">重置</el-button>
          <el-button type="primary" @click="handleSubmit" class="small-button">
            提交
          </el-button>
        </div>
      </el-form-item>
    </el-form>
  </div>
</template>

<script setup>
import { ref } from "vue";
import { MdEditor } from "md-editor-v3";
import "md-editor-v3/lib/style.css";
import { ElMessage } from "element-plus";
import { addPostUsingPost } from "@/api/postController";
import eventBus from "@/utils/eventBus";

// 表单数据
const editForm = ref({
  title: "",
  tags: [], // 标签数据
  content: ""
});

// 处理内容变化
const handleContentChange = (content) => {
  editForm.value.content = content;
};

// 处理提交
const handleSubmit = async () => {
  if (!editForm.value.title || !editForm.value.content) {
    ElMessage.error("标题和内容不能为空");
    return;
  }

  try {
    const res = await addPostUsingPost({
      ...editForm.value
    });
    if (res.code !== 200) {
      return ElMessage.error({
        duration: 1000,
        message: "发帖失败，请稍后重试"
      });
    }
    handleReset();
    ElMessage.success({
      duration: 1000,
      message: "发帖成功"
    });
    // 触发事件，通知 PostList 刷新数据
    eventBus.emit("refresh-post-list");
  } catch (error) {
    ElMessage.error("发帖失败，请稍后重试");
  }
};

// 处理重置
const handleReset = () => {
  editForm.value.title = "";
  editForm.value.tags = [];
  editForm.value.content = "";
};

// 标签验证函数
const validateTag = (tag) => {
  if (tag.length > 10) {
    return "标签长度不能超过 10 个字符";
  }
  return true;
};
</script>

<style lang="scss" scoped>
.post-editor {
  min-height: calc(100vh - 180px);
  padding: 8px 0 0;
  display: flex;
  flex-direction: column;

  .el-form {
    flex: 1;
    display: flex;
    flex-direction: column;

    .el-form-item {
      margin-bottom: 18px;

      &:last-child {
        margin-bottom: 0;
      }
    }

    .el-form-item__content {
      flex: 1;
      display: flex;
      flex-direction: column;

      .md-editor {
        flex: 1;
        display: flex;
        flex-direction: column;

        .md-editor-content {
          flex: 1;
        }
      }
    }
  }

  .el-input,
  .el-textarea {
    width: 100%;
  }

  .small-button {
    min-width: 88px;
    min-height: 36px;
    padding: 8px 18px;
  }

  .editor-actions {
    display: flex;
    justify-content: flex-end;
    gap: 10px;
    width: 100%;
  }
}

@media (max-width: 760px) {
  .post-editor {
    min-height: auto;

    .editor-actions {
      flex-direction: column-reverse;
    }

    .small-button {
      width: 100%;
    }
  }
}
</style>
