import { ref } from 'vue'

export const teamAvatarVersions = ref<Record<number, number>>({})

export function bumpTeamAvatarVersion(teamId: number) {
  teamAvatarVersions.value = { ...teamAvatarVersions.value, [teamId]: Date.now() }
}

export function getTeamAvatarVersion(teamId: number) {
  return teamAvatarVersions.value[teamId] || 0
}
