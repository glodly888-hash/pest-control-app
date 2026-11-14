<script setup>
import { computed } from "vue";
import { useDemo } from "../composables/useDemo.js";
const { state, service: s, task, go, taskStatus, modal } = useDemo();
const today = computed(() =>
  state.tasks.filter((t) => t.date === "2026-11-12"),
);
const count = computed(
  () => today.value.filter((t) => taskStatus(t) === "进行中").length,
);
</script>
<template>
  <PageFrame title="工作台" nav="home"
    ><template #action
      ><button
        type="button"
        class="icon-button"
        aria-label="消息通知"
        @click="
          modal(
            '消息通知',
            '今日计划已更新。请及时下载任务资料，并跟进到期风险。',
          )
        "
      >
        <Icon name="bell" /></button
    ></template>
    <div class="greeting">
      <div class="split">
        <div>
          <small>11月12日 · 星期四</small>
          <h2>早上好，陈工</h2>
          <p class="muted">每一次服务，都有据可查</p>
        </div>
        <div class="avatar">陈</div>
      </div>
    </div>
    <div class="inner">
      <div class="metrics">
        <div class="metric">
          <b>{{ today.length }}</b
          ><small>今日任务</small>
        </div>
        <div class="metric">
          <b>{{ count }}</b
          ><small>进行中</small>
        </div>
        <div class="metric">
          <b>{{ s.syncState === "已发布" ? 0 : 1 }}</b
          ><small>待同步</small>
        </div>
      </div>
      <button type="button" class="strip full-strip" @click="go('sync')">
        <Icon name="sync" /><span>{{
          s.syncState === "已发布"
            ? "当前服务已同步"
            : "现场资料已保留，可查看同步进度"
        }}</span
        ><Icon name="chevron" />
      </button>
      <div class="quick">
        <button
          v-for="i in [
            ['tasks', 'calendar', '全部任务'],
            ['tasks', 'plus', '应急服务'],
            ['risks', 'risk', '风险跟进'],
            ['sync', 'download', '离线资料'],
          ]"
          :key="i[2]"
          type="button"
          @click="go(i[0], i[2] === '应急服务' ? { new: 1 } : {})"
        >
          <span class="square"><Icon :name="i[1]" /></span>{{ i[2] }}
        </button>
      </div>
      <div class="section-title">
        今日待办<small>{{ today.length }}项任务</small>
      </div>
      <TaskCard v-for="t in today.slice(0, 2)" :key="t.id" :task="t" />
      <div class="section-title">需要关注</div>
      <div class="panel compact">
        <button type="button" class="row" @click="go('risks')">
          <span class="square"><Icon name="risk" /></span
          ><span class="main"
            ><strong
              >{{
                s.risks.filter((r) => r.state !== "已关闭").length
              }}条风险持续跟进</strong
            ><small>{{ task.store }}</small></span
          ><Icon name="chevron" class="chevron" />
        </button>
      </div></div
  ></PageFrame>
</template>
