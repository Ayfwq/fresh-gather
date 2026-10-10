import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  const backend = env.BACKEND_URL || 'http://127.0.0.1:8080'
  return {
    plugins: [vue()],
    server: { port: 5173, strictPort: true, proxy: {
      '/api': { target: backend, changeOrigin: true, rewrite: path => path.replace(/^\/api/, '') },
      '/uploads': { target: backend, changeOrigin: true },
      '/ws': { target: backend.replace(/^http/, 'ws'), ws: true },
      '/agent': { target: env.AGENT_URL || 'http://127.0.0.1:8001', changeOrigin: true },
    } },
  }
})
