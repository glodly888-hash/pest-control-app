<script setup>
import { ref } from "vue";
import logo from "../assets/sheweisi-logo.png";
import { useDemo } from "../composables/useDemo.js";
const { state, go, toast, modal } = useDemo();
const company = ref("PC001"),
  account = ref("PC-008"),
  password = ref(""),
  terms = ref(false);
function login() {
  if (state.offline) return toast("首次登录需要网络连接");
  if (!company.value.trim() || !account.value.trim())
    return toast("请填写公司编号和员工账号");
  if (!terms.value) return toast("请阅读并确认使用须知");
  password.value = "";
  go("home");
}
</script>
<template>
  <PageFrame title="员工登录"
    ><div class="login-brand">
      <img :src="logo" alt="舍为思Logo" />
      <h2>舍为思 · 虫控作业</h2>
      <p>提供超出预期的服务</p>
    </div>
    <form class="login-form" @submit.prevent="login">
      <FormField v-model="company" label="公司编号" required /><FormField
        v-model="account"
        label="员工账号"
        required
      /><FormField
        v-model="password"
        type="password"
        label="密码"
        placeholder="原型体验无需真实密码"
      /><label class="checkline"
        ><input v-model="terms" type="checkbox" /><span
          >我已阅读并同意使用须知与隐私说明</span
        ></label
      ><button type="submit" class="btn primary full">体验登录</button>
      <div class="split mt">
        <button
          type="button"
          class="text-button"
          @click="
            modal(
              '联系管理员',
              '公司统一分配账号与门店权限。本原型不连接账号系统，请勿输入真实密码。',
            )
          "
        >
          联系管理员</button
        ><small>首次登录需联网</small>
      </div>
    </form>
    <p class="login-bottom">
      授权员工服务工作台<br />演示数据仅保存在当前浏览器
    </p></PageFrame
  >
</template>
