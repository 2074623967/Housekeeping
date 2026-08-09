import { defineConfig } from "vite";
import vue from "@vitejs/plugin-vue";

const clearingApiTarget = process.env.HSP_CLEARING_API_TARGET || "http://127.0.0.1:18122";

export default defineConfig({
  plugins: [vue()],
  cacheDir: "/private/tmp/hsp-clearing-admin-web-vite-cache",
  server: {
    port: 5273,
    proxy: {
      "/api": {
        target: clearingApiTarget,
        changeOrigin: true
      }
    }
  }
});
