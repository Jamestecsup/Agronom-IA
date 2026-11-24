import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

// El panel habla con Django (puerto 8000). En desarrollo Vite hace de
// proxy para no depender de CORS; en producción sirve el build estático.
export default defineConfig({
  plugins: [react()],
  server: {
    host: "127.0.0.1",
    port: 5173,
    proxy: {
      "/api": {
        target: "http://127.0.0.1:8000",
        changeOrigin: true,
      },
    },
  },
});
