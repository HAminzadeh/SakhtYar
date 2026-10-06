import { HelpCenterPage } from './pages/HelpCenterPage';
import PlatformConfigPage from './pages/PlatformConfigPage';
import { Navigate, Route, Routes } from 'react-router-dom'
import { AppShell } from './app/AppShell'
import { PermissionRoute } from './auth/PermissionRoute'
import { ProtectedRoute } from './auth/ProtectedRoute'
import { AccountPage } from './pages/AccountPage'
import { CaseDetailPage } from './pages/CaseDetailPage'
import { CasesPage } from './pages/CasesPage'
import { LoginPage } from './pages/LoginPage'
import { RegisterPage } from './pages/RegisterPage'
import { UsersPage } from './pages/UsersPage'
import { PreferencesPage } from './pages/PreferencesPage'
import { AiManagementPage } from './pages/AiManagementPage'
import { OperationsPage } from './pages/OperationsPage'
import { KnowledgeAdminPage } from './pages/KnowledgeAdminPage'

export default function App() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route path="/register" element={<RegisterPage />} />

      <Route element={<ProtectedRoute />}>
        <Route element={<AppShell />}>
          <Route path="/account" element={<AccountPage />} />
          <Route element={<PermissionRoute permission="USER_MANAGE" />}> 
            <Route path="/admin/config" element={<PlatformConfigPage />} />
          </Route>
<Route path="/settings" element={<PreferencesPage />} />
          <Route path="/help" element={<HelpCenterPage />} />

          <Route element={<PermissionRoute permission="CASE_READ" />}>
            <Route path="/cases" element={<CasesPage />} />
            <Route path="/cases/:id" element={<CaseDetailPage />} />
          </Route>

          <Route element={<PermissionRoute permission="USER_MANAGE" />}>
            <Route path="/admin/users" element={<UsersPage />} />
          </Route>
          <Route element={<PermissionRoute permission="AI_MANAGE" />}>
            <Route path="/admin/ai" element={<AiManagementPage />} />
          </Route>
          <Route element={<PermissionRoute permission="OPERATIONS_READ" />}>
            <Route path="/admin/operations" element={<OperationsPage />} />
          </Route>
          <Route element={<PermissionRoute permission="USER_MANAGE" />}>
            <Route path="/admin/knowledge" element={<KnowledgeAdminPage />} />
          </Route>
        </Route>
      </Route>

      <Route path="*" element={<Navigate to="/cases" replace />} />
</Routes>
  )
}
