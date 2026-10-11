import { defineConfig } from "vite";
import vue from "@vitejs/plugin-vue";

const refundCenterApiTarget = process.env.REFUND_CENTER_API_TARGET || "http://localhost:18140";

export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5179,
    proxy: {
      "/api": refundCenterApiTarget
    }
  }
});
