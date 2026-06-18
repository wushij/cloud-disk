/// <reference types="vite/client" />

interface ImportMetaEnv {
  readonly VITE_API_BASE?: string
  /** 与旧项目共用域名且 /api 仍指旧后端时填 `/fileapi`，见 deploy/README 方案 B */
  readonly VITE_FILE_API_PREFIX?: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}