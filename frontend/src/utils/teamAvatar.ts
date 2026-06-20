import { reactive } from 'vue'

const versions = reactive<Record<number, number>>({})

export function bumpTeamAvatarVersion(teamId: number) {
  versions[teamId] = Date.now()
}

export function getTeamAvatarVersion(teamId: number) {
  return versions[teamId] || 0
}
