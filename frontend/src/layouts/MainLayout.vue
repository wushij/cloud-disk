<script setup lang="ts">

import { computed, ref, onMounted, onUnmounted, watch } from 'vue'

import { useRoute, useRouter } from 'vue-router'

import { storeToRefs } from 'pinia'

import { useAuthStore } from '@/stores/auth'

import { useNotificationStore } from '@/stores/notification'

import ThemePicker from '@/components/ThemePicker.vue'

import { useTransferStore } from '@/stores/transfer'

import TransferPanel from '@/components/TransferPanel.vue'

import TeamSpaceIcon from '@/components/icons/TeamSpaceIcon.vue'

import http from '@/api/http'

import { ElMessage } from 'element-plus'

import { subscribeWs } from '@/utils/ws'



const route = useRoute()

const router = useRouter()

const auth = useAuthStore()

const notifyStore = useNotificationStore()

const transferStore = useTransferStore()

const { runningCount: runningTransfersCount } = storeToRefs(transferStore)

const storageLabel = ref('')

function toggleTransferList() {
  if (transferStore.tasks.length === 0) {
    ElMessage.info('暂无传输任务')
    return
  }
  transferStore.toggleCollapse()
}



interface StorageUsage {

  usedBytes: number

  quotaBytes: number

  usedPercent: number

  usedFormatted: string

  quotaFormatted: string

}

const storageUsage = ref<StorageUsage | null>(null)



const notifyVisible = ref(false)

const avatarBroken = ref(false)

const navItems = [
  { path: '/disk', label: '我的云盘', icon: 'FolderOpened' },
  { path: '/shares', label: '我的分享', icon: 'Share' },
  { path: '/teams', label: '团队空间', icon: TeamSpaceIcon },
  { path: '/recycle', label: '回收站', icon: 'Delete' },
  { path: '/profile', label: '个人中心', icon: 'User' },
  { path: '/admin', label: '系统管理', icon: 'Setting', admin: true as const }
]

watch(() => auth.avatarVersion, () => {
  avatarBroken.value = false
})

const active = computed(() => route.path)

const pageTitle = computed(() => {
  const item = navItems.find((i) => route.path.startsWith(i.path))
  return item?.label ?? 'CloudDisk Pro'
})

const visibleNavItems = computed(() =>
  navItems.filter((i) => !('admin' in i && i.admin) || auth.isAdmin)
)

const unread = computed(() => notifyStore.unreadCount())



let unsubscribeWs: (() => void) | null = null



onMounted(async () => {

  try {

    await auth.fetchProfile()

  } catch {

    /* ignore */

  }

  try {

    const { data } = await http.get('/api/storage/info')

    if (data.type === 'minio') {

      storageLabel.value = `MinIO · ${data.bucket}`

    } else {

      storageLabel.value = '本地存储'

    }

  } catch {

    storageLabel.value = ''

  }

  try {

    const { data } = await http.get('/api/storage/usage')

    storageUsage.value = data

  } catch {

    /* ignore */

  }

  try {

    await notifyStore.loadFromApi()

  } catch {

    /* ignore */

  }



  unsubscribeWs = subscribeWs((data) => {

    if (data.type === 'notification') {

      notifyStore.push({

        id: data.notifyId,

        type: data.notifyType,

        title: data.title,

        content: data.content,

        refId: data.refId

      })

    }

  })

})



onUnmounted(() => {

  unsubscribeWs?.()

})



function logout() {

  auth.logout()

  router.push('/login')

}



function openNotify(item: { id: string; read: boolean; type: string }) {
  if (item.type === 'TEAM_INVITED') return
  notifyStore.markRead(item.id)
}

async function acceptTeamInvite(item: { id: string; refId?: string }) {
  if (!item.refId) return
  try {
    await notifyStore.acceptTeamInvite(item.refId)
    await notifyStore.markRead(item.id)
    ElMessage.success('已加入团队')
  } catch {
    /* global toast */
  }
}

async function rejectTeamInvite(item: { id: string; refId?: string }) {
  if (!item.refId) return
  try {
    await notifyStore.rejectTeamInvite(item.refId)
    await notifyStore.markRead(item.id)
    ElMessage.info('已拒绝邀请')
  } catch {
    /* global toast */
  }
}

