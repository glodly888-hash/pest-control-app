import { createRouter, createWebHistory } from 'vue-router'

// 骨架路由：登录、工作台、门店列表。其余页面随 W1–W4 逐步接入。
const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', redirect: '/login' },
    { path: '/login', name: 'login', component: () => import('@/views/LoginView.vue') },
    { path: '/dashboard', name: 'dashboard', component: () => import('@/views/DashboardView.vue') },
    { path: '/stores', name: 'stores', component: () => import('@/views/StoreListView.vue') },
    { path: '/stores/:storeId', name: 'store-detail', component: () => import('@/views/StoreDetailView.vue') },
    { path: '/tasks', name: 'tasks', component: () => import('@/views/TaskListView.vue') },
    { path: '/tasks/:taskId', name: 'task-detail', component: () => import('@/views/TaskDetailView.vue') },
  ],
})

// 路由守卫：未登录访问受保护页面时跳回登录页
router.beforeEach((to) => {
  const token = localStorage.getItem('access_token')
  if (to.name !== 'login' && !token) {
    return { name: 'login' }
  }
  if (to.name === 'login' && token) {
    return { name: 'dashboard' }
  }
  return true
})

export default router
