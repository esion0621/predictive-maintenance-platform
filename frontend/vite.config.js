import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig({
  plugins: [react()],
  server: {
    port: 2005,
    proxy: {
      '/api': {
        target: 'http://master:2006',
        changeOrigin: true,
      }
    }
  }
})
