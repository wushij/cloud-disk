<script setup lang="ts">
import { ref, watch } from 'vue'
import { coverCacheVersion, loadCoverThumb, cacheCoverFromUrl, cacheCoverFromDataUrl } from '@/utils/coverCache'

const props = withDefaults(
  defineProps<{
    fileId: number
    src: string
    alt?: string
    lazy?: boolean
    hasThumbnail?: boolean
    imgClass?: string
  }>(),
  { alt: '', lazy: true, imgClass: '' }
)

const emit = defineEmits<{ error: [] }>()

const displaySrc = ref('')

function sync() {
  if (!props.src || !props.fileId) {
    displaySrc.value = ''
    return
  }
  const version = coverCacheVersion(props.hasThumbnail)
  const cached = loadCoverThumb(props.fileId, version)
  displaySrc.value = cached || props.src
  if (!cached) {
    if (props.src.startsWith('data:')) {
      cacheCoverFromDataUrl(props.fileId, version, props.src)
      displaySrc.value = props.src
    } else {
      void cacheCoverFromUrl(props.fileId, version, props.src)
        .then((data) => {
          displaySrc.value = data
        })
        .catch(() => {})
    }
  }
}

watch(
  () => [props.fileId, props.src, props.hasThumbnail] as const,
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
    :loading="lazy ? 'lazy' : undefined"
    @error="emit('error')"
  />
</template>
