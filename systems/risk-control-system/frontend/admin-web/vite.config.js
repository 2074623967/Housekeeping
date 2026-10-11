import { defineConfig } from "vite";
import vue from "@vitejs/plugin-vue";

const riskControlApiTarget = process.env.RISK_CONTROL_API_TARGET || "http://localhost:18180";

export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5183,
    proxy: {
      "/api": riskControlApiTarget
    }
  }
});
