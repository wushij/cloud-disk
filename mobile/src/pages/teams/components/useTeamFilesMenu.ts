import { computed, type Ref } from 'vue'
import { isImageFile, isVideoFile } from '../../../utils/fileCover'
import { isTextFile } from '../../../utils/filePreview'
import type { FileItem } from '../../../stores/file'

export function useTeamFilesActionList(
  actionItem: Ref<FileItem | null>,
  teamAccess: Ref<{ role?: string; canWrite?: boolean; canShare?: boolean } | null>,
  myRole: Ref<string>
) {
  return computed(() => {
    const item = actionItem.value
    if (!item) return []

    const access = teamAccess.value
    const isOwnerOrAdmin = myRole.value === 'OWNER' || myRole.value === 'ADMIN'
    const canWrite = access?.canWrite ?? isOwnerOrAdmin
    const canDelete = isOwnerOrAdmin || item.canDelete

    const list: { key: string; label: string; icon: string; danger?: boolean }[] = []

    if (item.type === 'file') {
      list.push({ key: 'preview', label: '预览', icon: 'eye' })
      list.push({ key: 'download', label: '下载', icon: 'download' })
    }

    if (canWrite) {
      list.push({ key: 'rename', label: '重命名', icon: 'edit' })
      list.push({ key: 'move', label: '移动', icon: 'move' })
    }

    if (canDelete) {
      list.push({ key: 'delete', label: '删除', icon: 'trash', danger: true })
    }

    return list
  })
}
