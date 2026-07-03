const API_BASE = process.env.NEXT_PUBLIC_API_BASE || 'http://localhost:8080/api/v1'

export interface AuthResponse {
  accessToken: string
  expiresIn: number
  user: {
    id: string
    email: string
    name: string
    createdAt: string
  }
}

export interface ProblemDetail {
  type: string
  title: string
  status: number
  detail: string
  code?: string
}

class AuthClient {
  private refreshPromise: Promise<string> | null = null

  async register(
    name: string,
    email: string,
    password: string
  ): Promise<AuthResponse> {
    const response = await fetch(`${API_BASE}/auth/register`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      credentials: 'include',
      body: JSON.stringify({ name, email, password }),
    })

    if (!response.ok) {
      const error = await response.json()
      throw new Error(error.detail || 'Registration failed')
    }

    return response.json()
  }

  async login(email: string, password: string): Promise<AuthResponse> {
    const response = await fetch(`${API_BASE}/auth/login`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      credentials: 'include',
      body: JSON.stringify({ email, password }),
    })

    if (!response.ok) {
      const error = await response.json()
      throw new Error(error.detail || 'Invalid email or password')
    }

    return response.json()
  }

  async refresh(): Promise<AuthResponse> {
    if (this.refreshPromise) {
      return this.refreshPromise
    }

    this.refreshPromise = (async () => {
      try {
        const response = await fetch(`${API_BASE}/auth/refresh`, {
          method: 'POST',
          credentials: 'include',
        })

        if (!response.ok) {
          throw new Error('Refresh failed')
        }

        return response.json()
      } finally {
        this.refreshPromise = null
      }
    })()

    return this.refreshPromise
  }

  async logout(): Promise<void> {
    await fetch(`${API_BASE}/auth/logout`, {
      method: 'POST',
      credentials: 'include',
    })
  }

  async forgotPassword(email: string): Promise<void> {
    const response = await fetch(`${API_BASE}/auth/forgot-password`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      credentials: 'include',
      body: JSON.stringify({ email }),
    })

    if (!response.ok) {
      const error = await response.json()
      throw new Error(error.detail || 'Request failed')
    }
  }

  async resetPassword(token: string, newPassword: string): Promise<void> {
    const response = await fetch(`${API_BASE}/auth/reset-password`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      credentials: 'include',
      body: JSON.stringify({ token, newPassword }),
    })

    if (!response.ok) {
      const error = await response.json()
      throw new Error(error.detail || 'Reset failed')
    }
  }

  async getMe(accessToken: string) {
    const response = await fetch(`${API_BASE}/auth/me`, {
      headers: {
        Authorization: `Bearer ${accessToken}`,
      },
      credentials: 'include',
    })

    if (!response.ok) {
      if (response.status === 401) {
        throw new Error('Unauthorized')
      }
      const error = await response.json()
      throw new Error(error.detail || 'Failed to get user')
    }

    return response.json()
  }
}

export const authClient = new AuthClient()
