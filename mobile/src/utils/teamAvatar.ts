import { ref } from 'vue'
import { clearEntityAvatarThumb, teamAvatarCacheKey } from '@/utils/entityAvatarCache'

export const teamAvatarVersions = ref<Record<number, number>>({})

export function bumpTeamAvatarVersion(teamId: number) {
  teamAvatarVersions.value = { ...teamAvatarVersions.value, [teamId]: Date.now() }
  clearEntityAvatarThumb(teamAvatarCacheKey(teamId))
}

export function getTeamAvatarVersion(teamId: number) {
  return teamAvatarVersions.value[teamId] || 0
}
