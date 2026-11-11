// All calls go to the same origin; in dev Vite proxies /api to Spring Boot (vite.config.js).
// Request and response shapes: docs/api.md.

export async function getHealth() {
  const res = await fetch('/api/health')
  return res.json()
}

export async function getDemoCases() {
  const res = await fetch('/api/demo')
  if (!res.ok) throw await res.json()
  return res.json()
}
