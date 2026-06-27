<script setup lang="ts">
import { computed } from 'vue'

const props = withDefaults(
  defineProps<{
    size?: number | string
  }>(),
  { size: 48 }
)

const style = computed(() => {
  const v = props.size
  const px = typeof v === 'number' ? `${v}px` : v
  return { width: px, height: px }
})
</script>

<template>
  <img
    class="brand-mark"
    src="/theme-brand-icon-transparent.png"
    alt="CloudDisk Pro"
    :style="style"
    draggable="false"
  />
</template>

<style scoped>
.brand-mark {
  display: block;
  object-fit: contain;
  flex-shrink: 0;
  user-select: none;
}
</style>
