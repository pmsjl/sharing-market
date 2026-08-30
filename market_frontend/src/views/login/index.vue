<template>
  <AuthMarketLayout>
    <el-form
      class="auth-entry-form"
      :model="loginForm"
      :rules="rules"
      ref="loginForms"
      label-position="top"
      aria-label="登录校园二手交易平台"
      @submit.prevent="login"
    >
      <span class="auth-form-kicker">校园账户登录</span>
      <div class="auth-form-heading">
        <h2>欢迎回来</h2>
        <p>登录你的校园账户，继续发现同校好物。</p>
      </div>

      <el-form-item label="账号" prop="userAccount">
        <el-input
          id="login-account"
          v-model="loginForm.userAccount"
          name="username"
          autocomplete="username"
          :prefix-icon="User"
          placeholder="请输入 4-15 位账号"
        />
      </el-form-item>

      <el-form-item label="密码" prop="userPassword">
        <el-input
          id="login-password"
          v-model="loginForm.userPassword"
          name="password"
          autocomplete="current-password"
          type="password"
          :prefix-icon="Lock"
          placeholder="请输入登录密码"
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
        登录平台
      </el-button>

      <p class="auth-switch">
        还没有账号？
        <button
          type="button"
          class="auth-switch-link"
          :disabled="loading"
          @click="register"
        >
          创建一个账号
        </button>
      </p>

      <div class="auth-security">安全登录 · 信息加密</div>
    </el-form>
  </AuthMarketLayout>
</template>

<script setup lang="ts">
import { Lock, User } from "@element-plus/icons-vue";
import { reactive, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import { ElMessage, ElNotification } from "element-plus";
import type { FormInstance } from "element-plus";
import AuthMarketLayout from "@/components/AuthMarketLayout/index.vue";
import { getTime } from "@/utils/time";
import useUserStore from "@/store/modules/user";
import { getSafeRedirectPath } from "@/utils/roleHome";

const loginForm = reactive({
  userAccount: "",
  userPassword: ""
});
const loginForms = ref<FormInstance>();
const loading = ref(false);
const $router = useRouter();
const $route = useRoute();
const userStore = useUserStore();

const login = async () => {
  if (loading.value || !loginForms.value) {
    return;
  }

  const valid = await loginForms.value.validate().catch(() => false);
  if (!valid) {
    ElMessage({
      type: "error",
      message: "请检查账号和密码后重试",
      duration: 1500
    });
    return;
  }

  loading.value = true;
  try {
    await userStore.userLogin(loginForm);
    await userStore.userInfo();
    const redirect: any = $route.query.redirect;
    $router.push({
      path: getSafeRedirectPath(redirect, userStore.userRole)
    });
    ElNotification({
      type: "success",
      message: "欢迎回来",
      title: `HI,${getTime()}好`,
      duration: 3000
    });
  } catch (error) {
    ElNotification({
      type: "error",
      message: "用户名或密码错误"
    });
  } finally {
    loading.value = false;
  }
};

const register = () => {
  $router.push("/register");
};

const validatorUserName = (_rule: any, value: any, callback: any) => {
  if (/^\w{4,15}$/.test(value)) {
    callback();
  } else {
    callback(new Error("账号长度应该在 4-15 位之间"));
  }
};

const validatorPassword = (_rule: any, value: any, callback: any) => {
  if (/^\w{6,10}$/.test(value)) {
    callback();
  } else {
    callback(new Error("密码长度应该在 6-10 位之间"));
  }
};

const rules = {
  userAccount: [{ trigger: "blur", validator: validatorUserName }],
  userPassword: [{ trigger: "blur", validator: validatorPassword }]
};
</script>
