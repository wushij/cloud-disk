<script setup lang="ts">
import { computed } from 'vue'
import { useAuthStore } from '@/stores/auth'
import {
  memberAvatarCacheKey,
  memberAvatarVersion
} from '@/utils/entityAvatarCache'
import { mediaApiUrl } from '@/utils/mediaUrl'
import { useCachedEntityAvatar } from '@/composables/useCachedEntityAvatar'

interface MemberLike {
  userId: number
  username?: string
  nickname?: string
  avatar?: string | null
  hasAvatar?: boolean
}

const props = withDefaults(
  defineProps<{
    teamId: number
    member: MemberLike
    size?: number
    initial: string
  }>(),
  { size: 48 }
)

const emit = defineEmits<{ error: [userId: number] }>()

const auth = useAuthStore()

const liveSrc = computed(() => {
  if (props.member.username === auth.username && auth.avatarDisplaySrc) {
    return auth.avatarDisplaySrc
  }
  const hasAvatar = props.member.hasAvatar ?? !!props.member.avatar
  if (!hasAvatar || !props.teamId) return ''
  return mediaApiUrl(`/api/teams/${props.teamId}/members/${props.member.userId}/avatar`)
})

const cacheKey = computed(() =>
  props.member.username === auth.username
    ? ''
    : memberAvatarCacheKey(props.teamId, props.member.userId)
)

const version = computed(() => memberAvatarVersion(props.member))

const displaySrc = useCachedEntityAvatar(cacheKey, liveSrc, version)
</script>

<template>
  <el-avatar
    :size="size"
    :src="displaySrc || undefined"
    class="cd-member-avatar"
    @error="emit('error', member.userId)"
  >
    {{ initial }}
  </el-avatar>
</template>
