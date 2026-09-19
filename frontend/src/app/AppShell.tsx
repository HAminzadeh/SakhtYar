import ConstructionRoundedIcon from '@mui/icons-material/ConstructionRounded'
import LogoutRoundedIcon from '@mui/icons-material/LogoutRounded'
import {
  AppBar,
  Box,
  Button,
  Container,
  Stack,
  Toolbar,
  Typography,
} from '@mui/material'
import { Link, Outlet, useNavigate } from 'react-router-dom'
import { useAuth } from '../auth/AuthProvider'

export function AppShell() {
  const { user, logout } = useAuth()
  const navigate = useNavigate()

  const handleLogout = async () => {
    await logout()
    navigate('/login')
  }

  return (
    <Box minHeight="100vh" bgcolor="grey.50">
      <AppBar position="static" elevation={0}>
        <Toolbar>
          <ConstructionRoundedIcon sx={{ ml: 1 }} />
          <Typography variant="h6" sx={{ flexGrow: 1 }}>
            ساخت‌یار
          </Typography>

          <Stack direction="row" spacing={1} alignItems="center">
            <Button color="inherit" component={Link} to="/cases">
              پرونده‌ها
            </Button>
            <Typography variant="body2">
              {user?.displayName}
            </Typography>
            <Button
              color="inherit"
              startIcon={<LogoutRoundedIcon />}
              onClick={handleLogout}
            >
              خروج
            </Button>
          </Stack>
        </Toolbar>
      </AppBar>

      <Container maxWidth="xl" sx={{ py: 4 }}>
        <Outlet />
      </Container>
    </Box>
  )
}
