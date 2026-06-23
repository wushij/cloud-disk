<script setup lang="ts">
import { ref, watch } from 'vue'
import { Brush } from '@element-plus/icons-vue'
import { useThemeStore } from '@/stores/theme'
import { normalizeHex, PRESET_COLORS, type ThemePresetId } from '@/utils/theme'

const themeStore = useThemeStore()
const visible = ref(false)
const draftColor = ref(themeStore.currentPrimary)

watch(
  () => themeStore.currentPrimary,
  (color) => {
    draftColor.value = color
  }
)

watch(visible, (open) => {
  if (open) draftColor.value = themeStore.currentPrimary
})

const predefineColors = PRESET_COLORS

function pick(id: ThemePresetId) {
  themeStore.setPreset(id)
  draftColor.value = themeStore.currentPrimary
}

function applyDraft(color: string | null) {
  if (!color) return
  const normalized = normalizeHex(color)
  draftColor.value = normalized
  themeStore.setCustomColor(normalized)
}

function onHexInput(val: string) {
  applyDraft(val)
}
</script>

<template>
  <el-popover
    v-model:visible="visible"
    placement="bottom-end"
    :width="280"
    trigger="click"
    popper-class="theme-picker-popper"
  >
    <template #reference>
      <el-button class="cd-header-btn theme-picker-trigger" circle title="外观主题">
        <el-icon :size="18"><Brush /></el-icon>
      </el-button>
    </template>

    <div class="theme-picker-panel">
      <p class="theme-picker-title">外观主题</p>
      <p class="theme-picker-desc">侧边栏保持白色，切换主色与页面背景</p>

      <!-- 原有预设布局：4 列圆点 -->
      <div class="theme-picker-grid">
        <button
          v-for="preset in themeStore.presets"
          :key="preset.id"
          type="button"
          class="theme-picker-item"
          :class="{ active: themeStore.presetId === preset.id }"
          :title="preset.label"
          @click="pick(preset.id)"
        >
          <span class="theme-picker-dot" :style="{ background: preset.primary }" />
          <span class="theme-picker-label">{{ preset.label }}</span>
        </button>
      </div>

      <!-- 自定义取色：追加在下方，不改变上方布局 -->
      <div class="theme-picker-divider" />

      <div class="theme-custom-block">
        <span class="theme-custom-label">自定义颜色</span>
        <el-color-picker-panel
          v-model="draftColor"
          :border="true"
          color-format="hex"
          :predefine="predefineColors"
          class="theme-color-panel"
          @update:model-value="applyDraft"
        />
        <div class="theme-hex-row">
          <el-input
            :model-value="draftColor"
            maxlength="7"
            spellcheck="false"
            @update:model-value="onHexInput"
          />
        </div>
      </div>
    </div>
  </el-popover>
</template>

<style scoped>
.theme-picker-trigger {
  width: 36px !important;
  height: 36px !important;
  border: none !important;
  color: #111827 !important;
  transition: var(--cd-transition-fast);
}

.theme-picker-trigger:hover {
  background: var(--cd-primary-bg) !important;
  color: #111827 !important;
}

.theme-picker-panel {
  padding: 4px 2px 2px;
}

.theme-picker-title {
  margin: 0 0 4px;
  font-size: 14px;
  font-weight: 600;
  color: var(--theme-text-base);
}

.theme-picker-desc {
  margin: 0 0 12px;
  font-size: 12px;
  color: var(--theme-text-secondary);
  line-height: 1.4;
}

.theme-picker-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 8px;
}

.theme-picker-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 5px;
  padding: 8px 4px;
  border: 1px solid var(--theme-border);
  border-radius: 8px;
  background: #fff;
  cursor: pointer;
  transition: border-color 0.15s ease, background 0.15s ease;
}

.theme-picker-item:hover {
  border-color: color-mix(in srgb, var(--theme-primary) 40%, var(--theme-border));
  background: var(--theme-primary-muted);
}

.theme-picker-item.active {
  border-color: var(--theme-primary);
  background: var(--theme-primary-muted-strong);
}

.theme-picker-dot {
  width: 20px;
  height: 20px;
  border-radius: 50%;
  box-shadow: inset 0 0 0 2px rgba(255, 255, 255, 0.85);
}

.theme-picker-label {
  font-size: 11px;
  color: var(--theme-text-base);
  font-weight: 500;
}

.theme-picker-divider {
  height: 1px;
  background: var(--theme-border);
  margin: 14px 0 12px;
}

.theme-custom-block {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.theme-custom-label {
  font-size: 11px;
  font-weight: 600;
  color: var(--theme-text-secondary);
  letter-spacing: 0.04em;
  text-transform: uppercase;
}

.theme-color-panel {
  width: 100%;
}

.theme-color-panel :deep(.el-color-picker-panel) {
  width: 100%;
}

.theme-color-panel :deep(.el-color-svpanel) {
  width: 100% !important;
}

.theme-hex-row :deep(.el-input__wrapper) {
  font-family: ui-monospace, 'SF Mono', 'Fira Code', monospace;
  font-weight: 600;
  border-radius: 8px !important;
}
</style>

<style>
.theme-picker-popper {
  padding: 14px 16px 16px !important;
}
</style>
