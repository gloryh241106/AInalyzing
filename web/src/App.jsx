import { useEffect, useState } from 'react'
import { getHealth } from './api/client.js'

// Placeholder until task 07 (S01) and 08 (S02) land. Shows whether the API and database are reachable.
export default function App() {
  const [health, setHealth] = useState('đang kiểm tra…')

  useEffect(() => {
    getHealth()
      .then((h) => setHealth(`API ${h.status}, DB ${h.db}`))
      .catch(() => setHealth('không gọi được API'))
  }, [])

  return (
    <main style={{ fontFamily: 'system-ui, sans-serif', maxWidth: 720, margin: '0 auto', padding: 16 }}>
      <h1>AInalyzing</h1>
      <p>Trạng thái: {health}</p>
    </main>
  )
}
