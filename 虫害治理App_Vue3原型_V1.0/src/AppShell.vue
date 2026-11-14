<script setup>
import { computed, onMounted, ref, watch, nextTick } from "vue";
import logo from "./assets/sheweisi-logo.png";
import Icon from "./components/Icon.vue";
import { useDemo, resetDemo } from "./composables/useDemo.js";
const props = defineProps({ pageId: String, pageComponent: Object });
const { pages, state, ui, service, go, modal, fillCompleteDemo, exportData } =
  useDemo();
const meta = computed(() => pages.find((p) => p.id === props.pageId));
const groups = [...new Set(pages.map((p) => p.group))];
const dialog = ref(null);
const pure = new URLSearchParams(location.search).has("screen");
const index = computed(() => meta.value.number - 1);
watch(
  () => ui.dialog,
  async (value) => {
    await nextTick();
    if (value && !dialog.value.open) dialog.value.showModal();
    else if (!value && dialog.value.open) dialog.value.close();
  },
);
function confirm() {
  const fn = ui.dialog?.onConfirm;
  ui.dialog = null;
  if (fn) fn();
}
onMounted(() => (document.title = meta.value.title + " · 舍为思虫控作业"));
</script>
<template>
  <div :class="{ standalone: pure }">
    <header class="shellbar">
      <div class="brand">
        <img :src="logo" alt="舍为思波浪Logo" />
        <div>
          <strong>舍为思 <span>虫控作业</span></strong>
          <p>产品原型 · Vue 3 独立页面</p>
        </div>
      </div>
      <div class="bar-actions">
        <button
          class="outer-button"
          type="button"
          @click="state.offline = !state.offline"
        >
          <Icon :name="state.offline ? 'download' : 'wifi'" />{{
            state.offline ? "离线演示" : "联网演示"
          }}</button
        ><button
          class="outer-button"
          type="button"
          @click="
            modal(
              '载入完整示例',
              '会替换当前未签字的示例草稿，填充9个设施和7条风险；手写签字需另行操作。',
              '载入示例',
              fillCompleteDemo,
            )
          "
        >
          载入完整示例</button
        ><button
          class="outer-button"
          type="button"
          @click="
            modal(
              '重置演示资料',
              '这会清除本原型的本机演示记录，包括签名和选取的图片。',
              '重置原型',
              resetDemo,
            )
          "
        >
          重置
        </button>
      </div>
    </header>
    <div class="layout">
      <aside class="sidebar">
        <p class="side-caption">页面目录 <span>20 PAGES</span></p>
        <div v-for="group in groups" :key="group" class="nav-group">
          <h2>{{ group }}</h2>
          <button
            v-for="p in pages.filter((p) => p.group === group)"
            :key="p.id"
            type="button"
            class="screen-link"
            :class="{ active: p.id === pageId }"
            @click="go(p.id)"
          >
            <span>{{ String(p.number).padStart(2, "0") }}</span
            >{{ p.title }}<Icon v-if="p.id === pageId" name="chevron" />
          </button>
        </div>
        <div class="side-footer">以舍为思 VI 构建<br />提供超出预期的服务</div>
      </aside>
      <div class="workspace">
        <div class="workspace-top">
          <div>
            <p class="eyebrow">SHEWEISI / PEST CONTROL</p>
            <h2>{{ meta.title }}</h2>
            <p>{{ meta.description }}</p>
          </div>
          <label class="page-selector"
            ><span class="sr-only">选择原型页面</span
            ><select :value="pageId" @change="go($event.target.value)">
              <option v-for="p in pages" :key="p.id" :value="p.id">
                {{ String(p.number).padStart(2, "0") }} {{ p.title }}
              </option>
            </select></label
          >
        </div>
        <div v-if="ui.storageError" class="storage-error" role="alert">
          {{ ui.storageError
          }}<button type="button" @click="exportData">导出资料</button>
        </div>
        <div class="preview-stage">
          <section class="phone" :data-page="pageId" :aria-label="meta.title">
            <div class="statusbar">
              <span>09:41</span
              ><span><Icon name="wifi" /><span class="battery"></span></span>
            </div>
            <component :is="pageComponent" />
            <div class="gesture"></div>
            <div
              v-if="ui.message"
              class="toast"
              role="status"
              aria-live="polite"
            >
              {{ ui.message }}
            </div>
          </section>
          <aside class="annotation">
            <p class="eyebrow">
              {{ String(meta.number).padStart(2, "0") }} / 20
            </p>
            <h3>服务流程中的这一页</h3>
            <p>{{ meta.rule }}</p>
            <div class="annotation-rule">
              <strong>舍为思视觉语言</strong>
              <div class="swatches">
                <span></span><span></span><span></span>
              </div>
              <p>
                深蓝建立清晰层级<br />浅蓝提示交互与状态<br />白色保留作业的专注空间
              </p>
            </div>
            <div class="annotation-rule">
              <strong>独立文件</strong><code>{{ meta.component }}.vue</code>
              <p>
                每页独立编译。当前为本机交互原型，定位、同步与发布均为演示。
              </p>
            </div>
            <div class="page-arrows">
              <button
                type="button"
                :disabled="index === 0"
                @click="go(pages[index - 1].id)"
              >
                上一页</button
              ><button
                type="button"
                :disabled="index === 19"
                @click="go(pages[index + 1].id)"
              >
                下一页
              </button>
            </div>
          </aside>
        </div>
      </div>
    </div>
    <dialog ref="dialog" class="app-dialog" @cancel="ui.dialog = null">
      <template v-if="ui.dialog"
        ><h2>{{ ui.dialog.title }}</h2>
        <p>{{ ui.dialog.text }}</p>
        <div class="dialog-actions">
          <button class="btn" type="button" @click="ui.dialog = null">
            {{ ui.dialog.onConfirm ? "取消" : "知道了" }}</button
          ><button
            v-if="ui.dialog.onConfirm"
            class="btn primary"
            type="button"
            @click="confirm"
          >
            {{ ui.dialog.actionLabel }}
          </button>
        </div></template
      >
    </dialog>
  </div>
</template>
