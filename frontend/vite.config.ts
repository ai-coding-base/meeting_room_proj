/// <reference types="vitest" />
import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// 生产构建后的 index.html / assets 会被 Spring Boot 静态托管在同源 8000 上，
// 因此 axios 直接走相对路径 /api/* 即可，无需 proxy。
// 本地开发时通过 vite dev server 5173 → proxy 转发到后端 8000。
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:8000',
        changeOrigin: true,
      },
    },
  },
  build: {
    outDir: 'dist',
    sourcemap: false,
  },
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: ['./src/test/setup.ts'],
    css: false,
  },
});
