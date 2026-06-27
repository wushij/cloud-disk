<script setup lang="ts">
import { computed } from 'vue'

const props = withDefaults(
  defineProps<{
    size?: number | string
  }>(),
  { size: '96rpx' }
)

const style = computed(() => ({
  width: props.size,
  height: props.size
}))
</script>

<template>
  <image
    class="brand-mark"
    src="/static/theme-brand-icon-transparent.png"
    mode="aspectFit"
    :style="style"
  />
</template>

<style scoped lang="scss">
.brand-mark {
  display: block;
  flex-shrink: 0;
}
</style>
