<script setup lang="ts">
import { ref, watch } from 'vue'
import { coverCacheVersion, loadCoverThumb, cacheCoverFromUrl, cacheCoverFromDataUrl } from '@/utils/coverCache'

const props = withDefaults(
  defineProps<{
    fileId: number
    src: string
    hasThumbnail?: boolean
  }>(),
  {}
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
      return
    }
    const isH5Preview =
      typeof uni.getFileSystemManager !== 'function' &&
      props.src.includes('/preview') &&
      !props.hasThumbnail
    if (isH5Preview) {
      return
    }
    void cacheCoverFromUrl(props.fileId, version, props.src)
      .then((data) => {
        displaySrc.value = data
      })
      .catch(() => {})
  }
}

watch(
  () => [props.fileId, props.src, props.hasThumbnail] as const,
  sync,
  { immediate: true }
)
</script>

<template>
  <image
    v-if="displaySrc"
    :src="displaySrc"
    class="cached-cover"
    mode="aspectFill"
    @error="emit('error')"
  />
</template>

<style scoped>
.cached-cover {
  width: 100%;
  height: 100%;
}
</style>
