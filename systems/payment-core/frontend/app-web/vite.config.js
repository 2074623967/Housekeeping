import { defineConfig } from "vite";
import vue from "@vitejs/plugin-vue";

const paymentCoreApiTarget = process.env.HSP_PAYMENT_CORE_API_TARGET || "http://localhost:18081";

export default defineConfig({
  plugins: [vue()],
  cacheDir: "/private/tmp/hsp-app-web-vite-cache",
  server: {
    port: 5175,
    proxy: {
      "/api": {
        target: paymentCoreApiTarget,
        changeOrigin: true
      }
    }
  }
});
