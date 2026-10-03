<template>
  <section class="profile-settings">
    <header class="quiet-heading">
      <h1>资料设置</h1>
      <p>让同学认识你，从一个名字开始。</p>
    </header>
    <AsyncState
      :loading="profile.loading.value"
      :error="profile.error.value"
      @retry="profile.load"
    >
      <el-form
        ref="formRef"
        :model="form"
        :disabled="saving || uploading"
        label-position="top"
        @submit.prevent="save"
      >
        <el-form-item label="头像" class="avatar-field"
          ><div class="avatar-edit">
            <el-avatar :size="88" :src="form.userAvatar">{{
              form.userName.slice(0, 1) || "同学"
            }}</el-avatar
            ><el-upload
              :http-request="upload"
              :show-file-list="false"
              accept="image/*"
              :disabled="uploading || saving"
              ><el-button :loading="uploading" :disabled="saving"
                >更换头像</el-button
              ></el-upload
            >
          </div></el-form-item
        >
        <p v-if="uploadError" role="alert" class="field-error">
          {{ uploadError }}
        </p>
        <el-form-item
          label="昵称"
          prop="userName"
          class="profile-name-field"
          :rules="[
            {
              required: true,
              whitespace: true,
              message: '请填写昵称',
              trigger: 'blur'
            }
          ]"
          ><el-input v-model="form.userName"
        /></el-form-item>
        <el-form-item label="简介"
          ><el-input
            v-model="form.userProfile"
            type="textarea"
            :rows="5"
            placeholder="写点关于你的校园生活与交易偏好"
        /></el-form-item>
        <p v-if="saveError" role="alert" class="field-error">{{ saveError }}</p>
        <div class="settings-actions">
          <el-button
            type="primary"
            native-type="submit"
            :loading="saving"
            :disabled="uploading"
            >保存资料</el-button
          >
        </div>
      </el-form>
    </AsyncState>
  </section>
</template>
<script setup lang="ts">
import { onMounted, reactive, ref } from "vue";
import { ElMessage, FormInstance } from "element-plus";
import AsyncState from "@/components/AsyncState/index.vue";
import { useRemote, responseData } from "@/composables/useRemote";
import {
  getUserVoByIdUsingGet,
  updateMyUserUsingPost
} from "@/api/userController";
import { uploadFileUsingPost } from "@/api/fileController";
import { GET_ID } from "@/utils/token";
import useUserStore from "@/store/modules/user";
const store = useUserStore();
const formRef = ref<FormInstance>();
const form = reactive({ userName: "", userAvatar: "", userProfile: "" });
const saving = ref(false),
  uploading = ref(false),
  saveError = ref(""),
  uploadError = ref("");
const profile = useRemote<API.UserVO>({}, async () => {
  const result = responseData(await getUserVoByIdUsingGet({ id: GET_ID() }));
  Object.assign(form, {
    userName: result.userName || "",
    userAvatar: result.userAvatar || "",
    userProfile: result.userProfile || ""
  });
  return result;
});
async function upload(options: any) {
  if (uploading.value || saving.value) return;
  uploading.value = true;
  uploadError.value = "";
  try {
    form.userAvatar = responseData(
      await uploadFileUsingPost({ biz: "user_avatar" }, {}, options.file)
    );
  } catch (error) {
    uploadError.value = "头像上传失败，请重试。原头像已保留。";
  } finally {
    uploading.value = false;
  }
}
async function save() {
  if (saving.value || uploading.value) return;
  saving.value = true;
  saveError.value = "";
  try {
    if (!(await formRef.value?.validate().catch(() => false))) return;
    responseData(await updateMyUserUsingPost({ ...form }));
    await store.updateAvatar(form.userAvatar);
    await store.updateUserName(form.userName);
    ElMessage.success("资料已保存");
  } catch (error) {
    saveError.value =
      error instanceof Error ? error.message : "保存失败，请重试";
  } finally {
    saving.value = false;
  }
}
onMounted(() => void profile.load());
</script>
<style scoped lang="scss">
.profile-settings {
  max-width: 680px;
}
.avatar-field {
  padding-bottom: 20px;
  border-bottom: 1px solid var(--market-line);
}
.profile-name-field :deep(.el-input__inner) {
  font-size: 20px;
  font-weight: 600;
}
.settings-actions {
  padding-top: 12px;
}
.avatar-edit {
  display: flex;
  gap: 22px;
  align-items: center;
  margin-bottom: 12px;
}
.field-error {
  color: var(--el-color-danger);
  font-size: 13px;
  margin: 10px 0 18px;
}
</style>
