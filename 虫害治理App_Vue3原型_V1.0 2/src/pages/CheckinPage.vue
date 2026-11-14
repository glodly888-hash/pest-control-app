<script setup>
import { useDemo } from "../composables/useDemo.js";
const { state, service: s, task, locked, toast, saveModule } = useDemo();
function save() {
  if (locked.value) return;
  if (!state.downloaded && state.offline)
    return toast("未下载资料，无法离线开始");
  if (!s.value.checkin.photos.length) return toast("请添加一张门头照片");
  if (s.value.checkin.location !== "范围内" && !s.value.checkin.reason.trim())
    return toast("请填写定位异常原因");
  s.value.checkin.time ||= new Date().toISOString();
  saveModule("checkin", "intake");
}
</script>
<template>
  <PageFrame title="到店签到" back="detail"
    ><div class="inner">
      <h2 class="store-title">{{ task.store }}</h2>
      <small>门店 {{ task.code }} · {{ task.type }}</small
      ><EditorGuard
        ><div class="map">
          <div class="pin"><Icon name="pin" /></div>
          <span class="maplabel">定位示意 · 门店签到范围</span>
        </div>
        <div class="kv">
          <span>示例定位</span><span>距门店28米 · 精度±12米</span>
        </div>
        <PhotoField v-model="s.checkin.photos" label="门头照片（至少1张）" />
        <div class="section-title">签到信息</div>
        <div class="kv"><span>服务人员</span><span>陈工</span></div>
        <div class="kv">
          <span>到店时间</span
          ><span>{{
            s.checkin.time
              ? new Date(s.checkin.time).toLocaleString("zh-CN")
              : "确认签到时记录"
          }}</span>
        </div>
        <FormField
          v-model="s.checkin.location"
          label="定位情况"
          :options="['范围内', '超出范围', '无法定位', '精度不足']"
        /><FormField
          v-if="s.checkin.location !== '范围内'"
          v-model="s.checkin.reason"
          label="定位异常说明"
          type="textarea"
          required
        />
        <p class="hint">离线签到保留本机记录时间，联网后补传。</p></EditorGuard
      >
    </div>
    <template #footer
      ><button
        type="button"
        class="btn primary"
        :disabled="locked"
        @click="save"
      >
        {{ s.checkin.time ? "已签到 · 继续服务" : "确认签到" }}
      </button></template
    ></PageFrame
  >
</template>
