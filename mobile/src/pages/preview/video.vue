<script setup lang="ts">
import { nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
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
const pipSupported = ref(false)
const currentTime = ref(0)
const duration = ref(0)
const isH5 = import.meta.env.UNI_PLATFORM === 'h5'

onLoad((query) => {
  url.value = decodeURIComponent((query?.url as string) || '')
  name.value = decodeURIComponent((query?.name as string) || '视频播放')
})

onMounted(() => {
  // #ifdef H5
  pipSupported.value =
    typeof document !== 'undefined' &&
    'pictureInPictureEnabled' in document &&
    !!(document as Document & { pictureInPictureEnabled?: boolean }).pictureInPictureEnabled
  void nextTick(() => {
    syncH5VideoControls()
    const video = getH5VideoEl()
    if (!video) return
    video.addEventListener('timeupdate', () => {
      currentTime.value = video.currentTime
      if (video.duration > 0) duration.value = video.duration
    })

    const handleH5FullscreenChange = () => {
      const isFs = !!(
        document.fullscreenElement ||
        (document as any).webkitFullscreenElement ||
        (video as any).webkitDisplayingFullscreen
      )
      isLandscapeFullscreen.value = isFs
      if (isFs) {
        lockLandscape()
      } else {
        unlockLandscape()
      }
    }
    document.addEventListener('fullscreenchange', handleH5FullscreenChange)
    document.addEventListener('webkitfullscreenchange', handleH5FullscreenChange)
    video.addEventListener('webkitbeginfullscreen', () => {
      isLandscapeFullscreen.value = true
      lockLandscape()
    })
    video.addEventListener('webkitendfullscreen', () => {
      isLandscapeFullscreen.value = false
      unlockLandscape()
    })
  })
  // #endif
})

watch(playbackRate, () => {
  // #ifdef H5
  const video = getH5VideoEl()
  if (video) video.playbackRate = playbackRate.value
  // #endif
  // #ifndef H5
  getVideoContext().playbackRate(playbackRate.value)
  // #endif
})

watch(isLandscapeFullscreen, () => {
  if (isH5) void nextTick(syncH5VideoControls)
})

function formatRate(rate: number) {
  return Number.isInteger(rate) ? `${rate}x` : `${rate}x`
}

function formatTime(sec: number) {
  if (!Number.isFinite(sec) || sec < 0) return '0:00'
  const total = Math.floor(sec)
  const m = Math.floor(total / 60)
  const s = total % 60
  return `${m}:${String(s).padStart(2, '0')}`
}

function onTimeUpdate(e: unknown) {
  const detail = (e as { detail?: { currentTime?: number; duration?: number } }).detail
  if (detail?.currentTime != null) currentTime.value = detail.currentTime
  if (detail?.duration != null && detail.duration > 0) duration.value = detail.duration
}

function onLoadedMeta(e: unknown) {
  syncH5VideoControls()
  const detail = (e as { detail?: { duration?: number } }).detail
  if (detail?.duration != null && detail.duration > 0) duration.value = detail.duration
  // #ifdef H5
  const video = getH5VideoEl()
  if (video && video.duration > 0) duration.value = video.duration
  // #endif
}

function lockLandscape() {
  // #ifdef H5
  try {
    const orientation = screen.orientation as ScreenOrientation & { lock?: (o: string) => Promise<void> }
    if (orientation?.lock) {
      void orientation.lock('landscape').catch(() => {})
    }
  } catch {
    /* ignore */
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

function getH5VideoEl(): HTMLVideoElement | null {
  // #ifdef H5
  const root = document.getElementById(VIDEO_ID)
  if (!root) return null
  if (root.tagName === 'VIDEO') return root as HTMLVideoElement
  return root.querySelector('video')
  // #endif
  // #ifndef H5
  return null
  // #endif
}

function onFullscreenChange(e: unknown) {
  if (isH5) return
  const detail = (e as { detail?: { fullScreen?: boolean } }).detail
  if (!detail) return
  isLandscapeFullscreen.value = !!detail.fullScreen
  if (detail.fullScreen) {
    lockLandscape()
  } else {
    unlockLandscape()
  }
}

function syncH5VideoControls() {
  // #ifdef H5
  const video = getH5VideoEl()
  if (!video) return
  video.controls = isLandscapeFullscreen.value
  if (!isLandscapeFullscreen.value) {
    video.removeAttribute('controls')
    video.setAttribute('controlsList', 'nodownload nofullscreen noremoteplayback')
  } else {
    video.setAttribute('controls', 'true')
    video.removeAttribute('controlsList')
  }
  video.removeAttribute('disablePictureInPicture')
  if (Math.abs(video.playbackRate - playbackRate.value) > 0.01) {
    video.playbackRate = playbackRate.value
  }
  if (video.duration > 0) duration.value = video.duration
  // #endif
}

function enterLandscapeFullscreen() {
  isLandscapeFullscreen.value = true
  lockLandscape()
  if (!isH5) {
    getVideoContext().requestFullScreen({ direction: 90 })
    return
  } else {
    const video = getH5VideoEl()
    if (video) {
      if (video.requestFullscreen) {
        void video.requestFullscreen().catch(() => {})
      } else if ((video as any).webkitRequestFullscreen) {
        void (video as any).webkitRequestFullscreen().catch(() => {})
      } else if ((video as any).webkitEnterFullscreen) {
        void (video as any).webkitEnterFullscreen()
      }
    }
  }
  void nextTick(syncH5VideoControls)
}

function exitLandscapeFullscreen() {
  isLandscapeFullscreen.value = false
  fsRateMenuVisible.value = false
  unlockLandscape()
  if (!isH5) {
    getVideoContext().exitFullScreen()
    return
  } else {
    if (document.exitFullscreen) {
      void document.exitFullscreen().catch(() => {})
    } else if ((document as any).webkitExitFullscreen) {
      void (document as any).webkitExitFullscreen().catch(() => {})
    }
  }
  void nextTick(syncH5VideoControls)
}

const fsRateMenuVisible = ref(false)

function openFsRateMenu() {
  fsRateMenuVisible.value = true
}

function closeFsRateMenu() {
  fsRateMenuVisible.value = false
}

function selectFsRate(rate: number) {
  playbackRate.value = rate
  closeFsRateMenu()
}

function toggleLandscapeFullscreen() {
  if (isLandscapeFullscreen.value) {
    exitLandscapeFullscreen()
  } else {
    enterLandscapeFullscreen()
  }
}

async function togglePictureInPicture() {
  // #ifdef H5
  const video = getH5VideoEl()
  if (!video) return
  try {
    if (document.pictureInPictureElement) {
      await document.exitPictureInPicture()
      return
    }
    if (typeof video.requestPictureInPicture === 'function') {
      await video.requestPictureInPicture()
      return
    }
    uni.showToast({ title: '当前浏览器不支持画中画', icon: 'none' })
  } catch {
    uni.showToast({ title: '画中画开启失败', icon: 'none' })
  }
  // #endif
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

function cleanupPlayback() {
  isLandscapeFullscreen.value = false
  fsRateMenuVisible.value = false
  unlockLandscape()
  if (isH5) {
    // #ifdef H5
    if (document.pictureInPictureElement) {
      void document.exitPictureInPicture().catch(() => {})
    }
    // #endif
    return
  }
  getVideoContext().exitFullScreen()
}

onUnload(cleanupPlayback)
onUnmounted(cleanupPlayback)
</script>

<template>
  <view class="video-root">
    <view class="page" :class="{ 'is-landscape-fs': isLandscapeFullscreen }">
      <view v-if="!isLandscapeFullscreen" class="top-bar">
        <text class="title">{{ name }}</text>
      </view>

      <view v-else-if="!isH5 && !isLandscapeFullscreen" class="fs-top-bar">
        <view class="fs-back cd-pressable" @click.stop="exitLandscapeFullscreen">
          <u-icon name="arrow-down" size="18" color="#fff" />
        </view>
        <text class="fs-title">{{ name }}</text>
      </view>

      <view class="player-wrap">
        <video
          v-if="url"
          :id="VIDEO_ID"
          :key="url"
          :src="url"
          class="player"
          :class="{ 'player--fs': isLandscapeFullscreen, 'player--with-h5-bar': isH5 && !isLandscapeFullscreen }"
          :controls="isLandscapeFullscreen || !isH5"
          playsinline
          webkit-playsinline
          x5-playsinline
          :playback-rate="playbackRate"
          :show-center-play-btn="false"
          :show-play-btn="!isH5"
          :show-progress="!isH5"
          :enable-progress-gesture="!isH5"
          :show-fullscreen-btn="false"
          :vslide-gesture-in-fullscreen="!isH5"
          :direction="90"
          object-fit="contain"
          @error="onError"
          @play="onPlay"
          @pause="onPause"
          @timeupdate="onTimeUpdate"
          @fullscreenchange="onFullscreenChange"
          @loadedmetadata="onLoadedMeta"
        >
          <!-- #ifndef H5 -->
          <view v-if="isLandscapeFullscreen" class="fs-video-overlay" @click.stop>
            <view class="fs-top-bar fs-top-bar--native">
              <view class="fs-back cd-pressable" @click.stop="exitLandscapeFullscreen">
                <svg viewBox="0 0 24 24" class="fs-icon" aria-hidden="true">
                  <path d="M19 10h-5V5m0 5l5-5M5 14h5v5m0-5l-5 5" />
                </svg>
              </view>
              <text class="fs-title">{{ name }}</text>
              <view class="fs-top-actions">
                <view class="ctrl-btn cd-pressable" @click.stop="openFsRateMenu">
                  <text class="ctrl-btn-text">{{ formatRate(playbackRate) }}</text>
                </view>
              </view>
            </view>

            <view v-if="fsRateMenuVisible" class="fs-rate-menu" @click.stop="closeFsRateMenu">
              <view class="fs-rate-menu-panel" @click.stop>
                <view
                  v-for="rate in PLAYBACK_RATES"
                  :key="rate"
                  class="fs-rate-item"
                  :class="{ active: playbackRate === rate }"
                  @click="selectFsRate(rate)"
                >
                  <text class="fs-rate-text">{{ formatRate(rate) }}</text>
                </view>
              </view>
            </view>
          </view>
          <!-- #endif -->
        </video>

        <view
          v-if="url && isH5 && !isPlaying && !isLandscapeFullscreen"
          class="center-play cd-pressable"
          @click.stop="togglePlay"
        >
          <view class="center-play-inner">
            <u-icon name="play-right-fill" size="22" color="#fff" />
          </view>
        </view>

        <view v-if="url && isH5 && !isLandscapeFullscreen" class="h5-bar">
          <view class="h5-bar-row">
            <view class="bar-play cd-pressable" @click.stop="togglePlay">
              <u-icon :name="isPlaying ? 'pause' : 'play-right-fill'" size="20" color="#fff" />
            </view>
            <text class="bar-time">{{ formatTime(currentTime) }} / {{ formatTime(duration) }}</text>
            <view class="h5-bar-actions">
              <view class="ctrl-btn cd-pressable" @click.stop="showRatePicker">
                <text class="ctrl-btn-text">{{ formatRate(playbackRate) }}</text>
              </view>
              <view
                v-if="pipSupported"
                class="ctrl-btn ctrl-btn--icon cd-pressable"
                @click.stop="togglePictureInPicture"
              >
                <text class="ctrl-btn-icon-text">PiP</text>
              </view>
              <view class="ctrl-btn ctrl-btn--icon cd-pressable" @click.stop="enterLandscapeFullscreen">
                <svg viewBox="0 0 24 24" class="fs-icon" aria-hidden="true">
                  <path d="M8 3H5a2 2 0 0 0-2 2v3m18 0V5a2 2 0 0 0-2-2h-3m0 18h3a2 2 0 0 0 2-2v-3M3 16v3a2 2 0 0 0 2 2h3" />
                </svg>
              </view>
            </view>
          </view>
        </view>

        <!-- 小程序：原生 controls + 倍速/全屏 -->
        <view v-if="url && !isH5 && !isLandscapeFullscreen" class="extra-controls">
          <view class="ctrl-btn cd-pressable" @click.stop="showRatePicker">
            <text class="ctrl-btn-text">{{ formatRate(playbackRate) }}</text>
          </view>
          <view
            v-if="pipSupported"
            class="ctrl-btn ctrl-btn--icon cd-pressable"
            @click.stop="togglePictureInPicture"
          >
            <text class="ctrl-btn-icon-text">PiP</text>
          </view>
          <view class="ctrl-btn ctrl-btn--icon cd-pressable" @click.stop="toggleLandscapeFullscreen">
            <svg viewBox="0 0 24 24" class="fs-icon" aria-hidden="true">
              <path d="M8 3H5a2 2 0 0 0-2 2v3m18 0V5a2 2 0 0 0-2-2h-3m0 18h3a2 2 0 0 0 2-2v-3M3 16v3a2 2 0 0 0 2 2h3" />
            </svg>
          </view>
        </view>

        <view v-if="loadError" class="error-tip">
          <text>{{ loadError }}</text>
        </view>
      </view>
    </view>

    <MobileRateSheet
      v-model:show="rateSheetVisible"
      v-model:value="playbackRate"
      :rates="[...PLAYBACK_RATES]"
    />
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
  min-height: 100dvh;
  width: 100vw;
  width: 100dvw;
  height: 100vh;
  height: 100dvh;
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
  flex: 1;
}

.fs-top-bar {
  position: relative;
  z-index: 30;
  display: flex;
  align-items: center;
  gap: 16rpx;
  padding: calc(env(safe-area-inset-top, 0px) + 16rpx) 24rpx 16rpx;
  background: linear-gradient(180deg, rgba(0, 0, 0, 0.72) 0%, transparent 100%);
  flex-shrink: 0;
}

.fs-back {
  width: 56rpx;
  height: 56rpx;
  border-radius: 50%;
  background: rgba(0, 0, 0, 0.45);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.fs-title {
  flex: 1;
  min-width: 0;
  font-size: 26rpx;
  color: #fff;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.player {
  width: 100%;
  max-height: calc(100vh - 160rpx);
}

.page.is-landscape-fs .player,
.player--with-h5-bar {
  max-height: calc(100vh - 220rpx);
}

.page.is-landscape-fs .player {
  width: 100%;
  height: 100%;
  max-height: none;
  object-fit: contain;
}

.center-play {
  position: absolute;
  left: 50%;
  top: 50%;
  z-index: 25;
  transform: translate(-50%, -50%);
  pointer-events: auto;
}

.page.is-landscape-fs .center-play {
  z-index: 25;
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
}

/* H5 彻底隐藏浏览器原生控件，避免与自定义条重复 */
/* #ifdef H5 */
.player-wrap :deep(video::-webkit-media-controls),
.player-wrap :deep(video::-webkit-media-controls-enclosure),
.player-wrap :deep(video::-webkit-media-controls-panel),
.player-wrap :deep(video::-webkit-media-controls-overlay-play-button),
.player-wrap :deep(video::-webkit-media-controls-start-playback-button),
.player-wrap :deep(video::-webkit-media-controls-fullscreen-button),
.player-wrap :deep(video::-webkit-media-controls-play-button),
.player-wrap :deep(video::-webkit-media-controls-timeline),
.player-wrap :deep(video::-webkit-media-controls-current-time-display),
.player-wrap :deep(video::-webkit-media-controls-time-remaining-display),
.player-wrap :deep(video::-webkit-media-controls-mute-button),
.player-wrap :deep(video::-webkit-media-controls-volume-slider) {
  display: none !important;
  opacity: 0 !important;
  pointer-events: none !important;
  -webkit-appearance: none;
}
/* #endif */

/* H5 竖屏：整页旋转横屏铺满 */
/* #ifdef H5 */
@media (orientation: portrait) {
  .page.is-landscape-fs {
    width: 100dvh;
    height: 100dvw;
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

.h5-bar {
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  z-index: 30;
  padding: 12rpx calc(env(safe-area-inset-right, 0px) + 20rpx)
    calc(env(safe-area-inset-bottom, 0px) + 12rpx)
    calc(env(safe-area-inset-left, 0px) + 20rpx);
  background: linear-gradient(180deg, transparent 0%, rgba(0, 0, 0, 0.72) 40%, rgba(0, 0, 0, 0.92) 100%);
  pointer-events: auto;
}

.page.is-landscape-fs .h5-bar {
  padding-bottom: calc(env(safe-area-inset-bottom, 0px) + 16rpx);
}

/* H5 全屏 UI：不使用 fixed 贴物理屏幕，而是使用 absolute 定位在 page 容器内，参与旋转 */
/* #ifdef H5 */
.fs-ui {
  position: absolute;
  inset: 0;
  z-index: 10050;
  pointer-events: none;
}

.fs-ui-top,
.fs-ui-center,
.fs-ui-bar,
.fs-ui-bar .ctrl-btn,
.fs-ui-bar .bar-play,
.fs-ui-top .fs-back {
  pointer-events: auto;
}

.fs-ui-top {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  display: flex;
  align-items: center;
  gap: 12px;
  padding: calc(env(safe-area-inset-top, 0px) + 12px) 16px 12px;
  background: linear-gradient(180deg, rgba(0, 0, 0, 0.75) 0%, transparent 100%);
}

.fs-ui-title {
  flex: 1;
  min-width: 0;
  font-size: 14px;
  color: #fff;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.fs-ui-center {
  position: absolute;
  left: 50%;
  top: 50%;
  transform: translate(-50%, -50%);
  z-index: 2;
}

.fs-ui-bar {
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  padding: 12px calc(env(safe-area-inset-right, 0px) + 16px)
    calc(env(safe-area-inset-bottom, 0px) + 12px)
    calc(env(safe-area-inset-left, 0px) + 16px);
  background: linear-gradient(180deg, transparent 0%, rgba(0, 0, 0, 0.55) 35%, rgba(0, 0, 0, 0.9) 100%);
}

.fs-ui-bar-row {
  display: flex;
  align-items: center;
  gap: 10px;
}

.fs-ui-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-left: auto;
  flex-shrink: 0;
}

.fs-ui .bar-time {
  font-size: 13px;
}

.fs-ui .bar-play {
  width: 40px;
  height: 40px;
}

.fs-ui .ctrl-btn {
  min-width: 44px;
  height: 36px;
  padding: 0 12px;
  border-radius: 18px;
}

.fs-ui .ctrl-btn--icon {
  width: 36px;
  padding: 0;
}

.fs-ui .ctrl-btn-text {
  font-size: 13px;
}

.fs-ui .ctrl-btn-icon-text {
  font-size: 11px;
}
/* #endif */

.h5-bar-row {
  display: flex;
  align-items: center;
  gap: 12rpx;
}

.h5-bar-actions {
  display: flex;
  align-items: center;
  gap: 10rpx;
  margin-left: auto;
  flex-shrink: 0;
}

.bar-time {
  color: rgba(255, 255, 255, 0.88);
  font-size: 22rpx;
  font-variant-numeric: tabular-nums;
  flex-shrink: 0;
}

.bar-play {
  width: 56rpx;
  height: 56rpx;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.14);
  border: 1rpx solid rgba(255, 255, 255, 0.18);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
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

.ctrl-btn-icon-text {
  color: #fff;
  font-size: 20rpx;
  font-weight: 700;
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

/* Fullscreen Overlay and Speed Selector styles */
.fs-video-overlay {
  position: absolute;
  inset: 0;
  z-index: 999;
  pointer-events: none;
}

.fs-top-bar--native {
  position: absolute !important;
  top: 0;
  left: 0;
  right: 0;
  pointer-events: auto;
}

.fs-top-actions {
  display: flex;
  align-items: center;
  gap: 16rpx;
  flex-shrink: 0;
  pointer-events: auto;
}

.fs-rate-menu {
  position: absolute;
  inset: 0;
  background: rgba(0, 0, 0, 0.5);
  z-index: 1000;
  display: flex;
  justify-content: flex-end;
  pointer-events: auto;
}

.fs-rate-menu-panel {
  width: 320rpx;
  height: 100%;
  background: rgba(15, 23, 42, 0.9);
  backdrop-filter: blur(20px);
  border-left: 1rpx solid rgba(255, 255, 255, 0.12);
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  gap: 32rpx;
  padding: 40rpx 0;
  box-sizing: border-box;
}

.fs-rate-item {
  width: 80%;
  height: 72rpx;
  border-radius: 36rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.2s ease;
}

.fs-rate-item.active {
  background: rgba(255, 255, 255, 0.15);
}

.fs-rate-text {
  color: #fff;
  font-size: 28rpx;
  font-weight: 600;
}

.fs-rate-item.active .fs-rate-text {
  color: var(--cd-primary, #3b82f6);
}
</style>
