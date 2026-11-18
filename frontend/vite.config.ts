import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  resolve: { alias: { vue: 'vue/dist/vue.esm-bundler.js' } },
  build: {
    cssCodeSplit: false,
    chunkSizeWarningLimit: 2500,
    rollupOptions: {
      input: { workboard: 'src/main.ts' },
      output: {
        entryFileNames: 'assets/workboard.js',
        assetFileNames: (asset) =>
          asset.names.includes('style.css') || asset.names.includes('workboard.css')
            ? 'assets/workboard.css'
            : 'assets/[name]-[hash][extname]',
      },
    },
  },
})
