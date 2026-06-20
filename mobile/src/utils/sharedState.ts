import { ref } from 'vue'

export const globalShareList = ref<any[]>([])
export const globalTeamList = ref<any[]>([])

// Storage usage persistent cache
const cachedUsage = uni.getStorageSync('cd_storage_usage')
export const globalStorageUsage = ref<{ usedBytes?: number; quotaBytes?: number } | null>(
  cachedUsage ? JSON.parse(cachedUsage) : null
)

export function updateStorageUsage(data: { usedBytes?: number; quotaBytes?: number } | null) {
  globalStorageUsage.value = data
  if (data) {
    uni.setStorageSync('cd_storage_usage', JSON.stringify(data))
  } else {
    uni.removeStorageSync('cd_storage_usage')
  }
}
