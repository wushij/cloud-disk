<script setup lang="ts">
import { onLaunch } from '@dcloudio/uni-app'
import { useAuthStore } from '@/stores/auth'

onLaunch(() => {
  useAuthStore().restore()
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

/* 圆角弹出层 */
.u-action-sheet {
  border-radius: var(--cd-radius-xl) var(--cd-radius-xl) 0 0 !important;
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
