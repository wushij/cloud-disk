<script setup lang="ts">
withDefaults(
  defineProps<{
    /** 是否渲染为压缩包图标 */
    archive?: boolean
    size?: number | string
  }>(),
  { archive: false, size: 44 }
)
</script>

<template>
  <svg
    class="folder-type-icon"
    :width="size"
    :height="size"
    viewBox="0 0 64 64"
    fill="none"
    xmlns="http://www.w3.org/2000/svg"
    aria-hidden="true"
  >
    <defs>
      <!-- 后背板渐变 -->
      <linearGradient id="backFolderGrad" x1="32" y1="10" x2="32" y2="56" gradientUnits="userSpaceOnUse">
        <stop offset="0%" stop-color="#F57C00" />
        <stop offset="100%" stop-color="#E65100" />
      </linearGradient>
      
      <!-- 前盖板渐变 -->
      <linearGradient id="frontFolderGrad" x1="32" y1="19" x2="32" y2="56" gradientUnits="userSpaceOnUse">
        <stop offset="0%" stop-color="#FFE082" />
        <stop offset="30%" stop-color="#FFB300" />
        <stop offset="100%" stop-color="#FF8F00" />
      </linearGradient>

      <!-- 前盖板高光渐变 -->
      <linearGradient id="frontHighlight" x1="32" y1="19" x2="32" y2="24" gradientUnits="userSpaceOnUse">
        <stop offset="0%" stop-color="#FFF9C4" stop-opacity="0.85" />
        <stop offset="100%" stop-color="#FFF9C4" stop-opacity="0" />
      </linearGradient>
      
      <!-- 纸张渐变 -->
      <linearGradient id="paperGrad" x1="32" y1="13" x2="32" y2="39" gradientUnits="userSpaceOnUse">
        <stop offset="0%" stop-color="#FFFFFF" />
        <stop offset="100%" stop-color="#ECEFF1" />
      </linearGradient>

      <!-- 纸张淡蓝装饰条渐变 -->
      <linearGradient id="paperLineGrad" x1="22" y1="19" x2="38" y2="19" gradientUnits="userSpaceOnUse">
        <stop offset="0%" stop-color="#90CAF9" />
        <stop offset="100%" stop-color="#42A5F5" />
      </linearGradient>

      <!-- 阴影滤镜 -->
      <filter id="folderShadow" x="-15%" y="-15%" width="130%" height="130%">
        <feDropShadow dx="0" dy="1.5" stdDeviation="1.5" flood-color="#4E342E" flood-opacity="0.25" />
      </filter>

      <!-- 拉链金属轨道渐变 -->
      <linearGradient id="zipperTrackGrad" x1="32" y1="19" x2="32" y2="56" gradientUnits="userSpaceOnUse">
        <stop offset="0%" stop-color="#37474F" />
        <stop offset="100%" stop-color="#212121" />
      </linearGradient>
      
      <!-- 拉片金属渐变 -->
      <linearGradient id="pullerGrad" x1="32" y1="36" x2="32" y2="49" gradientUnits="userSpaceOnUse">
        <stop offset="0%" stop-color="#CFD8DC" />
        <stop offset="50%" stop-color="#90A4AE" />
        <stop offset="100%" stop-color="#455A64" />
      </linearGradient>
    </defs>

    <!-- 1. 底部软投影 -->
    <ellipse cx="32" cy="57" rx="23" ry="3.5" fill="#000000" fill-opacity="0.16" />

    <!-- 2. 后背板 -->
    <path
      d="M6 14C6 11.7909 7.79086 10 10 10H22.5858C23.6467 10 24.6641 10.4214 25.4142 11.1716L29.4142 15.1716C30.1643 15.9217 31.1817 16.3431 32.2426 16.3431H54C56.2091 16.3431 58 18.1323 58 20.3431V52C58 54.2091 56.2091 56 54 56H10C7.79086 56 6 54.2091 6 52V14Z"
      fill="url(#backFolderGrad)"
    />

    <!-- 3. 内部装设的信笺纸张（立体细节） -->
    <!-- 微微倾斜的衬纸 -->
    <rect x="22" y="12" width="22" height="25" rx="1.5" transform="rotate(2.5 22 12)" fill="#CFD8DC" opacity="0.8" />
    <!-- 正中的文件纸 -->
    <g>
      <rect x="18" y="13" width="26" height="26" rx="1.5" fill="url(#paperGrad)" />
      <rect x="22" y="18" width="18" height="1.8" rx="0.9" fill="url(#paperLineGrad)" />
      <rect x="22" y="24" width="13" height="1.8" rx="0.9" fill="#B0BEC5" />
      <rect x="22" y="30" width="8" height="1.8" rx="0.9" fill="#B0BEC5" />
    </g>

    <!-- 4. 前盖板（应用投影滤镜以突出前后立体落差） -->
    <path
      d="M6 23C6 20.7909 7.79086 19 10 19H54C56.2091 19 58 20.7909 58 23V52C58 54.2091 56.2091 56 54 56H10C7.79086 56 6 54.2091 6 52V23Z"
      fill="url(#frontFolderGrad)"
      filter="url(#folderShadow)"
    />

    <!-- 5. 前盖板上缘的高光层 -->
    <path
      d="M10 19H54C56.2091 19 58 20.7909 58 23V25.5H6V23C6 20.7909 7.79086 19 10 19Z"
      fill="url(#frontHighlight)"
    />

    <!-- 6. 压缩包模式下的金属咬合拉链轨道 -->
    <template v-if="archive">
      <!-- 垂直轨道基座 -->
      <rect x="30" y="19" width="4" height="37" fill="url(#zipperTrackGrad)" />
      <!-- 拉链排齿 -->
      <path
        d="M29 19V56 M35 19V56"
        stroke="#90A4AE"
        stroke-width="0.9"
        stroke-dasharray="1.8 1.8"
      />
      <!-- 咬合中线 -->
      <line x1="32" y1="19" x2="32" y2="56" stroke="#ECEFF1" stroke-width="1.2" stroke-dasharray="1.2 1.2" />

      <!-- 精细金属滑块与拉片 -->
      <!-- 拉锁底座 -->
      <rect x="29" y="30" width="6" height="7.5" rx="1" fill="#455A64" stroke="#212121" stroke-width="0.8" />
      <rect x="30.5" y="35" width="3" height="3" rx="0.5" fill="#212121" />
      <!-- 悬挂大拉片 -->
      <path
        d="M29.5 37.5C29.5 36.6716 30.1716 36 31 36H33C33.8284 36 34.5 36.6716 34.5 37.5V47C34.5 48.1046 33.6046 49 32.5 49H31.5C30.4005 49 29.5 48.1046 29.5 47V37.5Z"
        fill="url(#pullerGrad)"
        stroke="#263238"
        stroke-width="0.8"
      />
      <!-- 吊扣孔洞 -->
      <rect x="31" y="40.5" width="2" height="4.5" rx="0.6" fill="#212121" />
    </template>
  </svg>
</template>

<style scoped>
.folder-type-icon {
  display: block;
  flex-shrink: 0;
  margin: 0 auto;
}
</style>
