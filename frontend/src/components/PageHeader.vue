<script setup lang="ts">
import type { Component } from 'vue'

withDefaults(
  defineProps<{
    title: string
    description?: string
    icon?: Component
    count?: number
    countLabel?: string
  }>(),
  { countLabel: '项' }
)
</script>

<template>
  <div class="cd-page-header">
    <div class="cd-page-header-main">
      <div v-if="icon" class="cd-page-header-icon">
        <el-icon :size="20"><component :is="icon" /></el-icon>
      </div>
      <div class="cd-page-header-text">
        <div class="cd-page-header-title-row">
          <h2 class="cd-page-header-title">{{ title }}</h2>
          <el-tag v-if="count !== undefined" size="small" round type="info" effect="plain">
            {{ count }} {{ countLabel }}
          </el-tag>
        </div>
        <p v-if="description" class="cd-page-header-desc">{{ description }}</p>
      </div>
    </div>
    <div v-if="$slots.actions" class="cd-page-header-actions">
      <slot name="actions" />
    </div>
  </div>
</template>

<style scoped>
.cd-page-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  padding: 18px 22px;
  border-bottom: 1px solid var(--cd-border-light);
  background: linear-gradient(180deg, #ffffff 0%, color-mix(in srgb, var(--theme-bg) 25%, #fff) 100%);
}

.cd-page-header-main {
  display: flex;
  align-items: flex-start;
  gap: 14px;
  min-width: 0;
}

.cd-page-header-icon {
  width: 42px;
  height: 42px;
  border-radius: 12px;
  background: var(--theme-primary-muted);
  color: var(--cd-primary);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  box-shadow: inset 0 0 0 1px color-mix(in srgb, var(--cd-primary) 12%, transparent);
}

.cd-page-header-title-row {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.cd-page-header-title {
  margin: 0;
  font-size: 17px;
  font-weight: 700;
  color: var(--cd-text-primary);
  letter-spacing: -0.02em;
}

.cd-page-header-desc {
  margin: 6px 0 0;
  font-size: 13px;
  color: var(--cd-text-secondary);
  line-height: 1.5;
}

.cd-page-header-actions {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  gap: 8px;
}
</style>
