<script setup>
import { useDemo } from "../composables/useDemo.js";
import logo from "../assets/sheweisi-logo.png";
const { state, service: s, go, modal, exportData } = useDemo();
function info(title, text) {
  modal(title, text);
}
function logout() {
  modal(
    "返回登录演示",
    "本机演示记录继续保留。正式账号认证、权限隔离及退出清理将由安卓客户端和后端实现。",
    "返回登录",
    () => go("login"),
  );
}
</script>
<template>
  <PageFrame title="我的" nav="profile"
    ><div class="inner">
      <div class="profile-head">
        <div class="avatar">陈</div>
        <div>
          <h2>陈工</h2>
          <p>服务人员 · PC008</p>
          <span class="tag tag-blue">江阴服务团队</span>
        </div>
      </div>
      <div class="panel compact">
        <button type="button" class="row" @click="go('sync')">
          <span class="square"><Icon name="sync" /></span
          ><span class="main"
            ><strong>同步与离线资料</strong
            ><small
              >{{ state.offline ? "离线模式" : "当前在线" }} ·
              {{ state.downloaded ? "已缓存任务" : "待下载任务" }}</small
            ></span
          ><Icon name="arrow" /></button
        ><button type="button" class="row" @click="go('reportdone')">
          <span class="square"><Icon name="file" /></span
          ><span class="main"
            ><strong>本次报告记录</strong
            ><small
              >{{ s.publishedReports.length }}个已归档演示版本</small
            ></span
          ><Icon name="arrow" /></button
        ><button type="button" class="row" @click="exportData">
          <span class="square"><Icon name="download" /></span
          ><span class="main"
            ><strong>导出演示资料</strong
            ><small>保存当前浏览器中的原型数据</small></span
          ><Icon name="arrow" />
        </button>
      </div>
      <div class="panel compact">
        <button
          type="button"
          class="row"
          @click="
            info(
              '账号与数据范围',
              '当前为员工角色演示。正式系统由管理员配置区域、团队和门店权限，服务端对每次访问执行数据权限校验。',
            )
          "
        >
          <span class="main"><strong>账号与权限说明</strong></span
          ><Icon name="arrow" /></button
        ><button
          type="button"
          class="row"
          @click="
            info(
              '使用帮助',
              '建议先从任务详情按步骤填写。评审时可使用页面外侧“载入完整示例”，再体验报告预览、双方签字、同步异常和版本更正。',
            )
          "
        >
          <span class="main"><strong>使用帮助</strong></span
          ><Icon name="arrow" /></button
        ><button
          type="button"
          class="row"
          @click="
            info(
              '关于产品',
              '舍为思虫害治理App · Vue3独立页面原型V1.0。品牌颜色、波浪标志、字体层级和组件规范依据提供的VI资料设计。',
            )
          "
        >
          <span class="main"><strong>关于产品</strong></span
          ><Icon name="arrow" />
        </button>
      </div>
      <button type="button" class="btn full" @click="logout">返回登录</button>
      <div class="brand-inline mt">
        <img :src="logo" alt="舍为思标志" /><span>舍为思智慧科技</span>
      </div>
      <p class="hint">提供超出预期的服务</p>
    </div></PageFrame
  >
</template>
