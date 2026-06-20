<script setup lang="ts">
import { storeToRefs } from 'pinia'
import { useConfirmDialogStore } from '@/stores/confirmDialog'

const store = useConfirmDialogStore()
const { visible, title, message, confirmText, cancelText, danger } = storeToRefs(store)
</script>

<template>
  <Teleport to="body">
    <Transition name="cd-confirm-fade">
      <div v-if="visible" class="cd-confirm-root" @keydown.esc="store.cancel()">
        <div class="cd-confirm-mask" @click="store.cancel()" />
        <div class="cd-confirm-panel" role="dialog" aria-modal="true">
          <div v-if="danger" class="cd-confirm-icon cd-confirm-icon--danger">
            <el-icon :size="28"><WarningFilled /></el-icon>
          </div>
          <h3 class="cd-confirm-title">{{ title }}</h3>
          <p v-if="message" class="cd-confirm-message">{{ message }}</p>

          <div class="cd-confirm-actions">
            <button type="button" class="cd-confirm-btn cd-confirm-btn--ghost" @click="store.cancel()">
              {{ cancelText }}
            </button>
            <button
              type="button"
              class="cd-confirm-btn"
              :class="danger ? 'cd-confirm-btn--danger' : 'cd-confirm-btn--primary'"
              @click="store.confirm()"
            >
              {{ confirmText }}
            </button>
          </div>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<style scoped>
.cd-confirm-root {
  position: fixed;
  inset: 0;
  z-index: 3000;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
}

.cd-confirm-mask {
  position: absolute;
  inset: 0;
  background: rgba(15, 23, 42, 0.42);
  backdrop-filter: blur(4px);
}

.cd-confirm-panel {
  position: relative;
  z-index: 1;
  width: 100%;
  max-width: 420px;
  padding: 28px 28px 24px;
  background: var(--cd-bg-white, #fff);
  border-radius: var(--cd-radius-xl, 20px);
  border: 1px solid var(--cd-border-light);
  box-shadow: var(--cd-shadow-xl), 0 0 0 1px rgba(255, 255, 255, 0.5) inset;
}

.cd-confirm-icon {
  width: 56px;
  height: 56px;
  margin: 0 auto 16px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
}

.cd-confirm-icon--danger {
  background: rgba(239, 68, 68, 0.1);
  color: #ef4444;
}

.cd-confirm-title {
  margin: 0;
  text-align: center;
  font-size: 18px;
  font-weight: 800;
  color: var(--cd-text-primary);
  letter-spacing: -0.02em;
}

.cd-confirm-message {
  margin: 12px 0 0;
  text-align: center;
  font-size: 14px;
  line-height: 1.6;
  color: var(--cd-text-secondary);
}

.cd-confirm-actions {
  margin-top: 24px;
  display: flex;
  gap: 12px;
}

.cd-confirm-btn {
  flex: 1;
  height: 44px;
  border: none;
  border-radius: 999px;
  font-size: 14px;
  font-weight: 700;
  cursor: pointer;
  transition: transform 0.15s ease, opacity 0.15s ease, box-shadow 0.15s ease;
}

.cd-confirm-btn:active {
  transform: scale(0.98);
}

.cd-confirm-btn--ghost {
  background: #f1f5f9;
  color: var(--cd-text-secondary);
  border: 1px solid var(--cd-border);
}

.cd-confirm-btn--ghost:hover {
  background: #e2e8f0;
}

.cd-confirm-btn--primary {
  background: var(--cd-primary-gradient);
  color: #fff;
  box-shadow: 0 8px 20px rgba(1, 7, 16, 0.18);
}

.cd-confirm-btn--primary:hover {
  opacity: 0.95;
}

.cd-confirm-btn--danger {
  background: linear-gradient(135deg, #ef4444 0%, #dc2626 100%);
  color: #fff;
  box-shadow: 0 8px 20px rgba(239, 68, 68, 0.28);
}

.cd-confirm-btn--danger:hover {
  opacity: 0.95;
}

.cd-confirm-fade-enter-active,
.cd-confirm-fade-leave-active {
  transition: opacity 0.2s ease;
}

.cd-confirm-fade-enter-active .cd-confirm-panel,
.cd-confirm-fade-leave-active .cd-confirm-panel {
  transition: transform 0.2s ease, opacity 0.2s ease;
}

.cd-confirm-fade-enter-from,
.cd-confirm-fade-leave-to {
  opacity: 0;
}

.cd-confirm-fade-enter-from .cd-confirm-panel,
.cd-confirm-fade-leave-to .cd-confirm-panel {
  transform: scale(0.96) translateY(8px);
  opacity: 0;
}
</style>
