import { defineConfig } from "vite";
import vue from "@vitejs/plugin-vue";

const walletAccountApiTarget = process.env.HSP_WALLET_ACCOUNT_API_TARGET || "http://localhost:8095";

export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5178,
    proxy: {
      "/api": {
        target: walletAccountApiTarget,
        changeOrigin: true
      }
    }
  }
});
