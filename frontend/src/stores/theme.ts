import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import {
  applyThemePreset,
  applyCustomTheme,
  DEFAULT_THEME_ID,
  getThemePreset,
  type ThemePresetId,
  type ThemeSelection,
  THEME_PRESETS
} from '@/utils/theme'

const STORAGE_KEY = 'cd_theme_preset'
const CUSTOM_COLOR_KEY = 'cd_theme_custom_color'

export const useThemeStore = defineStore('theme', () => {
  const presetId = ref<ThemeSelection>(DEFAULT_THEME_ID)
  const customColor = ref('#010710')

  const currentPrimary = computed(() => {
    if (presetId.value === 'custom') return customColor.value
    return getThemePreset(presetId.value).primary
  })

  const isCustom = computed(() => presetId.value === 'custom')

  function init() {
    const saved = localStorage.getItem(STORAGE_KEY)
    if (saved === 'custom') {
      const color = localStorage.getItem(CUSTOM_COLOR_KEY)
      if (color) {
        setCustomColor(color, false)
        return
      }
    }
    const id = saved && THEME_PRESETS.some((p) => p.id === saved) ? (saved as ThemePresetId) : DEFAULT_THEME_ID
    setPreset(id)
  }

  function setPreset(id: ThemePresetId) {
    presetId.value = id
    applyThemePreset(id)
    localStorage.setItem(STORAGE_KEY, id)
    localStorage.removeItem(CUSTOM_COLOR_KEY)
  }

  function setCustomColor(color: string, persist = true) {
    const normalized = applyCustomTheme(color)
    presetId.value = 'custom'
    customColor.value = normalized
    if (persist) {
      localStorage.setItem(STORAGE_KEY, 'custom')
      localStorage.setItem(CUSTOM_COLOR_KEY, normalized)
    }
  }

  return {
    presetId,
    customColor,
    currentPrimary,
    isCustom,
    init,
    setPreset,
    setCustomColor,
    presets: THEME_PRESETS
  }
})
