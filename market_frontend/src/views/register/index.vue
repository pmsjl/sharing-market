<template>
  <div class="auth-page register_container" ref="authPage">
    <section class="auth-board">
      <div class="auth-copy">
        <span class="market-eyebrow">加入校园公告栏</span>
        <h1>注册后，把你的闲置摊位开起来</h1>
        <p>
          一个账号即可发布商品、收藏好物、查看订单和参与校园交流。欢迎来到更有秩序的二手交易角。
        </p>
        <div class="notice-stack" aria-hidden="true">
          <div class="notice-note note-yellow">发布前整理照片和描述</div>
          <div class="notice-note note-green">交易前确认数量、价格和备注</div>
          <div class="notice-note note-blue">个人中心可持续管理订单</div>
        </div>
      </div>

      <el-form
        class="auth-form market-board"
        :model="registerForm"
        :rules="rules"
        ref="registerForms"
        label-position="top"
      >
        <div class="form-brand">
          <img src="@/assets/logo.png" alt="平台标识" />
          <div>
            <h2>创建账号</h2>
            <span>注册校园二手交易平台</span>
          </div>
        </div>

        <el-form-item label="账号" prop="userAccount">
          <el-input
            placeholder="请输入 4-15 位账号"
            :prefix-icon="User"
            v-model="registerForm.userAccount"
          />
        </el-form-item>
        <el-form-item label="密码" prop="userPassword">
          <el-input
            placeholder="请输入 8-10 位密码"
            type="password"
            :prefix-icon="Lock"
            v-model="registerForm.userPassword"
            show-password
          />
        </el-form-item>
        <el-form-item label="确认密码" prop="checkPassword">
          <el-input
            placeholder="请再次输入密码"
            type="password"
            :prefix-icon="Lock"
            v-model="registerForm.checkPassword"
            show-password
          />
        </el-form-item>
        <div class="auth-actions">
          <el-button
            :loading="loading"
            type="primary"
            class="register_btn"
            @click="register"
          >
            注册账号
          </el-button>
          <el-button class="back_login_btn" @click="backToLogin">
            返回登录
          </el-button>
        </div>
      </el-form>
    </section>
  </div>
</template>

<script setup lang="ts">
import { Lock, User } from "@element-plus/icons-vue";
import { onMounted, reactive, ref } from "vue";
import { useRouter } from "vue-router";
import { ElMessage } from "element-plus";
import { userRegisterUsingPost } from "@/api/userController";
import { animateIn } from "@/utils/motion";

const authPage = ref<HTMLElement | null>(null);
const registerForm = reactive({
  userAccount: "",
  userPassword: "",
  checkPassword: ""
});

const registerForms = ref();
const loading = ref(false);
const $router = useRouter();

const register = async () => {
  const valid = await registerForms.value.validate().catch(() => false);
  if (!valid) {
    ElMessage({
      type: "error",
      message: "表单参数不合法",
      duration: 1000
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
    callback(new Error("账号长度应该在4位-15位之间"));
  }
};

const validatorPassword = (_rule: any, value: any, callback: any) => {
  if (/^\w{8,10}$/.test(value)) {
    callback();
  } else {
    callback(new Error("密码长度应该在8位-10位之间"));
  }
};

const validatorCheckPassword = (_rule: any, value: any, callback: any) => {
  if (registerForm.userPassword != value) {
    callback(new Error("两次输入的密码不一致"));
  } else {
    callback();
  }
};

const rules = {
  userAccount: [{ trigger: "change", validator: validatorUserName }],
  userPassword: [{ trigger: "change", validator: validatorPassword }],
  checkPassword: [{ trigger: "change", validator: validatorCheckPassword }]
};

onMounted(() => {
  animateIn(authPage.value?.querySelectorAll(".auth-copy, .auth-form") || []);
});
</script>
<style scoped lang="scss">
.auth-page {
  display: grid;
  min-height: 100dvh;
  padding: 38px;
  place-items: center;
  background: var(--market-body-bg);
}

.auth-board {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(360px, 430px);
  gap: 42px;
  width: min(1080px, 100%);
  align-items: center;
}

.auth-copy {
  h1 {
    max-width: 640px;
    margin: 18px 0;
    color: var(--market-ink);
    font-size: clamp(36px, 6vw, 60px);
    font-weight: 900;
    line-height: 1.08;
  }

  p {
    max-width: 560px;
    color: var(--market-muted);
    font-size: 17px;
    line-height: 1.8;
  }
}

.notice-stack {
  display: grid;
  gap: 14px;
  max-width: 460px;
  margin-top: 34px;
}

.notice-note {
  width: fit-content;
  max-width: 100%;
  padding: 12px 16px;
  border: 1px solid rgba(35, 49, 63, 0.1);
  border-radius: 8px;
  box-shadow: var(--market-shadow-soft);
  font-weight: 800;
}

.note-green {
  margin-left: 42px;
  background: var(--market-note-green-bg);
  transform: rotate(1.5deg);
}

.note-yellow {
  background: var(--market-note-yellow-bg);
  transform: rotate(-1.5deg);
}

.note-blue {
  margin-left: 16px;
  color: #fff;
  background: var(--market-blue);
  transform: rotate(-0.8deg);
}

.auth-form {
  padding: 34px;
}

.form-brand {
  display: flex;
  align-items: center;
  gap: 14px;
  margin-bottom: 28px;

  img {
    width: 58px;
    height: 58px;
    border-radius: 8px;
    object-fit: contain;
    background: var(--market-soft-bg);
  }

  h2 {
    margin: 0;
    color: var(--market-ink);
    font-size: 28px;
    font-weight: 900;
  }

  span {
    color: var(--market-muted);
    font-size: 14px;
  }
}

.auth-actions {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
  margin-top: 4px;
}

.register_btn,
.back_login_btn {
  width: 100%;
}

@media (max-width: 860px) {
  .auth-page {
    padding: 24px 16px;
  }

  .auth-board {
    grid-template-columns: 1fr;
    gap: 24px;
  }

  .auth-copy h1 {
    font-size: 34px;
  }

  .notice-stack {
    display: none;
  }
}

@media (max-width: 520px) {
  .auth-form {
    padding: 28px 20px;
  }

  .auth-actions {
    grid-template-columns: 1fr;
  }
}
</style>
