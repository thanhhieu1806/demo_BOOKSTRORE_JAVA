import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig({
  plugins: [react()],
  base: '/dem_login-0.0.1-SNAPSHOT/',

  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:8080/dem_login-0.0.1-SNAPSHOT',
        changeOrigin: true,
        secure: false
      }
    }
  },

  build: {
    outDir: 'dist',
    assetsDir: 'assets'
  }
})