import { defineConfig } from "vite";
import vue from "@vitejs/plugin-vue";

const gatewayAccessApiTarget = process.env.HSP_GATEWAY_ACCESS_API_TARGET || "http://127.0.0.1:8090";

export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5184,
    proxy: {
      "/api": {
        target: gatewayAccessApiTarget,
        changeOrigin: true
      }
    }
  }
});
