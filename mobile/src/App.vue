<script setup lang="ts">
import { ref, computed } from 'vue'
import { onLaunch, onShow } from '@dcloudio/uni-app'
import { useAuthStore } from '@/stores/auth'
import { useNotificationStore } from '@/stores/notification'
import { subscribeWs } from '@/utils/ws'
import { updateStorageUsage } from '@/utils/sharedState'
import { request } from '@/api/http'
import { redirectPublicSharePathIfNeeded } from '@/utils/shareUrl'

let unsubscribeWs: (() => void) | null = null

function setupNotifications() {
  const auth = useAuthStore()
  const notifyStore = useNotificationStore()
  if (!auth.isLoggedIn) {
    unsubscribeWs?.()
    unsubscribeWs = null
    return
  }
  notifyStore.loadFromApi().catch(() => {})
  if (unsubscribeWs) return
  unsubscribeWs = subscribeWs((data) => {
    if (data.type === 'notification') {
      notifyStore.push({
        id: data.notifyId,
        type: data.notifyType,
        title: data.title,
        content: data.content,
        refId: data.refId,
        inviteStatus: data.inviteStatus,
        registrationStatus: data.registrationStatus,
        quotaStatus: data.quotaStatus
      })
      if (data.notifyType === 'ROLE_CHANGED' || data.notifyType === 'QUOTA_RESULT') {
        auth.fetchProfile().catch(() => {})
        request<{ usedBytes?: number; quotaBytes?: number }>({ url: '/api/storage/usage' })
          .then((usage) => updateStorageUsage(usage))
          .catch(() => {})
      }
    }
  })
}

onLaunch(() => {
  useAuthStore().restore()
  redirectPublicSharePathIfNeeded()
})

onShow(() => {
  const auth = useAuthStore()
  if (auth.isLoggedIn) {
    void auth.ensureMediaToken().catch(() => {})
  }
  setupNotifications()
})
</script>

<style lang="scss">
@import '@/styles/theme.scss';
@import '@/styles/page-shell.scss';
@import 'uview-plus/index.scss';

page {
  background: var(--cd-bg);
  color: var(--cd-text);
  font-family: -apple-system, BlinkMacSystemFont, 'PingFang SC', 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif;
  -webkit-font-smoothing: antialiased;
  -moz-osx-font-smoothing: grayscale;
  font-size: 28rpx;
  line-height: 1.5;
}

/* 重置 uView 默认样式 */
.u-icon {
  display: flex !important;
  align-items: center;
  justify-content: center;
}

/* 圆角弹出层及高度优化 */
.u-action-sheet {
  border-radius: var(--cd-radius-xl) var(--cd-radius-xl) 0 0 !important;
}

.u-action-sheet__item-wrap__item {
  padding: 20rpx 0 !important;
}

/* 加载图标颜色 */
.u-loading-icon {
  color: var(--cd-primary);
}

/* 搜索组件优化 */
:deep(.u-search__content) {
  border-radius: var(--cd-radius-full) !important;
}
</style>
