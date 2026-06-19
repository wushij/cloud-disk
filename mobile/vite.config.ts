import { defineConfig } from 'vite'
import uni from '@dcloudio/vite-plugin-uni'
// @ts-ignore
import { lanAccessBanner } from '../scripts/vite-lan-banner.mjs'

export default defineConfig({
  plugins: [uni(), lanAccessBanner('CloudDisk Mobile H5') as any],
  css: {
    preprocessorOptions: {
      scss: {
        additionalData: '@import "@/uni.scss";',
        quietDeps: true,
        silenceDeprecations: ['import', 'legacy-js-api']
      }
    }
  },
  server: {
    host: true,
    port: 5174,
    strictPort: true,
    proxy: {
      '/api': {
        target: 'http://127.0.0.1:8088',
        changeOrigin: true,
        timeout: 3_600_000,
        proxyTimeout: 3_600_000
      },
      '/share': {
        target: 'http://127.0.0.1:8088',
        changeOrigin: true
      },
      '/ws': {
        target: 'ws://127.0.0.1:8088',
        ws: true
      }
    }
  }
})
