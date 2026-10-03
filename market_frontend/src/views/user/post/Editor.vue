<template>
  <section class="editor-page post-compose editorial-surface">
    <router-link :to="returnTo" class="editor-back">← 返回攻略</router-link>
    <header class="quiet-heading">
      <h1>{{ postId ? "编辑你的分享" : "写一篇校园攻略" }}</h1>
      <p>买过、用过、踩过的坑，都值得记录。</p>
    </header>
    <AsyncState :loading="loading" :error="loadError" @retry="load">
      <el-form
        ref="formRef"
        :model="form"
        :disabled="saving"
        label-position="top"
        @submit.prevent="submit"
      >
        <el-form-item
          label="标题"
          prop="title"
          class="compose-title"
          :rules="[
            {
              required: true,
              whitespace: true,
              message: '请填写标题',
              trigger: 'blur'
            },
            { max: 80, message: '标题不能超过 80 字' }
          ]"
          ><el-input
            v-model="form.title"
            maxlength="80"
            show-word-limit
            placeholder="给这段经验，起一个名字"
        /></el-form-item>
        <el-form-item
          label="话题"
          prop="tags"
          class="compose-topics"
          :rules="[{ validator: validateTags, trigger: 'change' }]"
          ><el-input-tag
            v-model="form.tags"
            :max="5"
            :maxlength="10"
            placeholder="添加话题，按回车确认（最多 5 个，每个 10 字）"
        /></el-form-item>
        <div class="quiet-tabs" role="group" aria-label="正文显示方式">
          <button
            type="button"
            :class="{ active: !preview }"
            :aria-pressed="!preview"
            @click="preview = false"
          >
            编写正文</button
          ><button
            type="button"
            :class="{ active: preview }"
            :aria-pressed="preview"
            @click="preview = true"
          >
            阅读预览
          </button>
        </div>
        <el-form-item
          prop="content"
          :rules="[
            {
              required: true,
              whitespace: true,
              message: '请填写正文',
              trigger: 'blur'
            },
            { max: 8192, message: '正文不能超过 8192 字' }
          ]"
          ><MdPreview
            v-if="preview"
            :model-value="form.content"
            preview-theme="github"
            class="compose-preview" /><MdEditor
            v-else
            v-model="form.content"
            :preview="false"
            :disabled="saving"
            :toolbars="toolbars"
            preview-theme="github"
            class="compose-editor"
        /></el-form-item>
        <p v-if="submitError" class="field-error" role="alert">
          {{ submitError }}
        </p>
        <footer class="editor-actions">
          <span class="editor-count">{{ form.content.length }} / 8192 字</span
          ><el-button type="primary" native-type="submit" :loading="saving">{{
            postId ? "保存更改" : "发布攻略"
          }}</el-button>
        </footer>
      </el-form>
    </AsyncState>
  </section>
