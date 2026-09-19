import {
  createContext,
  type ReactNode,
  useContext,
  useEffect,
  useMemo,
  useState,
} from 'react'
import { api } from '../api/client'
import type { Me } from '../api/types'

type AuthContextValue = {
  user: Me | null
  loading: boolean
  login: (username: string, password: string) => Promise<void>
  logout: () => Promise<void>
  refresh: () => Promise<void>
}

const AuthContext = createContext<AuthContextValue | null>(null)

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<Me | null>(null)
  const [loading, setLoading] = useState(true)

  const refresh = async () => {
    try {
      const me = await api<Me>('/api/v1/auth/me')
      setUser(me)
    } catch {
      setUser(null)
    }
  }

  useEffect(() => {
    refresh().finally(() => setLoading(false))
  }, [])

  const login = async (username: string, password: string) => {
    const me = await api<Me>('/api/v1/auth/login', {
      method: 'POST',
      body: JSON.stringify({ username, password }),
    })
    setUser(me)
  }

  const logout = async () => {
    await api<void>('/api/v1/auth/logout', { method: 'POST' })
    setUser(null)
  }

  const value = useMemo(
    () => ({ user, loading, login, logout, refresh }),
    [user, loading],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth() {
  const value = useContext(AuthContext)
  if (!value) {
    throw new Error('useAuth must be used inside AuthProvider')
  }
  return value
}
