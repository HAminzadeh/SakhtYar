import { Navigate, Route, Routes } from 'react-router-dom'
import { AppShell } from './app/AppShell'
import { ProtectedRoute } from './auth/ProtectedRoute'
import { CaseDetailPage } from './pages/CaseDetailPage'
import { CasesPage } from './pages/CasesPage'
import { LoginPage } from './pages/LoginPage'

export default function App() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />

      <Route element={<ProtectedRoute />}>
        <Route element={<AppShell />}>
          <Route path="/cases" element={<CasesPage />} />
          <Route path="/cases/:id" element={<CaseDetailPage />} />
        </Route>
      </Route>

      <Route path="*" element={<Navigate to="/cases" replace />} />
    </Routes>
  )
}
