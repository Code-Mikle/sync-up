<template>
  <div class="register-page">
    <section class="register-hero">
      <div class="register-hero__mark">新</div>
      <p class="register-hero__eyebrow">加入搭子星球</p>
      <h1>创建你的星球身份</h1>
      <p>用一个账号开始发现同频的人，找到合适的队伍。</p>
    </section>

    <van-form class="app-form register-form" @submit="onSubmit">
      <van-cell-group inset>
        <van-field
            v-model="registerForm.userAccount"
            name="userAccount"
            label="账号"
            placeholder="至少 4 位"
            autocomplete="username"
            clearable
            :rules="[{ required: true, message: '请填写账号' }]"
        />
        <van-field
            v-model="registerForm.planetCode"
            name="planetCode"
            label="星球编号"
            placeholder="最多 5 位"
            clearable
            :rules="[{ required: true, message: '请填写星球编号' }]"
        />
        <van-field
            v-model="registerForm.userPassword"
            type="password"
            name="userPassword"
            label="密码"
            placeholder="至少 8 位"
            autocomplete="new-password"
            clearable
            :rules="[{ required: true, message: '请填写密码' }]"
        />
        <van-field
            v-model="registerForm.checkPassword"
            type="password"
            name="checkPassword"
            label="确认密码"
            placeholder="请再次输入密码"
            autocomplete="new-password"
            clearable
            :rules="[{ required: true, message: '请确认密码' }]"
        />
      </van-cell-group>
      <div class="app-form__submit">
        <van-button round block type="primary" native-type="submit" :loading="submitting">
          注册
        </van-button>
      </div>
    </van-form>

    <div class="auth-switch">
      已经有账号？
      <button type="button" @click="toLogin">去登录</button>
    </div>
  </div>
</template>

<script setup lang="ts">
import {ref} from "vue";
import {useRouter} from "vue-router";
import {showFailToast, showSuccessToast} from "vant";
import myAxios from "../plugins/myAxios";

const router = useRouter();

const submitting = ref(false);
const registerForm = ref({
  userAccount: '',
  userPassword: '',
  checkPassword: '',
  planetCode: '',
});

const validateForm = () => {
  const {userAccount, userPassword, checkPassword, planetCode} = registerForm.value;
  if (userAccount.trim().length < 4) {
    showFailToast('账号至少 4 位');
    return false;
  }
  if (planetCode.trim().length > 5) {
    showFailToast('星球编号最多 5 位');
    return false;
  }
  if (userPassword.length < 8) {
    showFailToast('密码至少 8 位');
    return false;
  }
  if (userPassword !== checkPassword) {
    showFailToast('两次输入的密码不一致');
    return false;
  }
  return true;
}

const onSubmit = async () => {
  if (!validateForm()) {
    return;
  }
  submitting.value = true;
  try {
    const res = await myAxios.post<number>('/user/register', {
      userAccount: registerForm.value.userAccount.trim(),
      userPassword: registerForm.value.userPassword,
      checkPassword: registerForm.value.checkPassword,
      planetCode: registerForm.value.planetCode.trim(),
    });
    if (res?.code === 0 && res.data) {
      showSuccessToast('注册成功，请登录');
      router.replace({
        path: '/user/login',
        query: {
          userAccount: registerForm.value.userAccount.trim(),
        },
      });
    } else {
      showFailToast(res?.description || '注册失败');
    }
  } catch (error) {
    console.error('/user/register error', error);
    showFailToast('注册失败，请稍后重试');
  } finally {
    submitting.value = false;
  }
}

const toLogin = () => {
  router.push('/user/login');
}
</script>

<style scoped>
.register-page {
  min-height: calc(100vh - var(--van-nav-bar-height) - var(--van-tabbar-height));
  padding: 20px var(--app-page-x) 0;
}

.register-hero {
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

.register-hero::after {
  position: absolute;
  right: -48px;
  bottom: -74px;
  width: 160px;
  height: 160px;
  content: "";
  border: 1px solid rgba(255, 255, 255, 0.22);
  border-radius: 50%;
}

.register-hero__mark {
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

.register-hero__eyebrow {
  margin: 0 0 8px;
  font-size: 12px;
  font-weight: 800;
  color: rgba(255, 255, 255, 0.78);
}

.register-hero h1 {
  max-width: 230px;
  margin: 0;
  font-size: 26px;
  font-weight: 900;
  line-height: 1.15;
  letter-spacing: 0;
}

.register-hero p:last-child {
  max-width: 240px;
  margin: 10px 0 0;
  font-size: 13px;
  line-height: 1.55;
  color: rgba(255, 255, 255, 0.82);
}

.register-form {
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
