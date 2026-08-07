<script setup lang="ts">
import { ref, computed } from 'vue'
import { onLaunch, onShow } from '@dcloudio/uni-app'
import { useAuthStore } from '@/stores/auth'
import { useNotificationStore } from '@/stores/notification'
import { subscribeWs } from '@/utils/ws'
import { updateStorageUsage } from '@/utils/sharedState'
import { request } from '@/api/http'
import { redirectPublicSharePathIfNeeded } from '@/utils/shareUrl'
import { getClientId, requestSessionSignKey } from '@/utils/security-config'

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

/**
 * 启动时主动协商会话签名密钥
 * 解决「PC 端在用户开启 SM3 签名后，移动端因密钥/开关状态未同步而 403」的问题。
 * 后端 /api/auth/session-sign-init 会同步下发当前安全开关状态，
 * 移动端据此刷新本地 secConfig，后续请求自动启用/关闭签名/加密。
 */
async function initSecurityConfig() {
  // clientId 已持久化到 sessionStorage，同一 H5 会话内稳定；
  // 首次协商的会话密钥在 TTL（120 分钟）内一直有效，无需每次 onShow 都强制重新协商。
  // 若此处强制 resetSecurityConfigAndPromise，会在 onShow 频繁触发时反复生成新 key 覆盖 Redis，
  // 造成"前端旧 key 与后端 Redis 新 key 不一致"的偶发/持续 403。
  // requestSessionSignKey 内部已有 early-return：key 存在时不再协商，仅在 key 为空/403 失效时协商。
  const requestFn = () =>
    request<any>({
      url: `/api/auth/session-sign-init?clientId=${getClientId()}`,
      method: 'POST',
      data: undefined,
      skipErrorHandler: true,
    } as any)
  try {
    await requestSessionSignKey(requestFn)
  } catch {
    /* 密钥协商失败不阻塞应用启动 */
  }
}

onLaunch(() => {
  useAuthStore().restore()
  redirectPublicSharePathIfNeeded()
})

onShow(() => {
  const auth = useAuthStore()
  if (auth.isLoggedIn) {
    // 仅在已登录后协商签名密钥：确保 Sa-Token 已识别登录态，
    // 后端 session-sign-init 会建立 clientId-user 绑定，否则后续签名请求会因绑定缺失而 403。
    void initSecurityConfig()
    void auth.ensureMediaToken().catch(() => {})
    void auth.fetchProfile().catch(() => {})
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
