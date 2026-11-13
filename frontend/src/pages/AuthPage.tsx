import { useState, type FormEvent } from 'react'
import { Link, Navigate, useLocation, useNavigate } from 'react-router-dom'
import { Loader2, Wallet } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { useAuth } from '@/auth/AuthContext'
import { errorMessage } from '@/api/client'

const DEMO = { email: 'demo@financecopilot.app', password: 'Demo@123' }

export function AuthPage({ mode }: { mode: 'login' | 'register' }) {
  const { user, login, register } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [displayName, setDisplayName] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  const redirectTo = (location.state as { from?: string } | null)?.from ?? '/'
  if (user) return <Navigate to={redirectTo} replace />

  const isLogin = mode === 'login'

  async function submit(e: FormEvent, credentials = { email, password }) {
    e.preventDefault()
    setError(null)
    setSubmitting(true)
    try {
      if (isLogin) await login(credentials.email, credentials.password)
      else await register(credentials.email, credentials.password, displayName || undefined)
      navigate(redirectTo, { replace: true })
    } catch (err) {
      setError(errorMessage(err))
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div className="flex min-h-screen items-center justify-center bg-gradient-to-br from-indigo-50 via-background to-sky-50 px-4">
      <div className="w-full max-w-sm">
        <div className="mb-6 flex items-center justify-center gap-2">
          <div className="flex size-9 items-center justify-center rounded-xl bg-primary text-primary-foreground">
            <Wallet className="size-5" />
          </div>
          <span className="text-lg font-semibold tracking-tight">Finance Copilot</span>
        </div>
        <Card>
          <CardHeader>
            <CardTitle>{isLogin ? 'Welcome back' : 'Create your account'}</CardTitle>
            <CardDescription>
              {isLogin ? 'Sign in to see your spending.' : 'Upload a statement and get insights in seconds.'}
            </CardDescription>
          </CardHeader>
          <CardContent>
            <form onSubmit={submit} className="space-y-4">
              {!isLogin && (
                <div className="space-y-2">
                  <Label htmlFor="name">Name</Label>
                  <Input id="name" value={displayName} onChange={(e) => setDisplayName(e.target.value)} autoComplete="name" />
                </div>
              )}
              <div className="space-y-2">
                <Label htmlFor="email">Email</Label>
                <Input id="email" type="email" required value={email} onChange={(e) => setEmail(e.target.value)} autoComplete="email" />
              </div>
              <div className="space-y-2">
                <Label htmlFor="password">Password</Label>
                <Input
                  id="password"
                  type="password"
                  required
                  minLength={isLogin ? undefined : 8}
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  autoComplete={isLogin ? 'current-password' : 'new-password'}
                />
                {!isLogin && <p className="text-xs text-muted-foreground">At least 8 characters.</p>}
              </div>
              {error && (
                <p role="alert" className="rounded-md bg-destructive/10 px-3 py-2 text-sm text-destructive">
                  {error}
                </p>
              )}
              <Button type="submit" className="w-full" disabled={submitting}>
                {submitting && <Loader2 className="size-4 animate-spin" />}
                {isLogin ? 'Sign in' : 'Create account'}
              </Button>
              {isLogin && (
                <Button
                  type="button"
                  variant="outline"
                  className="w-full"
                  disabled={submitting}
                  onClick={(e) => {
                    setEmail(DEMO.email)
                    setPassword(DEMO.password)
                    void submit(e as unknown as FormEvent, DEMO)
                  }}
                >
                  Try the demo account
                </Button>
              )}
            </form>
            <p className="mt-4 text-center text-sm text-muted-foreground">
              {isLogin ? 'New here? ' : 'Already have an account? '}
              <Link to={isLogin ? '/register' : '/login'} className="font-medium text-primary hover:underline">
                {isLogin ? 'Create an account' : 'Sign in'}
              </Link>
            </p>
          </CardContent>
        </Card>
      </div>
    </div>
  )
}
