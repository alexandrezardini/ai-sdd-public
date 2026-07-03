import './globals.css'
import { AuthProvider } from '@/lib/auth/context'

export const metadata = {
  title: 'Video Max',
  description: 'Your personal video management platform',
}

export default function RootLayout({
  children,
}: {
  children: React.ReactNode
}) {
  return (
    <html lang="en">
      <body>
        <AuthProvider>
          {children}
        </AuthProvider>
      </body>
    </html>
  )
}
