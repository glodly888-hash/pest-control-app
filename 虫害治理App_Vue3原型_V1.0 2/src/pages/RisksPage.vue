<script setup>
import { ref, computed } from "vue";
import { useDemo } from "../composables/useDemo.js";
const { service: s, state, task, locked, go } = useDemo();
const filter = ref("全部");
const list = computed(() =>
  s.value.risks.filter(
    (r) => filter.value === "全部" || r.state === filter.value,
  ),
);
function open(r) {
  state.selectedRisk = r.id;
  go("riskdetail");
}
</script>
<template>
  <PageFrame title="风险台账" nav="risks"
    ><template #action
      ><button
        class="text-button"
        type="button"
        :disabled="locked"
        @click="go('newrisk')"
      >
        新增
      </button></template
    >
    <div class="tabs">
      <button
        v-for="f in ['全部', '待整改', '整改中', '待复核', '已关闭']"
        :key="f"
        type="button"
        :class="{ active: filter === f }"
        @click="filter = f"
      >
        {{ f }}
      </button>
    </div>
    <div class="inner">
      <div class="split mb">
        <small>{{ task.store }}</small
        ><span class="tag tag-blue">跨服务跟进</span>
      </div>
      <button
        v-for="r in list"
        :key="r.id"
        class="task-card"
        type="button"
        @click="open(r)"
      >
        <div class="split">
          <small>{{ r.type }}</small
          ><span
            class="tag"
            :class="r.state === '已关闭' ? 'tag-success' : 'tag-blue'"
            >{{ r.state }}</span
          >
        </div>
        <h3>{{ r.title }}</h3>
        <p>{{ r.area }} · {{ r.floor }} · {{ r.pest }}风险</p>
        <div class="card-bottom">
          <span>{{ r.checked ? "本次已跟进" : r.due + "前整改" }}</span
          ><span class="link-label">查看跟进 <Icon name="chevron" /></span>
        </div>
      </button>
      <div v-if="!list.length" class="empty-state">
        <Icon name="shield" />
        <h3>暂无{{ filter }}风险</h3>
        <p>风险复核结果会保留在历史记录中</p>
      </div>
      <p class="hint">
        服务完成不会自动关闭风险。请逐条跟进已有风险，或记录未核查原因。
      </p>
    </div></PageFrame
  >
</template>
