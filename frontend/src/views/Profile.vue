<script setup lang="ts">
import { ref, onMounted, computed, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { Camera, Check, User, Message, Iphone, Loading } from '@element-plus/icons-vue'
import { useAuthStore } from '@/stores/auth'
import http from '@/api/http'

const auth = useAuthStore()
const nickname = ref('')
const email = ref('')
const phone = ref('')
const loading = ref(false)
const avatarUploading = ref(false)
const avatarInputRef = ref<HTMLInputElement | null>(null)
const avatarLoadFailed = ref(false)

interface StorageUsage {
  usedBytes: number
  quotaBytes: number
  usedPercent: number
  usedFormatted: string
  quotaFormatted: string
}
const usage = ref<StorageUsage | null>(null)

const storagePercent = computed(() => {
  if (!usage.value) return 0
  if (usage.value.quotaBytes === 0) return 0
  return Math.min(100, usage.value.usedPercent)
})

const storageColor = computed(() => {
  const p = storagePercent.value
  if (p >= 90) return '#ef4444'
  if (p >= 70) return '#f59e0b'
  return 'var(--theme-primary)'
})

onMounted(async () => {
  await auth.ensureMediaToken()
  const data = await auth.fetchProfile()
  nickname.value = data.nickname || ''
  email.value = data.email || ''
  phone.value = data.phone || ''
  try {
    const { data: u } = await http.get('/api/storage/usage')
    usage.value = u
  } catch {
    /* ignore */
  }
})

async function onAvatarSelected(e: Event) {
  const input = e.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file) return

  if (!file.type.startsWith('image/')) {
    ElMessage.warning('请选择图片文件')
    return
  }
  if (file.size > 5 * 1024 * 1024) {
    ElMessage.warning('图片大小不能超过 5MB')
    return
  }

  avatarUploading.value = true
  avatarLoadFailed.value = false
  try {
    await auth.uploadAvatar(file)
    ElMessage.success('头像已更新')
  } catch {
    /* global toast */
  } finally {
    avatarUploading.value = false
  }
}

function openAvatarPicker() {
  if (!avatarUploading.value) avatarInputRef.value?.click()
}

function onAvatarError() {
  avatarLoadFailed.value = true
}

watch(() => auth.avatarVersion, () => {
  avatarLoadFailed.value = false
})

