import { defineConfig } from "vite";
import vue from "@vitejs/plugin-vue";

const walletApiTarget = process.env.HSP_WALLET_API_TARGET || "http://localhost:8094";

export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5185,
    proxy: {
      "/api": {
        target: walletApiTarget,
        changeOrigin: true
      }
    }
  }
});
