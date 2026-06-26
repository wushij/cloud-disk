<script setup lang="ts">
import { nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import videojs from 'video.js'
import type Player from 'video.js/dist/types/player'
import 'video.js/dist/video-js.css'

const props = defineProps<{ src: string }>()

const videoRef = ref<HTMLVideoElement | null>(null)
let player: Player | null = null

function getVideoType(url: string): string {
  const lower = url.toLowerCase()
  if (lower.includes('.m3u8')) return 'application/x-mpegURL'
  if (lower.includes('.webm')) return 'video/webm'
  if (lower.includes('.ogg') || lower.includes('.ogv')) return 'video/ogg'
  return 'video/mp4'
}

function stopPlayback() {
  if (!player) return
  try {
    player.pause()
    if (player.isFullscreen()) {
      player.exitFullscreen()
    }
  } catch {
    /* player may already be disposing */
  }
}

function disposePlayer() {
  stopPlayback()
  if (player) {
    player.dispose()
    player = null
  }
}

defineExpose({ stop: stopPlayback })

async function initPlayer() {
  await nextTick()
  if (!videoRef.value) return
  disposePlayer()

  player = videojs(videoRef.value, {
    controls: true,
    autoplay: false,
    preload: 'auto',
    fluid: false,
    fill: true,
    responsive: true,
    playbackRates: [0.5, 0.75, 1, 1.25, 1.5, 2],
    controlBar: {
      children: [
        'playToggle',
        'currentTimeDisplay',
        'timeDivider',
        'durationDisplay',
        'progressControl',
        'playbackRateMenuButton',
        'volumePanel',
        'fullscreenToggle'
      ],
      pictureInPictureToggle: false,
      volumePanel: { inline: false },
      progressControl: { seekBar: true }
    },
    sources: [{ src: props.src, type: getVideoType(props.src) }]
  })

  player.ready(() => {
    player?.trigger('resize')
  })
}

function updateSource() {
  if (!player) {
    void initPlayer()
    return
  }
  player.src({ src: props.src, type: getVideoType(props.src) })
  player.ready(() => player?.trigger('resize'))
}

watch(() => props.src, () => updateSource())

onMounted(() => {
  void initPlayer()
})

onBeforeUnmount(disposePlayer)
</script>

<template>
  <div class="cd-video-preview">
    <video
      ref="videoRef"
      class="video-js vjs-default-skin vjs-big-play-centered cd-video-player"
      playsinline
    />
  </div>
</template>

<style scoped>
.cd-video-preview {
  position: relative;
  flex: 1;
  min-height: 0;
  width: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 12px 20px 16px;
  box-sizing: border-box;
  background: #0b1120;
}

.cd-video-preview :deep(.video-js) {
  width: 100% !important;
  max-width: 100% !important;
  height: calc(88vh - 120px) !important;
  max-height: calc(88vh - 120px) !important;
  border-radius: 10px;
  overflow: hidden;
  background: #000;
}

.cd-video-preview :deep(.vjs-tech) {
  object-fit: contain;
}

.cd-video-preview :deep(.vjs-control-bar) {
  display: flex !important;
  opacity: 1 !important;
  visibility: visible !important;
  height: 3.6em;
  padding: 0 12px 8px;
  align-items: flex-end;
  background: linear-gradient(180deg, transparent 0%, rgba(0, 0, 0, 0.55) 40%, rgba(0, 0, 0, 0.88) 100%);
}

.cd-video-preview :deep(.video-js.vjs-fullscreen) {
  width: 100vw !important;
  height: 100vh !important;
  max-height: 100vh !important;
  padding-top: 0 !important;
  border-radius: 0;
}

.cd-video-preview :deep(.video-js.vjs-fullscreen .vjs-control-bar) {
  height: 4.2em;
  padding: 0 24px calc(14px + env(safe-area-inset-bottom, 0px));
  background: linear-gradient(180deg, transparent 0%, rgba(0, 0, 0, 0.65) 35%, rgba(0, 0, 0, 0.94) 100%);
  box-shadow: 0 -6px 28px rgba(0, 0, 0, 0.4);
}

.cd-video-preview :deep(.vjs-progress-control) {
  cursor: pointer;
}

.cd-video-preview :deep(.vjs-playback-rate .vjs-playback-rate-value) {
  font-size: 1.1em;
  line-height: 2.4em;
}

.cd-video-preview :deep(.vjs-volume-panel .vjs-volume-control) {
  width: 3em;
  height: 6.5em;
}

.cd-video-preview :deep(.vjs-volume-panel.vjs-hover .vjs-volume-control),
.cd-video-preview :deep(.vjs-volume-panel:active .vjs-volume-control),
.cd-video-preview :deep(.vjs-volume-panel:focus .vjs-volume-control) {
  opacity: 1;
  visibility: visible;
}
</style>
