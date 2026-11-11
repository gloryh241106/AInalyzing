import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// Dev server forwards /api to Spring Boot so the browser sees one origin, as in production.
export default defineConfig({
  plugins: [react()],
  server: {
    proxy: { '/api': 'http://localhost:8080' },
  },
})
