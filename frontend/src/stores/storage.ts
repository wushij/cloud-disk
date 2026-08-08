import { ref } from 'vue'
import { defineStore } from 'pinia'
import { storageApi } from '@/api'

export interface StorageUsage {
  usedBytes: number
  quotaBytes: number
  usedPercent: number
  usedFormatted: string
  quotaFormatted: string
}

export const useStorageStore = defineStore('storage', () => {
  const usage = ref<StorageUsage | null>(null)

  async function refresh() {
    try {
      const data = await storageApi.usage()
      usage.value = data
    } catch {
      /* ignore */
    }
  }

  return { usage, refresh }
})
