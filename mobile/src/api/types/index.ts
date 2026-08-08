// 移动端 18 个后端 VO 的 TypeScript 接口映射与领域模型定义

// 通用分页包装（对应 PageResultVO<T>）
export interface PageResult<T> {
  content: T[]
  totalElements: number
  page: number
  size: number
  teamAccess?: Record<string, unknown>
}

// 1. AuthTokenVO
export interface AuthTokenVO {
  token: string
  username: string
  nickname: string
  role: string
  userId: number
  defaultPassword: boolean
}

// 2. BreadcrumbVO
export interface BreadcrumbVO {
  id: number
  name: string
}

// 3. CaptchaRequiredVO
export interface CaptchaRequiredVO {
  required: boolean
}

// 4. CaptchaVO
export interface CaptchaVO {
  id: string
  img: string
}

// 5. DirectUrlVO
export interface DirectUrlVO {
  url: string
  expireSeconds: number
  storageType: string
  bucket: string
  proxy: boolean
}

// 6. FileVO
export interface FileVO {
  id: number
  userId: number
  folderId: number
  fileName: string
  fileSize: number
  fileType: string
  fileMd5: string
  thumbnailPath: string
  posterPath: string
  transcodePath: string
  transcodeStatus: string
  status: number
  createTime: string
  updateTime: string
  previewable?: boolean
  officeFile?: boolean
}

// 7. FolderTreeNodeVO
export interface FolderTreeNodeVO {
  id: number
  label: string
  parentId: number
  children?: FolderTreeNodeVO[]
}

// 8. FolderVO
export interface FolderVO {
  id: number
  userId: number
  parentId: number
  folderName: string
  status: number
  createTime: string
  updateTime: string
}

// 9. MessageVO
export interface MessageVO {
  message: string
}

// 10. OperationResultVO
export interface OperationResultVO {
  success: boolean
  message: string
}

// 11. QuotaApplicationVO
export interface QuotaApplicationVO {
  id: number
  userId: number
  username: string
  currentQuota: number
  applyQuota: number
  reason: string
  status: string
  approvalOpinion: string
  createTime: string
  updateTime: string
}

// 12. RegisterResultVO
export interface RegisterResultVO {
  pending: boolean
  title: string
  message: string
  token?: string
  username?: string
  nickname?: string
  role?: string
}

// 13. SecurityPublicConfigVO
export interface SecurityPublicConfigVO {
  timestampEnabled: boolean
  nonceEnabled: boolean
  sm3SignEnabled: boolean
  sm2SignEnabled: boolean
  sm4EncryptEnabled: boolean
}

// 14. ShareVO
export interface ShareVO {
  id: number
  shareCode: string
  shareType: string
  extractCode: string
  expireTime: string
  viewCount: number
  downloadCount: number
  status: number
  shareUrl: string
  createdAt: string
  folderId: number
  fileId: number
  fileName: string
}

// 15. StorageUsageVO
export interface StorageUsageVO {
  usedBytes: number
  quotaBytes: number
  usedFormatted: string
  quotaFormatted: string
  usedPercent: number
}

// 16. TeamSpaceVO
export interface TeamSpaceVO {
  id: number
  name: string
  ownerId: number
  rootFolderId: number
  maxSize: number
  status: number
  avatar: string
  role: string
  memberCount: number
  createTime: string
  updateTime: string
}

// 17. UserVO
export interface UserVO {
  id: number
  username: string
  nickname: string
  avatar: string
  email: string
  phone: string
  status: number
  role: string
  storageQuota: number
  storageUsed: number
  defaultPassword: boolean
  createTime: string
  updateTime: string
}
export type UserRow = UserVO

// ── 移动端领域模型（上提断开 utils -> stores 依赖环） ────────────────

export interface FileItem {
  id: number
  name: string
  type: 'file' | 'folder'
  sizeBytes?: number
  mimeType?: string | null
  previewable?: boolean
  hasThumbnail?: boolean
  transcodeStatus?: string
  createdAt?: string
  officeFile?: boolean
  ownerId?: number
  canDelete?: boolean
  canModify?: boolean
  canEdit?: boolean
}

export type TeamSpace = TeamSpaceVO

export type FileKind =
  | 'folder' | 'image' | 'video' | 'audio' | 'archive'
  | 'pdf' | 'doc' | 'sheet' | 'slide' | 'text' | 'code' | 'default'

export interface FileCoverContext {
  id?: number
  name?: string
  type?: 'file' | 'folder'
  mimeType?: string | null
  hasThumbnail?: boolean
  transcodeStatus?: string
  shareCode?: string
  extractCode?: string
}
