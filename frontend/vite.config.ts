import { defineConfig } from "vite"
import react from "@vitejs/plugin-react"

const API_BASE_URL = "http://localhost:8080"

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    proxy: {
      "/api": {
        target: process.env.BACKEND_URL ?? API_BASE_URL,
        changeOrigin: true
      }
    }
  }
})
