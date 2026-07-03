'use client'

import { useAuth } from '@/lib/auth/context'
import { useRouter } from 'next/navigation'

export function AppHeader() {
  const { user, logout } = useAuth()
  const router = useRouter()

  const handleLogout = async () => {
    await logout()
    router.push('/login')
  }

  return (
    <header className="border-b border-border bg-background">
      <div className="max-w-7xl mx-auto px-4 py-4 flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold">Video Max</h1>
        </div>
        <div className="flex items-center gap-4">
          <span className="text-sm text-muted-foreground">{user?.name}</span>
          <button
            onClick={handleLogout}
            className="px-4 py-2 text-sm font-medium bg-primary text-primary-foreground rounded-md hover:opacity-90"
          >
            Logout
          </button>
        </div>
      </div>
    </header>
  )
}