</template>
<script setup lang="ts">
import { computed, reactive, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import { ElMessage, FormInstance } from "element-plus";
import { MdEditor, MdPreview, ToolbarNames } from "md-editor-v3";
import "md-editor-v3/lib/style.css";
import AsyncState from "@/components/AsyncState/index.vue";
import {
  addPostUsingPost,
  editPostUsingPost,
  getPostVoByIdUsingGet
} from "@/api/postController";
import { responseData } from "@/composables/useRemote";
import { useUnsavedChanges } from "@/composables/useUnsavedChanges";
import { editorReturnPath } from "@/utils/marketNavigation";
import { GET_ID } from "@/utils/token";
const route = useRoute(),
  router = useRouter();
const postId = computed(() => String(route.params.id || ""));
const returnTo = computed(() =>
  editorReturnPath(
    route.query.returnTo,
    postId.value ? "/user/account/content?view=posts" : "/user/post"
  )
);
const form = reactive({ title: "", tags: [] as string[], content: "" });
const original = ref(JSON.stringify(form));
const saving = ref(false),
  loading = ref(false),
  loadError = ref(""),
  submitError = ref(""),
  preview = ref(false);
const formRef = ref<FormInstance>();
const toolbars: ToolbarNames[] = [
  "bold",
  "italic",
  "title",
  "quote",
  "unorderedList",
  "orderedList",
  "codeRow",
  "code",
  "link",
  "image",
  "table",
  "revoke",
  "next"
];
useUnsavedChanges(
  computed(
    () =>
      !loading.value &&
      !loadError.value &&
      original.value !== JSON.stringify(form)
  ),
  saving
);
const validateTags = (
  _rule: unknown,
  tags: string[],
  callback: (error?: Error) => void
) => {
  const valid =
    tags.length <= 5 &&
    tags.every((tag) => tag.length <= 10 && tag.trim().length > 0);
  callback(valid ? undefined : new Error("最多 5 个话题，每个话题 1～10 字"));
};
let sequence = 0;
async function load() {
  const version = ++sequence;
  loading.value = true;
  loadError.value = "";
  try {
    if (postId.value) {
      const result = responseData(
        await getPostVoByIdUsingGet({ id: postId.value })
      );
      if (version !== sequence) return;
      if (String(result.userId || result.user?.id) !== String(GET_ID()))
        throw new Error("只能编辑自己发布的攻略");
      Object.assign(form, {
        title: result.title || "",
        content: result.content || "",
        tags: [...(result.tagList || [])]
      });
    } else Object.assign(form, { title: "", content: "", tags: [] });
    original.value = JSON.stringify(form);
  } catch (error) {
    if (version === sequence)
      loadError.value =
        error instanceof Error ? error.message : "加载失败，请重试";
  } finally {
    if (version === sequence) loading.value = false;
  }
}
async function submit() {
  if (saving.value || loading.value || loadError.value) return;
  saving.value = true;
  submitError.value = "";
  try {
    if (!(await formRef.value?.validate().catch(() => false))) return;
    responseData(
      postId.value
        ? await editPostUsingPost({ ...form, id: postId.value })
        : await addPostUsingPost({ ...form })
    );
    original.value = JSON.stringify(form);
    saving.value = false;
    ElMessage.success(postId.value ? "更改已保存" : "攻略已发布");
    await router.replace(returnTo.value);
  } catch (error) {
    submitError.value =
      error instanceof Error ? error.message : "保存失败，请重试";
  } finally {
    saving.value = false;
  }
}
watch(postId, () => void load(), { immediate: true });
</script>
<style scoped lang="scss">
.post-compose {
  max-width: 920px;
}
.compose-title :deep(.el-input__wrapper) {
  min-height: 66px;
  background: transparent;
  box-shadow: none;
  border-radius: 0;
  padding: 0;
  border-bottom: 1px solid var(--market-line);
}
.compose-title :deep(.el-input__inner) {
  font-size: 28px;
  height: 64px;
  font-weight: 650;
}
.compose-title :deep(.el-input__wrapper.is-focus) {
  border-bottom-color: var(--market-primary);
}
.compose-title :deep(.el-input__count-inner) {
  background: transparent;
}
.compose-topics {
  max-width: 620px;
}
@media (max-width: 600px) {
  .compose-title :deep(.el-input__inner) {
    font-size: 22px;
  }
}
.compose-editor {
  height: min(580px, 65vh);
  min-height: 320px;
  width: 100%;
  border: 1px solid var(--market-line);
  background: var(--market-surface);
}
.compose-preview {
  min-height: 320px;
  width: 100%;
  background: transparent;
  color: var(--market-ink);
  overflow-wrap: anywhere;
}
.field-error {
  color: var(--el-color-danger);
  margin: 12px 0;
}
.quiet-tabs {
  margin: 8px 0 18px;
}
@media (max-width: 600px) {
  .editor-actions {
    gap: 8px;
    .el-button {
      min-width: 76px;
      padding-inline: 12px;
    }
  }
}
</style>
