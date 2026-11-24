import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

// La web de usuario habla con Spring Boot (puerto 8081). El proxy evita CORS en
// desarrollo y mantiene las rutas /agronomia-api iguales a las del móvil.
export default defineConfig({
  plugins: [react()],
  server: {
    host: "127.0.0.1",
    port: 5174,
    proxy: {
      "/agronomia-api": {
        target: "http://127.0.0.1:8081",
        changeOrigin: true,
      },
    },
  },
});
