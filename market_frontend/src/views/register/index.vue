<template>
  <AuthMarketLayout>
    <el-form
      class="auth-entry-form"
      :model="registerForm"
      :rules="rules"
      ref="registerForms"
      label-position="top"
      aria-label="注册校园二手交易平台"
      @submit.prevent="register"
    >
      <span class="auth-form-kicker">创建校园账户</span>
      <div class="auth-form-heading">
        <h2>加入校园市集</h2>
        <p>创建账号，开始发布闲置、收藏好物和管理订单。</p>
      </div>

      <el-form-item label="账号" prop="userAccount">
        <el-input
          id="register-account"
          v-model="registerForm.userAccount"
          name="username"
          autocomplete="username"
          :prefix-icon="User"
          placeholder="请输入 4-15 位账号"
        />
      </el-form-item>

      <el-form-item label="密码" prop="userPassword">
        <el-input
          id="register-password"
          v-model="registerForm.userPassword"
          name="new-password"
          autocomplete="new-password"
          type="password"
          :prefix-icon="Lock"
          placeholder="请输入 8-10 位密码"
          show-password
        />
      </el-form-item>

      <el-form-item label="确认密码" prop="checkPassword">
        <el-input
          id="register-password-confirmation"
          v-model="registerForm.checkPassword"
          name="password-confirmation"
          autocomplete="new-password"
          type="password"
          :prefix-icon="Lock"
          placeholder="请再次输入密码"
          show-password
        />
      </el-form-item>

      <el-button
        :loading="loading"
        :disabled="loading"
        native-type="submit"
        type="primary"
        class="auth-submit"
      >
        注册账号
      </el-button>

      <p class="auth-switch">
        已有账号？
        <button
          type="button"
          class="auth-switch-link"
          :disabled="loading"
          @click="backToLogin"
        >
          返回登录
        </button>
      </p>

      <div class="auth-security">账号安全 · 隐私保护</div>
    </el-form>
  </AuthMarketLayout>
</template>

<script setup lang="ts">
import { Lock, User } from "@element-plus/icons-vue";
import { reactive, ref } from "vue";
import { useRouter } from "vue-router";
import { ElMessage } from "element-plus";
import type { FormInstance } from "element-plus";
import AuthMarketLayout from "@/components/AuthMarketLayout/index.vue";
import { userRegisterUsingPost } from "@/api/userController";

const registerForm = reactive({
  userAccount: "",
  userPassword: "",
  checkPassword: ""
});

const registerForms = ref<FormInstance>();
const loading = ref(false);
const $router = useRouter();

const register = async () => {
  if (loading.value || !registerForms.value) {
    return;
  }

  const valid = await registerForms.value.validate().catch(() => false);
  if (!valid) {
    ElMessage({
      type: "error",
      message: "请检查注册信息后重试",
      duration: 1500
    });
    return;
  }

  loading.value = true;
  try {
    const result: any = await userRegisterUsingPost(registerForm);
    if (result.code == 200) {
      ElMessage.success({
        message: "注册用户成功",
        duration: 1500
      });
      $router.push("/login");
    } else {
      ElMessage.error({
        message: result.message,
        duration: 1500
      });
    }
  } finally {
    loading.value = false;
  }
};

const backToLogin = () => {
  $router.push("/login");
};

const validatorUserName = (_rule: any, value: any, callback: any) => {
  if (/^\w{4,15}$/.test(value)) {
    callback();
  } else {
    callback(new Error("账号长度应该在 4-15 位之间"));
  }
};

const validatorPassword = (_rule: any, value: any, callback: any) => {
  if (/^\w{8,10}$/.test(value)) {
    callback();
  } else {
    callback(new Error("密码长度应该在 8-10 位之间"));
  }
};

const validatorCheckPassword = (_rule: any, value: any, callback: any) => {
  if (registerForm.userPassword !== value) {
    callback(new Error("两次输入的密码不一致"));
  } else {
    callback();
  }
};

const rules = {
  userAccount: [{ trigger: "blur", validator: validatorUserName }],
  userPassword: [{ trigger: "blur", validator: validatorPassword }],
  checkPassword: [{ trigger: "blur", validator: validatorCheckPassword }]
};
</script>
