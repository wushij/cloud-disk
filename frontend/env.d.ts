/// <reference types="vite/client" />

declare module '*.vue' {
  import type { DefineComponent } from 'vue'
  const component: DefineComponent<object, object, unknown>
  export default component
}

interface ImportMetaEnv {
  readonly VITE_API_BASE?: string
  readonly VITE_FILE_API_PREFIX?: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}
