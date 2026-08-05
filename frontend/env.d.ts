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

// sm-crypto 没有官方 @types 包，手动声明
declare module 'sm-crypto' {
  export const sm3: (msg: string, options?: { key?: string }) => string
  export const sm4: {
    encrypt: (plaintext: string, key: string, options?: object) => string
    decrypt: (ciphertext: string | number[], key: string, options?: object) => string
  }
  export const sm2: {
    generateKeyPairHex: () => { privateKey: string; publicKey: string }
    doEncrypt: (msg: string, publicKey: string, cipherMode?: number) => string
    doDecrypt: (ciphertext: string, privateKey: string, cipherMode?: number) => string
  }
}
