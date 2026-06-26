<script setup lang="ts">
import { ref, watch } from 'vue'
import { loadEntityAvatarThumb, cacheEntityAvatarFromUrl } from '@/utils/entityAvatarCache'

const props = withDefaults(
  defineProps<{
    cacheKey: string
    src: string
    version?: number | string
  }>(),
  { version: 0 }
)

const emit = defineEmits<{ error: [] }>()

const displaySrc = ref('')

function sync() {
  if (!props.cacheKey || !props.src) {
    displaySrc.value = ''
    return
  }
  const cached = loadEntityAvatarThumb(props.cacheKey, props.version ?? 0)
  displaySrc.value = cached || props.src
  if (!cached) {
    void cacheEntityAvatarFromUrl(props.cacheKey, props.version ?? 0, props.src)
      .then((data) => {
        displaySrc.value = data
      })
      .catch(() => {})
  }
}

watch(
  () => [props.cacheKey, props.src, props.version] as const,
  sync,
  { immediate: true }
)
</script>

<template>
  <image
    v-if="displaySrc"
    :src="displaySrc"
    class="cached-entity-avatar"
    mode="aspectFill"
    @error="emit('error')"
  />
</template>

<style scoped>
.cached-entity-avatar {
  width: 100%;
  height: 100%;
}
</style>
