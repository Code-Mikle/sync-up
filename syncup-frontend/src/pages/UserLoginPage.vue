<template>
  <div class="login-page">
    <section class="login-hero">
      <div class="login-hero__mark">搭</div>
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
  </div>
</template>

<script setup lang="ts">
import {useRoute} from "vue-router";
import {ref} from "vue";
import myAxios, {setLoginToken} from "../plugins/myAxios";
import {showFailToast, showSuccessToast} from "vant";

const route = useRoute();

const userAccount = ref('');
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
      radial-gradient(circle at 88% 18%, rgba(255, 184, 77, 0.4), transparent 7rem),
      linear-gradient(135deg, #0b7d72 0%, #18a58f 58%, #70c69d 100%);
  border-radius: 24px;
  box-shadow: 0 18px 34px rgba(16, 113, 101, 0.22);
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
  color: var(--app-primary-deep);
  background: rgba(255, 255, 255, 0.9);
  border-radius: 16px;
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
</style>
