'use client'

import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { resetPasswordSchema, type ResetPasswordFormData } from '@/lib/auth/schemas'
import { useRouter, useSearchParams } from 'next/navigation'
import { useState } from 'react'
import Link from 'next/link'

export default function ResetPasswordPage() {
  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
    setError,
  } = useForm<ResetPasswordFormData>({
    resolver: zodResolver(resetPasswordSchema),
  })

  const router = useRouter()
  const searchParams = useSearchParams()
  const token = searchParams.get('token')
  const [submitted, setSubmitted] = useState(false)

  if (!token) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-background">
        <div className="w-full max-w-md p-8 space-y-8">
          <div className="space-y-2 text-center">
            <h1 className="text-3xl font-bold">Video Max</h1>
            <p className="text-muted-foreground">Invalid reset link</p>
          </div>

          <div className="p-4 bg-destructive/10 text-destructive rounded-md">
            This reset link is invalid or has expired. Please request a new one.
          </div>

          <Link
            href="/forgot-password"
            className="block text-center text-primary hover:underline font-medium"
          >
            Request a new password reset
          </Link>
        </div>
      </div>
    )
  }

  const onSubmit = async (data: ResetPasswordFormData) => {
    try {
      const response = await fetch(
        `${process.env.NEXT_PUBLIC_API_BASE}/auth/reset-password`,
        {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({
            token,
            newPassword: data.password,
          }),
        }
      )

      if (!response.ok) {
        const errorData = await response.json()
        throw new Error(errorData.detail || 'Reset failed')
      }

      setSubmitted(true)
      setTimeout(() => router.push('/login'), 3000)
    } catch (error) {
      const message = error instanceof Error ? error.message : 'Reset failed'
      setError('root', { message })
    }
  }

  if (submitted) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-background">
        <div className="w-full max-w-md p-8 space-y-8">
          <div className="space-y-2 text-center">
            <h1 className="text-3xl font-bold">Video Max</h1>
            <p className="text-muted-foreground">Password reset successfully</p>
          </div>

          <div className="p-4 bg-accent/10 text-accent rounded-md">
            Your password has been reset. You will be redirected to sign in shortly.
          </div>
        </div>
      </div>
    )
  }

  return (
    <div className="min-h-screen flex items-center justify-center bg-background">
      <div className="w-full max-w-md p-8 space-y-8">
        <div className="space-y-2 text-center">
          <h1 className="text-3xl font-bold">Video Max</h1>
          <p className="text-muted-foreground">Set your new password</p>
        </div>

        <form onSubmit={handleSubmit(onSubmit)} className="space-y-6">
          <div className="space-y-2">
            <label htmlFor="password" className="text-sm font-medium">
              New Password
            </label>
            <input
              id="password"
              type="password"
              placeholder="At least 8 characters, with uppercase and number"
              {...register('password')}
              className="w-full px-3 py-2 border border-input rounded-md focus:outline-none focus:ring-2 focus:ring-primary"
            />
            {errors.password && (
              <p className="text-sm text-destructive">{errors.password.message}</p>
            )}
          </div>

          <div className="space-y-2">
            <label htmlFor="confirmPassword" className="text-sm font-medium">
              Confirm Password
            </label>
            <input
              id="confirmPassword"
              type="password"
              placeholder="Confirm your new password"
              {...register('confirmPassword')}
              className="w-full px-3 py-2 border border-input rounded-md focus:outline-none focus:ring-2 focus:ring-primary"
            />
            {errors.confirmPassword && (
              <p className="text-sm text-destructive">{errors.confirmPassword.message}</p>
            )}
          </div>

          {errors.root && (
            <div className="p-3 text-sm text-destructive bg-destructive/10 rounded-md">
              {errors.root.message}
            </div>
          )}

          <button
            type="submit"
            disabled={isSubmitting}
            className="w-full bg-primary text-primary-foreground py-2 px-4 rounded-md font-medium hover:opacity-90 disabled:opacity-50"
          >
            {isSubmitting ? 'Resetting...' : 'Reset password'}
          </button>
        </form>
      </div>
    </div>
  )
}
