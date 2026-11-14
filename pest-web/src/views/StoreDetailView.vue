<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getStore, listFacilities, type Store, type Facility } from '@/api'

const route = useRoute()
const router = useRouter()
const storeId = route.params.storeId as string

const store = ref<Store | null>(null)
const facilities = ref<Facility[]>([])
const loading = ref(true)
const error = ref('')
const statusFilter = ref('')

onMounted(async () => {
  try {
    store.value = await getStore(storeId)
    await loadFacilities()
  } catch (e) {
    error.value = '加载门店详情失败'
  } finally {
    loading.value = false
  }
})

async function loadFacilities() {
  const res = await listFacilities(storeId, {
    status: statusFilter.value || undefined,
  })
  facilities.value = res.items
}

function onFilterChange() {
  loadFacilities()
}

function goBack() {
  router.back()
}

const statusLabel: Record<string, string> = {
  ACTIVE: '在用',
  INACTIVE: '停用',
  REMOVED: '移除',
}
</script>

<template>
  <div class="detail">
    <header class="topbar">
      <button class="back" @click="goBack">‹</button>
      <span>门店详情</span>
    </header>

    <div class="content">
      <p v-if="loading" class="hint">加载中…</p>
      <p v-else-if="error" class="hint error">{{ error }}</p>

      <template v-else-if="store">
        <div class="card store-card">
          <div class="name">{{ store.name }}</div>
          <div class="row"><span class="k">编号</span><span>{{ store.code }}</span></div>
          <div class="row" v-if="store.brandName"><span class="k">品牌</span><span>{{ store.brandName }}</span></div>
          <div class="row" v-if="store.address"><span class="k">地址</span><span>{{ store.address }}</span></div>
          <div class="row" v-if="store.timezone"><span class="k">时区</span><span>{{ store.timezone }}</span></div>
          <div class="row" v-if="store.contactName"><span class="k">联系人</span><span>{{ store.contactName }}</span></div>
        </div>

        <div v-if="store.areas?.length" class="card">
          <div class="section-title">区域分区（{{ store.areas.length }}）</div>
          <div class="area-tags">
            <span v-for="a in store.areas" :key="a.id" class="tag">
              {{ a.name }}<span v-if="a.floor" class="floor"> · {{ a.floor }}</span>
            </span>
          </div>
        </div>

        <div class="card">
          <div class="section-title">设施台账</div>
          <div class="filter">
            <select v-model="statusFilter" @change="onFilterChange">
              <option value="">全部状态</option>
              <option value="ACTIVE">在用</option>
              <option value="INACTIVE">停用</option>
              <option value="REMOVED">移除</option>
            </select>
          </div>
          <p v-if="facilities.length === 0" class="hint">暂无设施</p>
          <div v-else class="facility-list">
            <div v-for="f in facilities" :key="f.id" class="facility">
              <div class="f-top">
                <span class="f-code">{{ f.code }}</span>
                <span class="f-status" :class="f.status.toLowerCase()">{{ statusLabel[f.status] || f.status }}</span>
              </div>
              <div class="f-position">{{ f.position }}</div>
              <div class="f-meta">
                <span v-if="f.floor">楼层 {{ f.floor }}</span>
              </div>
            </div>
          </div>
        </div>
      </template>
    </div>
  </div>
</template>

<style scoped>
.detail {
  height: 100%;
  display: flex;
  flex-direction: column;
}
.topbar {
  height: 52px;
  padding: 0 12px;
  display: flex;
  align-items: center;
  gap: 8px;
  background: var(--c-primary);
  color: #fff;
  font-size: 17px;
}
.back {
  background: none;
  border: none;
  color: #fff;
  font-size: 24px;
  cursor: pointer;
  padding: 0 4px;
  line-height: 1;
}
.content {
  flex: 1;
  padding: 16px;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.card {
  background: #fff;
  border-radius: 10px;
  padding: 16px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.06);
}
.store-card .name {
  font-size: 18px;
  font-weight: 700;
  margin-bottom: 12px;
}
.row {
  display: flex;
  gap: 8px;
  margin-top: 6px;
  font-size: 14px;
}
.row .k {
  color: var(--c-text-weak);
  min-width: 48px;
}
.section-title {
  font-size: 15px;
  font-weight: 600;
  margin-bottom: 10px;
}
.area-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.tag {
  background: var(--c-bg-soft, #f2f4f7);
  padding: 4px 10px;
  border-radius: 6px;
  font-size: 13px;
}
.tag .floor {
  color: var(--c-text-weak);
}
.filter {
  margin-bottom: 10px;
}
.filter select {
  width: 100%;
  padding: 8px 10px;
  border: 1px solid #d9dce1;
  border-radius: 8px;
  font-size: 14px;
  background: #fff;
}
.facility-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.facility {
  border: 1px solid #eef0f3;
  border-radius: 8px;
  padding: 10px;
}
.f-top {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.f-code {
  font-weight: 600;
  font-size: 14px;
}
.f-status {
  font-size: 12px;
  padding: 2px 8px;
  border-radius: 4px;
}
.f-status.active {
  background: #e8f5e9;
  color: #2e7d32;
}
.f-status.inactive {
  background: #fff3e0;
  color: #e65100;
}
.f-status.removed {
  background: #f5f5f5;
  color: #9e9e9e;
}
.f-position {
  margin-top: 6px;
  font-size: 14px;
}
.f-meta {
  margin-top: 4px;
  font-size: 12px;
  color: var(--c-text-weak);
}
.hint {
  color: var(--c-text-weak);
  font-size: 14px;
}
.hint.error {
  color: var(--c-danger);
}
</style>
