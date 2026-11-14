<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { listStores, type Store } from '@/api'

const router = useRouter()
const stores = ref<Store[]>([])
const loading = ref(true)
const error = ref('')

onMounted(async () => {
  try {
    const res = await listStores()
    stores.value = res.items
  } catch (e) {
    error.value = '加载门店失败，请重试'
  } finally {
    loading.value = false
  }
})

function openStore(s: Store) {
  router.push(`/stores/${s.id}`)
}
</script>

<template>
  <div class="stores">
    <header class="topbar">门店列表</header>
    <div class="content">
      <p v-if="loading" class="hint">加载中…</p>
      <p v-else-if="error" class="hint error">{{ error }}</p>
      <p v-else-if="stores.length === 0" class="hint">暂无授权门店</p>
      <div v-else class="list">
        <div v-for="s in stores" :key="s.id" class="card item" @click="openStore(s)">
          <div class="name">{{ s.name }}</div>
          <div v-if="s.code" class="code">编号：{{ s.code }}</div>
          <div v-if="s.brandName" class="brand">{{ s.brandName }}</div>
          <div v-if="s.address" class="addr">{{ s.address }}</div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.stores {
  height: 100%;
  display: flex;
  flex-direction: column;
}
.topbar {
  height: 52px;
  padding: 0 16px;
  display: flex;
  align-items: center;
  background: var(--c-primary);
  color: #fff;
  font-size: 17px;
}
.content {
  flex: 1;
  padding: 16px;
  overflow-y: auto;
}
.list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.item {
  cursor: pointer;
  transition: box-shadow 0.15s;
}
.item:active {
  box-shadow: 0 2px 6px rgba(0, 0, 0, 0.1);
}
.item .name {
  font-size: 16px;
  font-weight: 600;
}
.item .code,
.item .brand {
  margin-top: 4px;
  color: var(--c-text-weak);
  font-size: 12px;
}
.item .addr {
  margin-top: 4px;
  color: var(--c-text-weak);
  font-size: 13px;
}
.hint {
  color: var(--c-text-weak);
  font-size: 14px;
}
.hint.error {
  color: var(--c-danger);
}
</style>
