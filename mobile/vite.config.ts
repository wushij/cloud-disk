import { defineConfig } from 'vite'
import uni from '@dcloudio/vite-plugin-uni'
// @ts-ignore
import { lanAccessBanner } from '../scripts/vite-lan-banner.mjs'

export default defineConfig({
  base: '/',
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
        target: 'http://127.0.0.1:8055',
        changeOrigin: true,
        timeout: 3_600_000,
        proxyTimeout: 3_600_000
      },
      '/share': {
        target: 'http://127.0.0.1:8055',
        changeOrigin: true,
        bypass: (req, res) => {
          const url = req.url || ''
          const isApi = url.includes('/items') || 
                        url.includes('/access') || 
                        url.includes('/download') || 
                        url.includes('/preview') || 
                        url.includes('/direct-url') || 
                        url.includes('/onlyoffice')
          const isHtml = req.headers.accept && req.headers.accept.includes('text/html')
          if (isHtml && !isApi) {
            const match = url.match(/^\/share\/([^/?#]+)/)
            if (match && match[1]) {
              res.writeHead(302, { Location: `/#/pages/share/view?code=${match[1]}` })
              res.end()
              return false
            }
          }
        }
      },
      '/ws': {
        target: 'ws://127.0.0.1:8055',
        ws: true
      }
    }
  }
})
