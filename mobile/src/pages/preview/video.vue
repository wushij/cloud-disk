<script setup lang="ts">
import { onMounted, onUnmounted, ref } from 'vue'
import { onLoad, onUnload } from '@dcloudio/uni-app'
import MobileRateSheet from '@/components/MobileRateSheet.vue'

const PLAYBACK_RATES = [0.5, 0.75, 1, 1.25, 1.5, 2] as const
const VIDEO_ID = 'cd-mobile-video'

const url = ref('')
const name = ref('')
const loadError = ref('')
const playbackRate = ref(1)
const rateSheetVisible = ref(false)
const isLandscapeFullscreen = ref(false)
const isPlaying = ref(false)
const isH5 = import.meta.env.UNI_PLATFORM === 'h5'

onLoad((query) => {
  url.value = decodeURIComponent((query?.url as string) || '')
  name.value = decodeURIComponent((query?.name as string) || '视频播放')
})

function formatRate(rate: number) {
  return Number.isInteger(rate) ? `${rate}x` : `${rate}x`
}

function lockLandscape() {
  // #ifdef H5
  try {
    const orientation = screen.orientation as ScreenOrientation & { lock?: (o: string) => Promise<void> }
    if (orientation?.lock) {
      void orientation.lock('landscape').catch(() => {})
    }
  } catch {
    /* orientation lock may be denied outside fullscreen */
  }
  // #endif
}

function unlockLandscape() {
  // #ifdef H5
  try {
    screen.orientation?.unlock?.()
  } catch {
    /* ignore */
  }
  // #endif
}

function getVideoContext() {
  return uni.createVideoContext(VIDEO_ID)
}

function onFullscreenChange(e: { detail: { fullScreen: boolean } }) {
  isLandscapeFullscreen.value = e.detail.fullScreen
  if (e.detail.fullScreen) {
    lockLandscape()
  } else {
    unlockLandscape()
  }
}

function enterLandscapeFullscreen() {
  isLandscapeFullscreen.value = true
  lockLandscape()
  if (isH5) {
    // #ifdef H5
    const el = document.querySelector(`#${VIDEO_ID} video, #${VIDEO_ID}`) as HTMLElement | null
    const video = el?.tagName === 'VIDEO' ? el : el?.querySelector?.('video')
    void (video as HTMLVideoElement | undefined)?.requestFullscreen?.().catch(() => {})
    // #endif
    return
  }
  getVideoContext().requestFullScreen({ direction: 90 })
}

function exitLandscapeFullscreen() {
  isLandscapeFullscreen.value = false
  unlockLandscape()
  if (isH5) {
    // #ifdef H5
    if (document.fullscreenElement) {
      void document.exitFullscreen().catch(() => {})
    }
    // #endif
    return
  }
  getVideoContext().exitFullScreen()
}

function toggleLandscapeFullscreen() {
  if (isLandscapeFullscreen.value) {
    exitLandscapeFullscreen()
  } else {
    enterLandscapeFullscreen()
  }
}

function showRatePicker() {
  rateSheetVisible.value = true
}

function onPlay() {
  isPlaying.value = true
}

function onPause() {
  isPlaying.value = false
}

function togglePlay() {
  const ctx = getVideoContext()
  if (isPlaying.value) {
    ctx.pause()
  } else {
    ctx.play()
  }
}

function onError() {
  loadError.value = '视频加载失败，请稍后重试'
}

function onBrowserFullscreenChange() {
  // #ifdef H5
  if (!document.fullscreenElement && isLandscapeFullscreen.value) {
    isLandscapeFullscreen.value = false
    unlockLandscape()
  }
  // #endif
}

function cleanupPlayback() {
  unlockLandscape()
  if (isH5) {
    // #ifdef H5
    if (document.fullscreenElement) {
      void document.exitFullscreen().catch(() => {})
    }
    // #endif
    return
  }
  getVideoContext().exitFullScreen()
}

onMounted(() => {
  // #ifdef H5
  document.addEventListener('fullscreenchange', onBrowserFullscreenChange)
  // #endif
})

onUnload(cleanupPlayback)
onUnmounted(() => {
  // #ifdef H5
  document.removeEventListener('fullscreenchange', onBrowserFullscreenChange)
  // #endif
  cleanupPlayback()
})
</script>

