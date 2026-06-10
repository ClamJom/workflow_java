import { fileURLToPath, URL } from 'node:url'

import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// https://vite.dev/config/
export default defineConfig({
  plugins: [
    vue(),
  ],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url))
    },
  },
  server:{
    open: true,
    proxy:{
      '/api/v3': {
        target: 'http://localhost:8080/api/v3',
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/api\/v3/, ''),
        configure: (proxy, options) =>{
          proxy.on('proxyReq', (proxyReq, req, res) => {
            console.log('[代理请求]', req.url, '->', options.target + req.url);
          })
        }
      },
    }
  }
})
