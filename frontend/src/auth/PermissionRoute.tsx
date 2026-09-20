import { Alert, Stack } from '@mui/material'
import { Navigate, Outlet } from 'react-router-dom'
import type { Permission } from '../api/types'
import { useAuth } from './AuthProvider'

export function PermissionRoute({
  permission,
}: {
  permission: Permission
}) {
  const { user, hasPermission } = useAuth()

  if (!user) {
    return <Navigate to="/login" replace />
  }

  if (!hasPermission(permission)) {
    return (
      <Stack p={3}>
        <Alert severity="error">
          شما مجوز دسترسی به این بخش را ندارید.
        </Alert>
      </Stack>
    )
  }

  return <Outlet />
}