<template>
  <view class="page" :class="{ 'is-landscape-fs': isLandscapeFullscreen }">
    <view v-if="!isLandscapeFullscreen" class="top-bar">
      <text class="title">{{ name }}</text>
    </view>

    <view class="player-wrap">
      <video
        v-if="url"
        :id="VIDEO_ID"
        :key="url"
        :src="url"
        class="player"
        controls
        :playback-rate="playbackRate"
        :show-center-play-btn="false"
        :show-play-btn="true"
        :show-progress="true"
        :enable-progress-gesture="true"
        :show-fullscreen-btn="false"
        :vslide-gesture-in-fullscreen="true"
        :direction="90"
        object-fit="contain"
        @error="onError"
        @play="onPlay"
        @pause="onPause"
        @fullscreenchange="onFullscreenChange"
      />

      <view
        v-if="url && !isPlaying && !isLandscapeFullscreen"
        class="center-play cd-pressable"
        @click.stop="togglePlay"
      >
        <view class="center-play-inner">
          <u-icon name="play-right-fill" size="22" color="#fff" />
        </view>
      </view>

      <view v-if="url" class="extra-controls">
        <view class="ctrl-btn cd-pressable" @click.stop="showRatePicker">
          <text class="ctrl-btn-text">{{ formatRate(playbackRate) }}</text>
        </view>
        <view class="ctrl-btn ctrl-btn--icon cd-pressable" @click.stop="toggleLandscapeFullscreen">
          <svg v-if="!isLandscapeFullscreen" viewBox="0 0 24 24" class="fs-icon" aria-hidden="true">
            <path d="M8 3H5a2 2 0 0 0-2 2v3m18 0V5a2 2 0 0 0-2-2h-3m0 18h3a2 2 0 0 0 2-2v-3M3 16v3a2 2 0 0 0 2 2h3" />
          </svg>
          <u-icon v-else name="arrow-down" size="18" color="#fff" />
        </view>
      </view>

      <MobileRateSheet
        v-model:show="rateSheetVisible"
        v-model:value="playbackRate"
        :rates="[...PLAYBACK_RATES]"
      />

      <view v-if="loadError" class="error-tip">
        <text>{{ loadError }}</text>
      </view>
    </view>
  </view>
</template>

<style scoped lang="scss">
.page {
  min-height: 100vh;
  background: #000;
  display: flex;
  flex-direction: column;
}

.page.is-landscape-fs {
  position: fixed;
  inset: 0;
  z-index: 9999;
  min-height: 100vh;
  width: 100vw;
  height: 100vh;
  background: #000;
}

.top-bar {
  position: relative;
  z-index: 10;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: calc(var(--status-bar-height, 0px) + 20rpx) 24rpx 20rpx;
  background: linear-gradient(180deg, rgba(0, 0, 0, 0.72) 0%, transparent 100%);
}

.title {
  width: 100%;
  text-align: center;
  font-size: 28rpx;
  color: #fff;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.player-wrap {
  position: relative;
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 0;
  padding: 24rpx;
  box-sizing: border-box;
}

.page.is-landscape-fs .player-wrap {
  padding: 0;
  width: 100%;
  height: 100%;
}

.player {
  width: 100%;
  max-height: calc(100vh - 160rpx);
}

.page.is-landscape-fs .player {
  width: 100%;
  height: 100%;
  max-height: none;
}

.center-play {
  position: absolute;
  left: 50%;
  top: 50%;
  z-index: 15;
  transform: translate(-50%, -50%);
  pointer-events: auto;
}

.center-play-inner {
  width: 88rpx;
  height: 88rpx;
  border-radius: 50%;
  background: rgba(15, 23, 42, 0.52);
  backdrop-filter: blur(10px);
  border: 2rpx solid rgba(255, 255, 255, 0.22);
  box-shadow: 0 10rpx 28rpx rgba(0, 0, 0, 0.38);
  display: flex;
  align-items: center;
  justify-content: center;
  padding-left: 6rpx;
  transition: transform 0.15s ease, background 0.15s ease;
}

.center-play:active .center-play-inner {
  transform: scale(0.94);
  background: rgba(15, 23, 42, 0.68);
}

/* H5 隐藏浏览器默认大播放按钮 */
/* #ifdef H5 */
.player-wrap :deep(video::-webkit-media-controls-overlay-play-button),
.player-wrap :deep(video::-webkit-media-controls-start-playback-button) {
  display: none !important;
  -webkit-appearance: none;
}
/* #endif */

/* H5 竖屏设备上，横屏全屏时旋转铺满 */
/* #ifdef H5 */
@media (orientation: portrait) {
  .page.is-landscape-fs {
    width: 100vh;
    height: 100vw;
    top: 50%;
    left: 50%;
    transform: translate(-50%, -50%) rotate(90deg);
  }
}
/* #endif */

.extra-controls {
  position: absolute;
  right: 24rpx;
  bottom: 108rpx;
  z-index: 20;
  display: flex;
  align-items: center;
  gap: 16rpx;
  pointer-events: auto;
}

.page.is-landscape-fs .extra-controls {
  right: 32rpx;
  bottom: 32rpx;
}

.ctrl-btn {
  min-width: 72rpx;
  height: 56rpx;
  padding: 0 18rpx;
  border-radius: 28rpx;
  background: rgba(0, 0, 0, 0.58);
  backdrop-filter: blur(8px);
  display: flex;
  align-items: center;
  justify-content: center;
  border: 1rpx solid rgba(255, 255, 255, 0.12);
}

.ctrl-btn-text {
  color: #fff;
  font-size: 24rpx;
  font-weight: 600;
}

.ctrl-btn--icon {
  width: 56rpx;
  padding: 0;
}

.fs-icon {
  width: 36rpx;
  height: 36rpx;
  stroke: #fff;
  stroke-width: 2.2;
  stroke-linecap: round;
  stroke-linejoin: round;
  fill: none;
}

.error-tip {
  position: absolute;
  left: 24rpx;
  right: 24rpx;
  bottom: 48rpx;
  padding: 20rpx 24rpx;
  border-radius: 16rpx;
  background: rgba(239, 68, 68, 0.16);
  color: #fecaca;
  font-size: 24rpx;
  text-align: center;
}
</style>
