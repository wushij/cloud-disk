import { ref } from 'vue'
import { defineStore } from 'pinia'
import http from '@/api/http'

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
      const { data } = await http.get<StorageUsage>('/api/storage/usage')
      usage.value = data
    } catch {
      /* ignore */
    }
  }

  return { usage, refresh }
})
