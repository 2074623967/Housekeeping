import path from "node:path";
import { defineConfig } from "vite";
import vue from "@vitejs/plugin-vue";

const paymentCoreApiTarget = process.env.HSP_PAYMENT_CORE_API_TARGET || "http://127.0.0.1:18081";

export default defineConfig({
  plugins: [vue()],
  cacheDir: "/private/tmp/hsp-h5-web-vite-cache",
  resolve: {
    alias: {
      vue: path.resolve(__dirname, "node_modules/vue"),
      "vue-router": path.resolve(__dirname, "node_modules/vue-router")
    }
  },
  server: {
    port: 5176,
    fs: {
      allow: [path.resolve(__dirname, "..")]
    },
    proxy: {
      "/api": {
        target: paymentCoreApiTarget,
        changeOrigin: true
      }
    }
  }
});