</script>



<template>

  <el-container class="cd-layout">

    <!-- 深色渐变侧边栏 -->

    <el-aside width="240px" class="layout-sidebar cd-sidebar">

      <!-- Logo -->

      <div class="cd-sidebar-logo">

        <div class="cd-logo-icon">

          <el-icon :size="24"><UploadFilled /></el-icon>

        </div>

        <span class="cd-logo-text">CloudDisk Pro</span>

      </div>



      <!-- 导航菜单 -->

      <nav class="cd-nav">

        <router-link

          v-for="item in visibleNavItems"

          :key="item.path"

          :to="item.path"

          class="cd-nav-item"

          :class="{ active: active === item.path }"

        >

          <el-icon :size="20"><component :is="item.icon" /></el-icon>

          <span>{{ item.label }}</span>

          <div v-if="active === item.path" class="cd-nav-indicator" />

        </router-link>

      </nav>



      <!-- 底部存储信息 -->
      <div class="cd-sidebar-footer">
        <div v-if="storageUsage" class="cd-storage-card">
          <div class="cd-storage-head">
            <span class="cd-storage-head-label">存储空间</span>
            <span v-if="storageUsage.quotaBytes === 0" class="cd-storage-badge">无限容量</span>
            <span v-else class="cd-storage-percent">{{ storageUsage.usedPercent }}%</span>
          </div>

          <div class="cd-storage-usage">
            <strong>{{ storageUsage.usedFormatted }}</strong>
            <span v-if="storageUsage.quotaBytes > 0" class="cd-storage-quota">
              / {{ storageUsage.quotaFormatted }}
            </span>
          </div>

          <el-progress
            v-if="storageUsage.quotaBytes > 0"
            :percentage="storageUsage.usedPercent"
            :stroke-width="5"
            :color="storageUsage.usedPercent >= 90 ? '#ef4444' : storageUsage.usedPercent >= 70 ? '#f59e0b' : 'var(--theme-primary)'"
            :show-text="false"
            class="cd-storage-progress"
          />

          <div v-if="storageLabel" class="cd-storage-type">
            <el-icon :size="12"><Monitor /></el-icon>
            <span>{{ storageLabel }}</span>
          </div>
        </div>
      </div>

    </el-aside>



    <el-container class="cd-main-container">

      <!-- 毛玻璃 Header -->

      <el-header class="cd-header">

        <div class="cd-header-left">

          <h1 class="cd-page-title">{{ pageTitle }}</h1>

        </div>

        <div class="cd-header-right">

          <div class="cd-header-transfer-wrap" @click="toggleTransferList">
            <button class="cd-header-btn cd-transfer-btn" :class="{ active: runningTransfersCount > 0 }">
              <svg viewBox="0 0 24 24" fill="none" class="cd-transfer-btn-icon">
                <path d="M19.35 10.04C18.67 6.59 15.64 4 12 4 9.11 4 6.6 5.64 5.35 8.04 2.34 8.36 0 10.91 0 14c0 3.31 2.69 6 6 6h13c2.76 0 5-2.24 5-5 0-2.64-2.05-4.78-4.65-4.96z" fill="currentColor"/>
              </svg>
            </button>
            <span v-if="runningTransfersCount > 0" class="cd-transfer-btn-badge">
              {{ runningTransfersCount > 99 ? '99+' : runningTransfersCount }}
            </span>
          </div>

          <el-badge :value="unread" :hidden="unread === 0" :max="99">

            <el-button class="cd-header-btn" circle @click="notifyVisible = true">

              <el-icon :size="18"><Bell /></el-icon>

            </el-button>

          </el-badge>

          <ThemePicker />

          <el-dropdown trigger="click" @command="logout">

            <div class="cd-user-info">

              <el-avatar
                :size="32"
                :src="avatarBroken ? undefined : auth.avatarSrc || undefined"
                class="cd-user-avatar"
                @error="avatarBroken = true"
              >

                {{ auth.avatarInitial }}

              </el-avatar>

              <span class="cd-user-name">{{ auth.nickname || auth.username }}</span>

              <el-icon :size="14" class="cd-user-arrow"><ArrowDown /></el-icon>

            </div>

            <template #dropdown>

              <el-dropdown-menu>

                <el-dropdown-item command="profile" @click="router.push('/profile')">

                  <el-icon><User /></el-icon> 个人中心

                </el-dropdown-item>

                <el-dropdown-item divided command="logout">

                  <el-icon><SwitchButton /></el-icon> 退出登录

                </el-dropdown-item>

              </el-dropdown-menu>

            </template>

          </el-dropdown>

        </div>

      </el-header>



      <!-- 主内容区 -->

      <el-main class="cd-main">

        <router-view v-slot="{ Component }">

          <transition name="fade" mode="out-in">

            <component :is="Component" />

          </transition>

        </router-view>

      </el-main>

    </el-container>



    <!-- 通知抽屉 -->

    <el-drawer v-model="notifyVisible" title="消息通知" size="380px">

      <template #header>

        <div class="cd-notify-header">

          <span class="cd-notify-title">消息通知</span>

          <el-button link type="primary" @click="notifyStore.markAllRead()">全部已读</el-button>

        </div>

      </template>

      <el-empty v-if="!notifyStore.items.length" description="暂无通知" />

      <div v-else class="cd-notify-list">

        <div
          v-for="item in notifyStore.items"
          :key="item.id"
          class="cd-notify-item"
          :class="{ unread: !item.read }"
          @click="openNotify(item)"
        >
          <div v-if="!item.read" class="cd-notify-dot" />
          <div class="cd-notify-content">
            <div class="cd-notify-item-title">{{ item.title }}</div>
            <div class="cd-notify-item-text">{{ item.content }}</div>
            <div
              v-if="item.type === 'TEAM_INVITED' && item.refId && !item.read"
              class="cd-notify-actions"
              @click.stop
            >
              <el-button size="small" type="primary" @click="acceptTeamInvite(item)">接受</el-button>
              <el-button size="small" @click="rejectTeamInvite(item)">拒绝</el-button>
            </div>
          </div>
        </div>

      </div>

    </el-drawer>

    <!-- 全局传输进度面板 -->
    <TransferPanel />

  </el-container>

