import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
export default defineConfig({
  plugins: [vue()],
  resolve: { alias: { vue: 'vue/dist/vue.esm-bundler.js' } },
  server: { proxy: { '/api': 'http://127.0.0.1:8080', '/images': 'http://127.0.0.1:8080' } },
  build: { chunkSizeWarningLimit: 2500 },
})
