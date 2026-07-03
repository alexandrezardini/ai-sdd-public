'use client'

import { useAuth } from '@/lib/auth/context'
import { AppHeader } from '@/components/layout/AppHeader'

export default function LibraryPage() {
  const { user } = useAuth()

  return (
    <div className="min-h-screen bg-background">
      <AppHeader />
      <main className="max-w-7xl mx-auto px-4 py-12">
        <div className="text-center space-y-4">
          <h1 className="text-4xl font-bold">Welcome, {user?.name}!</h1>
          <p className="text-muted-foreground text-lg">
            Your video library is ready. F06 will bring video management here.
          </p>
        </div>
      </main>
    </div>
  )
}