</template>



<style scoped>

/* ============================================

   布局容器

   ============================================ */

.cd-layout {

  width: 100%;

  height: 100vh;

  min-height: 100vh;

  overflow: hidden;

  background: var(--theme-bg, var(--cd-bg));

}



.cd-main-container {

  flex: 1;

  flex-direction: column;

  min-width: 0;

  min-height: 0;

  overflow: hidden;

}



/* ============================================

   毛玻璃 Header

   ============================================ */

.cd-header {

  background: rgba(255, 255, 255, 0.92);

  backdrop-filter: blur(12px);

  -webkit-backdrop-filter: blur(12px);

  border-bottom: 1px solid var(--cd-border-light);

  display: flex;

  align-items: center;

  justify-content: space-between;

  padding: 0 20px;

  height: 56px;

  flex-shrink: 0;

  position: sticky;

  top: 0;

  z-index: 9;

}



.cd-header-left {

  display: flex;

  align-items: center;

  min-width: 0;

}



.cd-page-title {

  margin: 0;

  font-size: 17px;

  font-weight: 700;

  color: var(--cd-text-primary);

  letter-spacing: -0.01em;

}



.cd-header-right {

  display: flex;

  align-items: center;

  gap: 8px;

}



.cd-header-btn {

  width: 36px !important;

  height: 36px !important;

  border: none !important;

  color: var(--cd-text-secondary) !important;

  transition: var(--cd-transition-fast);

}



.cd-header-btn:hover {

  background: var(--cd-primary-bg) !important;

  color: var(--cd-primary) !important;

}



/* ============================================
   Header 传输按钮
   ============================================ */
.cd-header-transfer-wrap {
  position: relative;
  display: flex;
  align-items: center;
}

.cd-transfer-btn {
  width: 36px !important;
  height: 36px !important;
  border: none !important;
  color: var(--cd-text-secondary) !important;
  transition: var(--cd-transition-fast);
  display: flex !important;
  align-items: center;
  justify-content: center;
  padding: 0 !important;
  background: transparent !important;
  cursor: pointer;
  border-radius: 10px !important;
}

