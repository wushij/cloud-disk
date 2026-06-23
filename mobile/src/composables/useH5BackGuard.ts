import { onMounted, onUnmounted, watch } from 'vue'

export interface H5BackGuardOptions {
  /** 当前可返回层数：根目录为 0，一级子目录为 1，以此类推（breadcrumb.length - 1）*/
  depth: () => number
  /** 页面内返回（上一级文件夹） */
  onAppBack: () => void
  /**
   * 已在最顶层时的返回（如团队文件根目录返回团队列表）。
   * 未设置时直接交给浏览器返回上一页。
   */
  onRootBack?: () => void
}

/**
 * H5 浏览器右滑 / 返回键：子级先页面内返回，根级交给浏览器或 onRootBack。
 */
export function useH5BackGuard(options: H5BackGuardOptions) {
  if (typeof window === 'undefined' || !window || typeof history === 'undefined' || !history) {
    return
  }

  let allowLeave = false

  function getNormalizedDepth() {
    return Math.max(0, options.depth())
  }

  let lastSyncedDepth = (typeof history !== 'undefined' && history.state && history.state.cdBackGuard && typeof history.state.depth === 'number')
    ? history.state.depth
    : getNormalizedDepth()

  function pushTrap(depth: number) {
    const currentState = history.state || {}
    history.pushState({ ...currentState, cdBackGuard: true, depth }, '', location.href)
  }

  function onPopState(event: PopStateEvent) {
    if (allowLeave) {
      allowLeave = false
      return
    }

    const state = event.state
    if (state && typeof state.depth === 'number') {
      const targetDepth = state.depth
      const currentDepth = getNormalizedDepth()

      if (currentDepth !== targetDepth && targetDepth < currentDepth) {
        lastSyncedDepth = targetDepth
        const steps = currentDepth - targetDepth
        for (let i = 0; i < steps; i++) {
          options.onAppBack()
        }
        return
      }

      // H5 刷新后残留的 trap 状态：历史中的 depth 比当前实际 depth 大
      // （刷新后 breadcrumb 重置为根，但浏览器历史还有旧的深层 trap）
      // 此时放行继续回退，直到退到正常的上一页
      if (targetDepth > currentDepth) {
        lastSyncedDepth = currentDepth
        allowLeave = true
        history.back()
        return
      }

      return
    }

    // 兜底保护：历史 state 不是我们的格式（uni-app 路由推入等），但应用仍处于子目录（depth>0）
    // 注意：depth 语义已改为"可返回层数"，根目录 = 0，所以 depth>0 时 onAppBack 一定会执行
    const currentDepth = getNormalizedDepth()
    if (currentDepth > 0) {
      const prevDepth = currentDepth
      options.onAppBack()
      const newDepth = getNormalizedDepth()
      lastSyncedDepth = newDepth
      // 只有当 onAppBack 真正减少了层级，且仍在子目录中，才重新推入 trap 维持拦截
      if (newDepth < prevDepth && newDepth > 0) {
        const currentState = history.state || {}
        history.pushState({ ...currentState, cdBackGuard: true, depth: newDepth }, '', location.href)
      }
      return
    }

    if (options.onRootBack) {
      options.onRootBack()
      return
    }
  }

  watch(getNormalizedDepth, (newDepth) => {
    if (newDepth === lastSyncedDepth) return

    const oldDepth = lastSyncedDepth
    lastSyncedDepth = newDepth

    if (newDepth > oldDepth) {
      for (let d = oldDepth + 1; d <= newDepth; d++) {
        pushTrap(d)
      }
    } else if (newDepth < oldDepth) {
      const diff = oldDepth - newDepth
      if (diff > 0) {
        allowLeave = true
        history.go(-diff)
      }
    }
  })

  onMounted(() => {
    const currentState = history.state || {}
    const hasValidState = currentState.cdBackGuard && typeof currentState.depth === 'number'
    if (!hasValidState) {
      history.replaceState({ ...currentState, cdBackGuard: true, depth: getNormalizedDepth() }, '', location.href)
    }
    window.addEventListener('popstate', onPopState)
  })

  onUnmounted(() => {
    window.removeEventListener('popstate', onPopState)
    allowLeave = false
  })
}
