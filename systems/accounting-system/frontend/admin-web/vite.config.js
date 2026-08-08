import { defineConfig } from "vite";
import vue from "@vitejs/plugin-vue";

const accountingApiTarget = process.env.HSP_ACCOUNTING_API_TARGET || "http://127.0.0.1:18110";

export default defineConfig({
  plugins: [vue()],
  cacheDir: "/private/tmp/hsp-accounting-admin-web-vite-cache",
  server: {
    port: 5275,
    proxy: {
      "/api": {
        target: accountingApiTarget,
        changeOrigin: true
      }
    }
  }
});