.cd-transfer-btn:hover {
  background: var(--cd-primary-bg) !important;
  color: var(--cd-primary) !important;
}

.cd-transfer-btn.active {
  color: var(--cd-primary) !important;
}

.cd-transfer-btn-icon {
  width: 18px;
  height: 18px;
}

.cd-transfer-btn-badge {
  position: absolute;
  top: -2px;
  right: -3px;
  min-width: 14px;
  height: 14px;
  padding: 0 3px;
  border-radius: 99px;
  background: var(--theme-primary-gradient, linear-gradient(135deg, #4f46e5 0%, #6366f1 100%));
  color: #fff;
  font-size: 9px;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
  line-height: 1;
  border: 1.5px solid #fff;
  box-shadow: 0 1px 4px rgba(79, 70, 229, 0.25);
  animation: badgePopIn 0.3s cubic-bezier(0.34, 1.56, 0.64, 1);
}

@keyframes badgePopIn {
  from {
    transform: scale(0);
    opacity: 0;
  }
  to {
    transform: scale(1);
    opacity: 1;
  }
}



/* 用户信息 */

.cd-user-info {

  display: flex;

  align-items: center;

  gap: 8px;

  padding: 4px 12px 4px 4px;

  border-radius: var(--cd-radius-full);

  cursor: pointer;

  transition: var(--cd-transition-fast);

}



.cd-user-info:hover {

  background: var(--cd-primary-bg);

}



.cd-user-avatar {

  background: var(--cd-primary-gradient) !important;

  color: #fff !important;

  font-weight: 600 !important;

  font-size: 13px !important;

}



.cd-user-name {

  font-size: 14px;

  font-weight: 500;

  color: var(--cd-text-primary);

  max-width: 120px;

  overflow: hidden;

  text-overflow: ellipsis;

  white-space: nowrap;

}



.cd-user-arrow {

  color: var(--cd-text-placeholder) !important;

}



/* ============================================

   主内容区

   ============================================ */

.cd-main {

  --el-main-padding: 0;

  padding: 0 !important;

  flex: 1;

  min-height: 0;

  overflow: hidden;

  display: flex;

  flex-direction: column;

  animation: fadeIn 0.3s ease;

}



/* ============================================

   通知抽屉

   ============================================ */

.cd-notify-header {

  display: flex;

  align-items: center;

  justify-content: space-between;

  width: 100%;

}



.cd-notify-title {

  font-size: 16px;

  font-weight: 700;

  color: var(--cd-text-primary);

}



.cd-notify-list {

  display: flex;

  flex-direction: column;

  gap: 8px;

}



.cd-notify-item {

  padding: 14px 16px;

  border-radius: var(--cd-radius-lg);

  border: 1px solid var(--cd-border-light);

  cursor: pointer;

  transition: var(--cd-transition-fast);

  display: flex;

  gap: 10px;

  align-items: flex-start;

}



.cd-notify-item:hover {

  border-color: var(--cd-primary-light);

  background: var(--cd-primary-bg);

}



.cd-notify-item.unread {

  background: #F0F5FF;

  border-color: #C6DBFA;

}



.cd-notify-dot {

  width: 8px;

  height: 8px;

  border-radius: 50%;

  background: var(--cd-primary);

  flex-shrink: 0;

  margin-top: 6px;

  box-shadow: 0 0 6px rgba(79, 124, 255, 0.4);

}



.cd-notify-content {

  flex: 1;

  min-width: 0;

}



.cd-notify-item-title {

  font-weight: 600;

  font-size: 14px;

  color: var(--cd-text-primary);

  margin-bottom: 4px;

}



.cd-notify-item-text {

  color: var(--cd-text-secondary);

  font-size: 13px;

  line-height: 1.5;

  overflow: hidden;

  text-overflow: ellipsis;

  display: -webkit-box;

  -webkit-line-clamp: 2;

  -webkit-box-orient: vertical;

}

.cd-notify-actions {
  display: flex;
  gap: 8px;
  margin-top: 10px;
}

</style>
