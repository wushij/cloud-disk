// 通用分页响应包装（对应后端 PageResultVO<T>）
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
  defaultPassword?: boolean
  createTime: string
  updateTime: string
}

// 18. UserRow 别名
export type UserRow = UserVO
