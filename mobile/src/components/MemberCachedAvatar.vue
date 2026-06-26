<script setup lang="ts">
import { computed } from 'vue'
import { useAuthStore } from '@/stores/auth'
import {
  memberAvatarCacheKey,
  memberAvatarVersion
} from '@/utils/entityAvatarCache'
import { fileApiUrl } from '@/api/http'
import { useCachedEntityAvatar } from '@/composables/useCachedEntityAvatar'

interface MemberLike {
  userId: number
  username?: string
  nickname?: string
  avatar?: string | null
  hasAvatar?: boolean
}

const props = defineProps<{
  teamId: number
  member: MemberLike
}>()

const emit = defineEmits<{ error: [userId: number] }>()

const auth = useAuthStore()

const liveSrc = computed(() => {
  if (props.member.username === auth.username && auth.avatarDisplaySrc) {
    return auth.avatarDisplaySrc
  }
  const hasAvatar = props.member.hasAvatar ?? !!props.member.avatar
  if (!hasAvatar || !props.teamId) return ''
  return fileApiUrl(`/api/teams/${props.teamId}/members/${props.member.userId}/avatar`)
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
  <image
    v-if="displaySrc"
    :src="displaySrc"
    class="member-cached-avatar"
    mode="aspectFill"
    @error="emit('error', member.userId)"
  />
</template>

<style scoped>
.member-cached-avatar {
  width: 100%;
  height: 100%;
  border-radius: 50%;
}
</style>