async function save() {
  loading.value = true
  try {
    await auth.updateProfile({
      nickname: nickname.value.trim(),
      email: email.value.trim(),
      phone: phone.value.trim()
    })
    ElMessage.success('保存成功')
  } catch {
    /* global toast */
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="profile-page cd-page cd-page-scroll">
    <div class="profile-shell">
      <!-- 顶部身份区 -->
      <section class="profile-hero">
        <div class="profile-hero-banner" />

        <div class="profile-hero-main">
          <button
            type="button"
            class="profile-avatar-zone"
            :class="{ uploading: avatarUploading }"
            :disabled="avatarUploading"
            title="点击更换头像"
            @click="openAvatarPicker"
          >
            <div
              v-if="auth.hasAvatar && !auth.avatarDisplaySrc && !avatarLoadFailed"
              class="profile-avatar profile-avatar-skeleton"
              aria-hidden="true"
            />
            <el-avatar
              v-else
              :key="auth.avatarSrc"
              :size="96"
              :src="auth.avatarDisplaySrc && !avatarLoadFailed ? auth.avatarDisplaySrc : undefined"
              class="profile-avatar"
              @error="onAvatarError"
            >
              {{ auth.avatarInitial }}
            </el-avatar>
            <span class="profile-avatar-mask">
              <el-icon v-if="!avatarUploading" :size="22"><Camera /></el-icon>
              <el-icon v-else :size="22" class="is-loading"><Loading /></el-icon>
              <span>{{ avatarUploading ? '上传中' : '更换头像' }}</span>
            </span>
          </button>
          <input
            ref="avatarInputRef"
            type="file"
            accept="image/jpeg,image/png,image/gif,image/webp"
            class="profile-avatar-input"
            @change="onAvatarSelected"
          />

          <div class="profile-hero-info">
            <div class="profile-name-row">
              <h2 class="profile-display-name">{{ auth.nickname || auth.username }}</h2>
              <span class="profile-role-badge" :class="{ admin: auth.isAdmin, super: auth.isSuperAdmin }">
                {{ auth.isSuperAdmin ? '超级管理员' : auth.isAdmin ? '管理员' : '普通用户' }}
              </span>
            </div>
            <p class="profile-username">@{{ auth.username }}</p>
            <p class="profile-avatar-hint">支持 JPG / PNG / GIF / WebP，最大 5MB</p>
          </div>

          <div v-if="usage" class="profile-storage-panel">
            <div class="profile-storage-head">
              <span class="profile-storage-title">存储空间</span>
              <span v-if="usage.quotaBytes > 0" class="profile-storage-percent">{{ storagePercent }}%</span>
            </div>
            <el-progress
              v-if="usage.quotaBytes > 0"
              :percentage="storagePercent"
              :stroke-width="6"
              :color="storageColor"
              :show-text="false"
              class="profile-progress"
            />
            <div class="profile-storage-meta">
              <span><strong>{{ usage.usedFormatted }}</strong> 已用</span>
              <span>
                <strong>{{ usage.quotaBytes > 0 ? usage.quotaFormatted : '无限制' }}</strong>
                总量
              </span>
            </div>
          </div>
        </div>
      </section>

      <!-- 基本信息 -->
      <section class="profile-section">
        <div class="profile-section-head">
          <div class="profile-section-title">
            <el-icon><User /></el-icon>
            <span>基本信息</span>
          </div>
          <p class="profile-section-desc">更新您的昵称与联系方式</p>
        </div>

        <el-form label-position="top" class="profile-form" @submit.prevent="save">
          <div class="profile-form-grid">
            <el-form-item label="用户名">
              <el-input :model-value="auth.username || ''" disabled :prefix-icon="User" />
            </el-form-item>
            <el-form-item label="昵称">
              <el-input v-model="nickname" placeholder="请输入昵称" :prefix-icon="User" />
            </el-form-item>
            <el-form-item label="邮箱">
              <el-input v-model="email" placeholder="请输入邮箱" :prefix-icon="Message" />
            </el-form-item>
            <el-form-item label="手机号">
              <el-input v-model="phone" placeholder="请输入手机号" :prefix-icon="Iphone" />
            </el-form-item>
          </div>

          <div class="profile-form-actions">
            <el-button type="primary" size="large" :loading="loading" native-type="submit">
              <el-icon><Check /></el-icon>
              保存修改
            </el-button>
          </div>
        </el-form>
      </section>
    </div>
  </div>
</template>

<style scoped>
.profile-page {
  width: 100%;
  box-sizing: border-box;
  animation: fadeIn 0.35s ease;
}

.profile-shell {
  width: 100%;
  background: #fff;
  border: 1px solid var(--theme-border, var(--cd-border-light));
  border-radius: var(--cd-radius-xl);
  box-shadow: var(--cd-shadow-sm);
  overflow: hidden;
}

/* ---- Hero ---- */
.profile-hero {
  position: relative;
  border-bottom: 1px solid var(--theme-border, var(--cd-border-light));
}

.profile-hero-banner {
  height: 120px;
  background:
    radial-gradient(ellipse at 20% 0%, var(--theme-primary-muted-strong) 0%, transparent 55%),
    linear-gradient(
      135deg,
      color-mix(in srgb, var(--theme-primary) 14%, var(--theme-bg)) 0%,
      color-mix(in srgb, var(--theme-primary) 8%, var(--theme-bg)) 45%,
      var(--theme-bg) 100%
    );
  position: relative;
  overflow: hidden;
}

.profile-hero-banner::after {
  content: '';
  position: absolute;
  right: -40px;
  top: -40px;
  width: 180px;
  height: 180px;
  border-radius: 50%;
  background: radial-gradient(circle, var(--theme-primary-muted-strong) 0%, transparent 70%);
  opacity: 0.6;
}

.profile-hero-main {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-end;
  gap: 20px 28px;
  padding: 0 28px 24px;
  margin-top: -48px;
}

.profile-avatar-zone {
  position: relative;
  flex-shrink: 0;
  padding: 0;
  border: none;
  background: none;
  cursor: pointer;
  border-radius: 50%;
}

.profile-avatar-zone:disabled {
  cursor: wait;
}

.profile-avatar {
  font-size: 36px !important;
  font-weight: 700 !important;
  background: var(--cd-primary-gradient) !important;
  color: #fff !important;
  border: 4px solid #fff !important;
  box-shadow: 0 8px 28px var(--theme-primary-muted-strong), 0 0 0 1px var(--theme-primary-muted) !important;
  transition: transform 0.2s ease;
}

.profile-avatar-skeleton {
  width: 96px;
  height: 96px;
  border-radius: 50%;
  border: 4px solid #fff;
  box-shadow: 0 8px 28px var(--theme-primary-muted-strong), 0 0 0 1px var(--theme-primary-muted);
  background: linear-gradient(90deg, #e2e8f0 25%, #f1f5f9 50%, #e2e8f0 75%);
  background-size: 200% 100%;
  animation: profile-avatar-shimmer 1.2s ease-in-out infinite;
}

@keyframes profile-avatar-shimmer {
  0% { background-position: 200% 0; }
  100% { background-position: -200% 0; }
}

.profile-avatar-mask {
  position: absolute;
  inset: 4px;
  border-radius: 50%;
  background: rgba(15, 23, 42, 0.55);
  color: #fff;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 4px;
  font-size: 12px;
  font-weight: 500;
  opacity: 0;
  transition: opacity 0.2s ease;
}

.profile-avatar-zone:hover .profile-avatar-mask,
.profile-avatar-zone.uploading .profile-avatar-mask {
  opacity: 1;
}

.profile-avatar-zone:hover .profile-avatar {
  transform: scale(1.02);
}

.profile-avatar-input {
  display: none;
}

.profile-hero-info {
  flex: 1;
  min-width: 200px;
  padding-top: 52px;
}

.profile-name-row {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
  margin-bottom: 4px;
}

.profile-display-name {
  margin: 0;
  font-size: 24px;
  font-weight: 700;
  color: var(--cd-text-primary);
  letter-spacing: -0.02em;
}

.profile-username {
  margin: 0 0 6px;
  font-size: 14px;
  color: var(--cd-text-secondary);
}

.profile-avatar-hint {
  margin: 0;
  font-size: 12px;
  color: var(--cd-text-placeholder);
}

.profile-role-badge {
  display: inline-block;
  padding: 3px 12px;
  border-radius: var(--cd-radius-full);
  font-size: 12px;
  font-weight: 600;
  color: #475569;
  background: #f1f5f9;
  border: 1px solid transparent;
}

.profile-role-badge.admin {
  color: #b45309;
  background: #fffbeb;
  border-color: #fde68a;
}

.profile-storage-panel {
  flex: 0 1 280px;
  min-width: 240px;
  margin-left: auto;
  padding: 14px 16px;
  border-radius: var(--cd-radius-lg);
  background: var(--theme-bg, #f8fafc);
  border: 1px solid var(--theme-border, var(--cd-border-light));
}

.profile-storage-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
}

.profile-storage-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--cd-text-primary);
}

.profile-storage-percent {
  font-size: 13px;
  font-weight: 700;
  color: var(--cd-primary);
}

.profile-storage-meta {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  margin-top: 10px;
  font-size: 12px;
  color: var(--cd-text-secondary);
}

.profile-storage-meta strong {
  color: var(--cd-text-primary);
  font-weight: 600;
}

.profile-progress :deep(.el-progress-bar__outer) {
  background: color-mix(in srgb, var(--theme-primary) 10%, #fff);
}

/* ---- Form section ---- */
.profile-section {
  padding: 28px;
}

.profile-section-head {
  margin-bottom: 22px;
}

.profile-section-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 16px;
  font-weight: 600;
  color: var(--cd-text-primary);
}

.profile-section-title .el-icon {
  color: var(--cd-primary);
}

.profile-section-desc {
  margin: 6px 0 0;
  font-size: 13px;
  color: var(--cd-text-secondary);
}

.profile-form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 4px 24px;
}

.profile-form :deep(.el-form-item__label) {
  font-weight: 500;
  color: var(--cd-text-regular);
  padding-bottom: 6px;
}

.profile-form :deep(.el-input__wrapper) {
  height: 42px;
  border-radius: 10px !important;
}

.profile-form-actions {
  margin-top: 12px;
  padding-top: 22px;
  border-top: 1px solid var(--cd-border-light);
}

.profile-form-actions .el-button {
  min-width: 148px;
  height: 42px !important;
  border-radius: 10px !important;
}

@media (max-width: 900px) {
  .profile-hero-main {
    flex-direction: column;
    align-items: stretch;
    padding: 0 20px 20px;
  }

  .profile-hero-info {
    padding-top: 12px;
  }

  .profile-storage-panel {
    margin-left: 0;
    flex: 1 1 auto;
  }

  .profile-section {
    padding: 20px;
  }
}

@media (max-width: 640px) {
  .profile-form-grid {
    grid-template-columns: 1fr;
  }
}
</style>
