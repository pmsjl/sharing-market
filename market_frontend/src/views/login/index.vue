<template>
  <div class="auth-page login_container" ref="authPage">
    <section class="auth-board">
      <div class="auth-copy">
        <span class="market-eyebrow">校园闲置流转站</span>
        <h1>把闲置贴上公告栏，让好物继续发光</h1>
        <p>
          浏览同校好物、发布二手物品、管理订单与收藏，进来就能开始一场轻松的校园交易。
        </p>
        <div class="notice-stack" aria-hidden="true">
          <div class="notice-note note-green">教材 / 数码 / 生活小物</div>
          <div class="notice-note note-yellow">
            安全交易 · 校园场景 · 快速沟通
          </div>
          <div class="notice-note note-blue">AI 推荐帮你找到合适商品</div>
        </div>
      </div>

      <el-form
        class="auth-form market-board"
        :model="loginForm"
        :rules="rules"
        ref="loginForms"
        label-position="top"
      >
        <div class="form-brand">
          <img src="@/assets/logo.png" alt="平台标识" />
          <div>
            <h2>欢迎回来</h2>
            <span>登录校园二手交易平台</span>
          </div>
        </div>

        <el-form-item label="账号" prop="userAccount">
          <el-input
            :prefix-icon="User"
            v-model="loginForm.userAccount"
            placeholder="请输入 4-15 位账号"
          />
        </el-form-item>
        <el-form-item label="密码" prop="userPassword">
          <el-input
            type="password"
            :prefix-icon="Lock"
            v-model="loginForm.userPassword"
            placeholder="请输入登录密码"
            show-password
          />
        </el-form-item>

        <div class="auth-actions">
          <el-button
            :loading="loading"
            type="primary"
            class="login_btn"
            @click="login"
          >
            登录平台
          </el-button>
          <el-button :loading="loading" class="login_btn" @click="register">
            注册账号
          </el-button>
        </div>
      </el-form>
    </section>

    <p class="login_footer">
      <i class="iconfont icon-banquan"></i>
      2025 小白条出品 |
      <a href="https://beian.miit.gov.cn/#/Integrated/index">
        浙ICP备2023044565号-1
      </a>
      |
      <a href="https://beian.mps.gov.cn/#/query/webSearch">
        <img src="../../assets/images/logoPolice.png" alt="" />
        浙公网安备33028202001002号
      </a>
    </p>
  </div>
</template>

<script setup lang="ts">
import { Lock, User } from "@element-plus/icons-vue";
import { onMounted, reactive, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import { ElMessage, ElNotification } from "element-plus";
import { getTime } from "@/utils/time";
import useUserStore from "@/store/modules/user";
import { animateIn } from "@/utils/motion";
import { getSafeRedirectPath } from "@/utils/roleHome";

const authPage = ref<HTMLElement | null>(null);
const loginForm = reactive({
  userAccount: "xiaobaitiao",
  userPassword: "12345678"
});
const loginForms = ref();
const loading = ref(false);
const $router = useRouter();
const $route = useRoute();
const userStore = useUserStore();

const login = async () => {
  await loginForms.value.validate(async (valid: any) => {
    if (!valid) {
      ElMessage({
        type: "error",
        message: "表单参数不合法",
        duration: 1000
      });
    } else {
      loading.value = true;
      try {
        await userStore.userLogin(loginForm);
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
    }
  });
};

onMounted(() => {
  animateIn(authPage.value?.querySelectorAll(".auth-copy, .auth-form") || []);
});

const register = () => {
  $router.push("/register");
};

const validatorUserName = (_rule: any, value: any, callback: any) => {
  if (/^\w{4,15}$/.test(value)) {
    callback();
  } else {
    callback(new Error("账号长度应该在4位-15位之间"));
  }
};

const validatorPassword = (_rule: any, value: any, callback: any) => {
  if (/^\w{6,10}$/.test(value)) {
    callback();
  } else {
    callback(new Error("密码长度应该在6位-15位之间"));
  }
};

const rules = {
  userAccount: [{ trigger: "change", validator: validatorUserName }],
  userPassword: [{ trigger: "change", validator: validatorPassword }]
};
</script>
<style scoped lang="scss">
.auth-page {
  position: relative;
  display: grid;
  min-height: 100dvh;
  padding: 38px;
  place-items: center;
  overflow: hidden;
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
  color: var(--market-ink);

  h1 {
    max-width: 640px;
    margin: 18px 0;
    font-size: clamp(36px, 6vw, 64px);
    font-weight: 900;
    line-height: 1.05;
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
  transform: rotate(-1.2deg);
}

.note-green {
  background: var(--market-note-green-bg);
}

.note-yellow {
  margin-left: 48px;
  background: var(--market-note-yellow-bg);
  transform: rotate(1.5deg);
}

.note-blue {
  margin-left: 18px;
  color: #fff;
  background: var(--market-blue);
  transform: rotate(-0.6deg);
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

.login_btn {
  width: 100%;
}

.login_footer {
  position: absolute;
  bottom: 16px;
  left: 50%;
  display: flex;
  align-items: center;
  gap: 6px;
  width: min(680px, calc(100% - 28px));
  justify-content: center;
  color: rgba(35, 49, 63, 0.68);
  font-size: 13px;
  transform: translateX(-50%);

  img {
    width: 16px;
    height: 16px;
    margin-right: 4px;
    vertical-align: text-bottom;
  }
}

@media (max-width: 860px) {
  .auth-page {
    padding: 24px 16px 58px;
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

  .login_footer {
    flex-wrap: wrap;
    line-height: 1.5;
  }
}
</style>
