<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import videojs from 'video.js'
import 'video.js/dist/video-js.css'

const props = defineProps<{ src: string }>()
const videoRef = ref<HTMLVideoElement | null>(null)
let player: ReturnType<typeof videojs> | null = null

function initPlayer() {
  if (!videoRef.value) return
  dispose()
  player = videojs(videoRef.value, {
    controls: true,
    fluid: true,
    preload: 'auto',
    playbackRates: [0.5, 1, 1.5, 2],
    sources: [{ src: props.src, type: getVideoType(props.src) }]
  })
}

function getVideoType(url: string): string {
  const lower = url.toLowerCase()
  if (lower.includes('.m3u8')) return 'application/x-mpegURL'
  if (lower.includes('.webm')) return 'video/webm'
  if (lower.includes('.ogg') || lower.includes('.ogv')) return 'video/ogg'
  return 'video/mp4'
}

function dispose() {
  if (player) {
    player.dispose()
    player = null
  }
}

onMounted(initPlayer)
watch(() => props.src, initPlayer)
onBeforeUnmount(dispose)
</script>

<template>
  <div class="video-wrap">
    <video ref="videoRef" class="video-js vjs-default-skin" playsinline />
  </div>
</template>

<style scoped>
.video-wrap {
  width: 100%;
}
</style>
