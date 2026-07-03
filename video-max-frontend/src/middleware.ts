import { NextRequest, NextResponse } from 'next/server'

const publicRoutes = ['/login', '/register', '/forgot-password', '/reset-password']
const authRoutes = ['/auth', '/login', '/register', '/forgot-password', '/reset-password']

export function middleware(request: NextRequest) {
  const pathname = request.nextUrl.pathname
  const hasRefreshToken = request.cookies.has('refresh_token')

  // Check if the route is a public auth route
  const isPublicAuthRoute = authRoutes.some(route => pathname.startsWith(route))

  if (isPublicAuthRoute) {
    // User is on an auth page
    if (hasRefreshToken && pathname !== '/') {
      // If they have a refresh token and they're on a login/register page, redirect to library
      if (pathname === '/login' || pathname === '/register') {
        return NextResponse.redirect(new URL('/library', request.url))
      }
    }
    return NextResponse.next()
  }

  // For all other routes (protected routes), check for refresh token
  if (!hasRefreshToken) {
    return NextResponse.redirect(new URL('/login', request.url))
  }

  return NextResponse.next()
}

export const config = {
  matcher: ['/((?!_next/static|_next/image|favicon.ico).*)'],
}
