import { reactive } from 'vue'
import { clearEntityAvatarThumb, teamAvatarCacheKey } from '@/utils/entityAvatarCache'

const versions = reactive<Record<number, number>>({})

export function bumpTeamAvatarVersion(teamId: number) {
  versions[teamId] = Date.now()
  clearEntityAvatarThumb(teamAvatarCacheKey(teamId))
}

export function getTeamAvatarVersion(teamId: number) {
  return versions[teamId] || 0
}
