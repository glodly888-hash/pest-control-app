<script setup>
import { ref, computed } from "vue";
import { useDemo, freezeAndSign } from "../composables/useDemo.js";
const { service: s, go, toast, validation } = useDemo();
const signature = computed({
  get: () => s.value.signatureDrafts.staff,
  set: (value) => (s.value.signatureDrafts.staff = value),
});
const accepted = ref(false);
const errors = computed(validation);
function sign() {
  if (errors.value.length) return toast("请先补充缺失项");
  if (!accepted.value || !signature.value) return toast("请确认声明并手写签名");
  freezeAndSign(signature.value);
}
</script>
<template>
  <PageFrame title="员工签字" back="summary"
    ><div class="inner">
      <div class="panel">
        <span class="tag tag-blue">内容版本 V{{ s.version }}</span>
        <h2 class="mt">核对本次服务</h2>
        <p>签字将固定当前记录，修改后需重新双签。</p>
        <button
          type="button"
          class="text-button"
          @click="go('report', { return: 'staffsign' })"
        >
          查看本次报告 <Icon name="arrow" />
        </button>
      </div>
      <div v-if="errors.length" class="notice warning">
        <strong>还有{{ errors.length }}项待补充</strong>
        <ul class="validation-list">
          <li v-for="e in errors" :key="e.label">
            <button type="button" @click="go(e.page)">{{ e.label }}</button>
          </li>
        </ul>
      </div>
      <template v-if="s.signatures.staff"
        ><div class="notice">
          员工已签署 V{{ s.signatures.staff.version }}，请继续客户确认。
        </div>
        <img
          class="saved-signature"
          :src="s.signatures.staff.image"
          alt="员工已签名" /></template
      ><template v-else
        ><label class="checkline"
          ><input v-model="accepted" type="checkbox" /><span
            >我已核对服务记录、照片、风险和处置措施，确认内容真实完整。</span
          ></label
        ><SignaturePad v-model="signature" label="服务人员签名" />
        <p class="hint">服务人员：陈工 · 工号 PC008</p></template
      >
    </div>
    <template #footer
      ><button
        v-if="s.signatures.staff"
        type="button"
        class="btn primary"
        @click="go('clientsign')"
      >
        继续客户确认</button
      ><button
        v-else
        type="button"
        class="btn primary"
        :disabled="errors.length > 0 || !accepted || !signature"
        @click="sign"
      >
        确认签字，交由客户确认
      </button></template
    ></PageFrame
  >
</template>
