<template>
  <div class="login-page">
    <section class="login-hero">
      <div class="login-hero__mark">
        <img :src="logoIcon" alt="搭子星球" />
      </div>
      <p class="login-hero__eyebrow">搭子星球</p>
      <h1>回到你的搭子宇宙</h1>
      <p>登录后继续发现同频的人，加入合适的队伍。</p>
    </section>

    <van-form class="app-form login-form" @submit="onSubmit">
      <van-cell-group inset>
        <van-field
            v-model="userAccount"
            name="userAccount"
            label="账号"
            placeholder="请输入账号"
            autocomplete="username"
            :rules="[{ required: true, message: '请填写账号' }]"
        />
        <van-field
            v-model="userPassword"
            type="password"
            name="userPassword"
            label="密码"
            placeholder="请输入密码"
            autocomplete="current-password"
            :rules="[{ required: true, message: '请填写密码' }]"
        />
      </van-cell-group>
      <div class="app-form__submit">
        <van-button round block type="primary" native-type="submit" :loading="submitting">
          登录
        </van-button>
      </div>
    </van-form>

    <div class="auth-switch">
      还没有账号？
      <button type="button" @click="toRegister">去注册</button>
    </div>
  </div>
</template>

<script setup lang="ts">
import {useRoute, useRouter} from "vue-router";
import {ref} from "vue";
import myAxios, {setLoginToken} from "../plugins/myAxios";
import {showFailToast, showSuccessToast} from "vant";
import logoIcon from "../assets/logo.png";

const route = useRoute();
const router = useRouter();

const getQueryValue = (value: unknown) => {
  if (Array.isArray(value)) {
    return value[0] ?? '';
  }
  return value ?? '';
};

const userAccount = ref(String(getQueryValue(route.query.userAccount)));
const userPassword = ref('');
const submitting = ref(false);

const onSubmit = async () => {
  submitting.value = true;
  try {
    const res = await myAxios.post('/user/login', {
      userAccount: userAccount.value,
      userPassword: userPassword.value,
    })
    console.log(res, '用户登录');
    if (res.code === 0 && res.data) {
      setLoginToken(res.data.token, res.data.tokenPrefix);
      showSuccessToast('登录成功');
      // 跳转到之前的页面
      const redirectUrl = route.query?.redirect as string ?? '/';
      window.location.href = redirectUrl;
    } else {
      showFailToast('登录失败');
    }
  } catch (error) {
    console.error('/user/login error', error);
    showFailToast('登录失败，请稍后重试');
  } finally {
    submitting.value = false;
  }
};

const toRegister = () => {
  router.push('/user/register');
}

</script>

<style scoped>
.login-page {
  min-height: calc(100vh - var(--van-nav-bar-height) - var(--van-tabbar-height));
  padding: 20px var(--app-page-x) 0;
}

.login-hero {
  position: relative;
  padding: 26px 20px 30px;
  overflow: hidden;
  color: #ffffff;
  background:
      radial-gradient(circle at 88% 18%, rgba(var(--app-accent-rgb), 0.4), transparent 7rem),
      var(--app-brand-gradient);
  border-radius: 24px;
  box-shadow: var(--app-brand-shadow);
}

.login-hero::after {
  position: absolute;
  right: -48px;
  bottom: -74px;
  width: 160px;
  height: 160px;
  content: "";
  border: 1px solid rgba(255, 255, 255, 0.22);
  border-radius: 50%;
}

.login-hero__mark {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 46px;
  height: 46px;
  margin-bottom: 18px;
  font-size: 23px;
  font-weight: 900;
  background: rgba(255, 255, 255, 0.94);
  border-radius: 16px;
  box-shadow: 0 8px 20px rgba(23, 21, 79, 0.18);
}

.login-hero__mark img {
  width: 42px;
  height: 42px;
  object-fit: cover;
  border-radius: 14px;
}

.login-hero__eyebrow {
  margin: 0 0 8px;
  font-size: 12px;
  font-weight: 800;
  color: rgba(255, 255, 255, 0.78);
}

.login-hero h1 {
  max-width: 230px;
  margin: 0;
  font-size: 26px;
  font-weight: 900;
  line-height: 1.15;
  letter-spacing: 0;
}

.login-hero p:last-child {
  max-width: 240px;
  margin: 10px 0 0;
  font-size: 13px;
  line-height: 1.55;
  color: rgba(255, 255, 255, 0.82);
}

.login-form {
  margin-top: 18px;
}

.auth-switch {
  margin-top: 16px;
  color: var(--app-text-muted);
  font-size: 13px;
  text-align: center;
}

.auth-switch button {
  padding: 0;
  color: var(--app-primary-deep);
  font-weight: 800;
  background: transparent;
  border: 0;
}
</style>
