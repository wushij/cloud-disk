<script setup lang="ts">
import { ref, watch } from 'vue'
import { loadEntityAvatarThumb, cacheEntityAvatarFromUrl } from '@/utils/entityAvatarCache'

const props = withDefaults(
  defineProps<{
    cacheKey: string
    src: string
    version?: number | string
    alt?: string
    imgClass?: string
  }>(),
  { alt: '', version: 0, imgClass: '' }
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
  <img
    v-if="displaySrc"
    :src="displaySrc"
    :alt="alt"
    :class="imgClass"
    @error="emit('error')"
  />
</template>
