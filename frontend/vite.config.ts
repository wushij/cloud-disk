import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'
import { fileURLToPath, URL } from 'node:url'
// @ts-ignore
import { lanAccessBanner } from '../scripts/vite-lan-banner.mjs'

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  const apiTarget = process.env.VITE_API_PROXY_TARGET || env.VITE_API_PROXY_TARGET || 'http://127.0.0.1:8055'
  const wsTarget = apiTarget.startsWith('https://')
    ? apiTarget.replace(/^https:\/\//, 'wss://')
    : apiTarget.replace(/^http:\/\//, 'ws://')

  return {
    base: '/',
    plugins: [vue(), lanAccessBanner('CloudDisk PC') as any],
    resolve: {
      alias: {
        '@': fileURLToPath(new URL('./src', import.meta.url))
      }
    },
    server: {
      host: true,
      port: 5173,
      strictPort: true,
      proxy: {
        '/api': {
          target: apiTarget,
          changeOrigin: true,
          cookieDomainRewrite: '',
          timeout: 3_600_000,
          proxyTimeout: 3_600_000
        },
        '/share': {
          target: apiTarget,
          changeOrigin: true,
          cookieDomainRewrite: '',
          bypass: (req) => {
            const url = req.url || ''
            const isApi = url.includes('/items') || 
                          url.includes('/access') || 
                          url.includes('/download') || 
                          url.includes('/preview') || 
                          url.includes('/direct-url') || 
                          url.includes('/onlyoffice')
            const isHtml = req.headers.accept && req.headers.accept.includes('text/html')
            if (isHtml && !isApi) {
              return '/index.html'
            }
          }
        },
        '/ws': {
          target: wsTarget,
          ws: true
        }
      }
    },
    build: {
      outDir: 'dist',
      sourcemap: false
    }
  }
})
