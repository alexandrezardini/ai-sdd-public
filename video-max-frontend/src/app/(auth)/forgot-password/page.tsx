'use client'

import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { forgotPasswordSchema, type ForgotPasswordFormData } from '@/lib/auth/schemas'
import { useState } from 'react'
import Link from 'next/link'

export default function ForgotPasswordPage() {
  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<ForgotPasswordFormData>({
    resolver: zodResolver(forgotPasswordSchema),
  })

  const [submitted, setSubmitted] = useState(false)

  const onSubmit = async (data: ForgotPasswordFormData) => {
    try {
      const response = await fetch(
        `${process.env.NEXT_PUBLIC_API_BASE}/auth/forgot-password`,
        {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify(data),
        }
      )

      if (response.ok) {
        setSubmitted(true)
      }
    } catch (error) {
      console.error('Error requesting password reset:', error)
    }
  }

  if (submitted) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-background">
        <div className="w-full max-w-md p-8 space-y-8">
          <div className="space-y-2 text-center">
            <h1 className="text-3xl font-bold">Video Max</h1>
            <p className="text-muted-foreground">Password reset email sent</p>
          </div>

          <div className="p-4 bg-accent/10 text-accent rounded-md space-y-4">
            <p>Check your email for a link to reset your password. The link expires in 30 minutes.</p>
            <p>If you don't see the email, check your spam folder.</p>
          </div>

          <Link
            href="/login"
            className="block text-center text-primary hover:underline font-medium"
          >
            Back to sign in
          </Link>
        </div>
      </div>
    )
  }

  return (
    <div className="min-h-screen flex items-center justify-center bg-background">
      <div className="w-full max-w-md p-8 space-y-8">
        <div className="space-y-2 text-center">
          <h1 className="text-3xl font-bold">Video Max</h1>
          <p className="text-muted-foreground">Reset your password</p>
        </div>

        <form onSubmit={handleSubmit(onSubmit)} className="space-y-6">
          <div className="space-y-2">
            <label htmlFor="email" className="text-sm font-medium">
              Email Address
            </label>
            <input
              id="email"
              type="email"
              placeholder="Enter your email address"
              {...register('email')}
              className="w-full px-3 py-2 border border-input rounded-md focus:outline-none focus:ring-2 focus:ring-primary"
            />
            {errors.email && (
              <p className="text-sm text-destructive">{errors.email.message}</p>
            )}
          </div>

          <button
            type="submit"
            disabled={isSubmitting}
            className="w-full bg-primary text-primary-foreground py-2 px-4 rounded-md font-medium hover:opacity-90 disabled:opacity-50"
          >
            {isSubmitting ? 'Sending...' : 'Send reset link'}
          </button>
        </form>

        <div className="text-center text-sm">
          <Link href="/login" className="text-primary hover:underline">
            Back to sign in
          </Link>
        </div>
      </div>
    </div>
  )
}
