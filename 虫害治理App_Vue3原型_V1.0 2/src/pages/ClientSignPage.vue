<script setup>
import { ref, computed } from "vue";
import { useDemo } from "../composables/useDemo.js";
const { service: s, go, toast } = useDemo();
const signature = computed({
  get: () => s.value.signatureDrafts.client,
  set: (value) => (s.value.signatureDrafts.client = value),
});
const refusal = ref(false);
const reviewed = computed(() => s.value.reviewVersion === s.value.version);
const staffValid = computed(
  () => s.value.signatures.staff?.version === s.value.version,
);
function confirm() {
  if (!staffValid.value) return toast("请先由员工确认签字");
  if (!reviewed.value || !s.value.client.read)
    return toast("请预览报告至少5秒，并确认已阅读");
  if (!s.value.client.name.trim() || !signature.value)
    return toast("请填写姓名并签字");
  s.value.signatures.client = {
    image: signature.value,
    at: new Date().toISOString(),
    name: s.value.client.name.trim(),
    version: s.value.version,
    rating: s.value.client.rating,
    comment: s.value.client.comment,
  };
  s.value.client.refusal = "";
  s.value.endedAt ||= new Date().toISOString();
  go("sync");
}
function saveRefusal() {
  if (!s.value.client.refusal.trim())
    return toast("请填写客户不在场或拒签原因");
  toast("原因已保存，服务保持待确认");
}
</script>
<template>
  <PageFrame title="客户现场确认" back="staffsign"
    ><div class="inner">
      <div v-if="!staffValid" class="notice warning">
        员工尚未签字，请先完成员工确认。<button
          type="button"
          class="text-button"
          @click="go('staffsign')"
        >
          前往员工签字
        </button>
      </div>
      <div class="panel">
        <span class="tag tag-blue">服务内容 V{{ s.version }}</span>
        <h2 class="mt">请确认本次服务</h2>
        <p>查看服务发现、处理措施及需要门店配合的整改事项。</p>
        <button
          type="button"
          class="btn full mt"
          @click="go('report', { return: 'clientsign' })"
        >
          {{ reviewed ? "再次查看服务报告" : "查看服务报告（至少5秒）" }}
        </button>
      </div>
      <div v-if="s.signatures.client" class="notice">
        客户已签署本次内容版本。<img
          class="saved-signature"
          :src="s.signatures.client.image"
          alt="客户已签名"
        />
      </div>
      <fieldset v-else class="editor" :disabled="!staffValid">
        <FormField
          v-model="s.client.name"
          label="客户确认人姓名"
          placeholder="请输入现场确认人的姓名"
          required
        /><label class="checkline"
          ><input
            v-model="s.client.read"
            type="checkbox"
            :disabled="!reviewed"
          /><span
            >我已阅读并确认本次服务报告。{{
              reviewed ? "" : "请先完成报告预览。"
            }}</span
          ></label
        >
        <div class="section-title">服务评价 <small>选填</small></div>
        <div class="stars" role="group" aria-label="总体服务评价">
          <button
            v-for="n in 5"
            :key="n"
            type="button"
            :aria-label="n + '星'"
            :aria-pressed="s.client.rating === n"
            :class="{ selected: s.client.rating >= n }"
            @click="s.client.rating = s.client.rating === n ? 0 : n"
          >
            {{ s.client.rating >= n ? "★" : "☆" }}
          </button>
        </div>
        <FormField
          v-model="s.client.comment"
          label="意见与建议（选填）"
          type="textarea"
          placeholder="您的意见有助于改进服务"
        /><SignaturePad v-model="signature" label="客户签名" /><button
          type="button"
          class="text-button"
          @click="refusal = !refusal"
        >
          客户不在场 / 暂不签字
        </button>
        <div v-if="refusal" class="notice warning">
          <FormField
            v-model="s.client.refusal"
            label="未签字原因"
            type="textarea"
            required
          /><button type="button" class="btn full" @click="saveRefusal">
            保存原因，保持待确认
          </button>
        </div>
      </fieldset>
      <p class="hint">
        签名仅用于本次服务确认。离线签字将在联网同步完成后进入报告发布流程。
      </p>
    </div>
    <template #footer
      ><button
        v-if="s.signatures.client"
        type="button"
        class="btn primary"
        @click="go('sync')"
      >
        前往同步中心</button
      ><button
        v-else
        type="button"
        class="btn primary"
        :disabled="
          !staffValid ||
          !reviewed ||
          !s.client.read ||
          !s.client.name.trim() ||
          !signature
        "
        @click="confirm"
      >
        确认签字并完成现场服务
      </button></template
    ></PageFrame
  >
</template>
