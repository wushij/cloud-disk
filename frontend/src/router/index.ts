import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/login', component: () => import('@/views/Login.vue'), meta: { public: true } },
    { path: '/share/:code', component: () => import('@/views/SharePage.vue'), meta: { public: true } },
    {
      path: '/',
      component: () => import('@/layouts/MainLayout.vue'),
      children: [
        { path: '', redirect: '/disk' },
        { path: 'disk', component: () => import('@/views/Disk.vue'), meta: { keepAlive: true } },
        { path: 'office/:id', component: () => import('@/views/OfficeEditor.vue') },
        { path: 'shares', component: () => import('@/views/Shares.vue') },
        { path: 'teams', component: () => import('@/views/TeamSpace.vue') },
        { path: 'recycle', component: () => import('@/views/Recycle.vue'), meta: { keepAlive: true } },
        { path: 'profile', component: () => import('@/views/Profile.vue') },
        { path: 'admin', component: () => import('@/views/Admin.vue'), meta: { admin: true } },
        { path: 'admin/users', component: () => import('@/views/UserManage.vue'), meta: { admin: true } }
      ]
    }
  ]
})

router.beforeEach((to) => {
  const auth = useAuthStore()
  auth.restore()
  if (!to.meta.public && auth.token) {
    void auth.ensureMediaToken()
  }
  if (!to.meta.public && !auth.token) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }
  if (to.meta.admin && auth.role !== 'ADMIN' && auth.role !== 'SUPER_ADMIN') {
    return { path: '/disk' }
  }
  if (to.path === '/login' && auth.token) {
    return { path: '/disk' }
  }
})

export default router
