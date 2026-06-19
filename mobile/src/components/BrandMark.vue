<script setup lang="ts">
withDefaults(
  defineProps<{
    size?: number | string
    variant?: 'app' | 'mono'
  }>(),
  { size: 48, variant: 'app' }
)
</script>

<template>
  <svg
    class="brand-mark"
    :class="`brand-mark--${variant}`"
    :width="size"
    :height="size"
    viewBox="0 0 48 48"
    xmlns="http://www.w3.org/2000/svg"
    aria-hidden="true"
  >
    <rect v-if="variant === 'app'" width="48" height="48" rx="11" fill="#010710" />
    <path
      class="brand-cloud"
      d="M13.5 31c-2.2 0-4-1.8-4-4 0-1.7 1.1-3.1 2.6-3.6-.3-4.2 3-7.6 7.2-7.6 2.1 0 4 0.9 5.3 2.3 1.2-.5 2.6-.8 4-.8 4.3 0 7.8 3.3 8.2 7.5 1.9.5 3.2 2.1 3.2 3.9 0 2.3-1.9 4.2-4.2 4.2H13.5z"
    />
    <path
      class="brand-arrow"
      fill="none"
      stroke-width="2.5"
      stroke-linecap="round"
      stroke-linejoin="round"
      d="M24 20.5v8M20.5 24.5 24 20.5 27.5 24.5"
    />
  </svg>
</template>

<style scoped lang="scss">
.brand-mark--app {
  filter: drop-shadow(0 8rpx 24rpx rgba(1, 7, 16, 0.22));
}

.brand-cloud {
  fill: #ffffff;
}

.brand-arrow {
  stroke: #010710;
}
</style>
