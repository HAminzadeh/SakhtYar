import ConstructionRoundedIcon from '@mui/icons-material/ConstructionRounded'
import FolderRoundedIcon from '@mui/icons-material/FolderRounded'
import LogoutRoundedIcon from '@mui/icons-material/LogoutRounded'
import PersonRoundedIcon from '@mui/icons-material/PersonRounded'
import {
  AppBar,
  Avatar,
  BottomNavigation,
  BottomNavigationAction,
  Box,
  Button,
  Container,
  Stack,
  Toolbar,
  Typography,
  useMediaQuery,
  useTheme,
} from '@mui/material'
import { Link, Outlet, useLocation, useNavigate } from 'react-router-dom'
import { useAuth } from '../auth/AuthProvider'

export function AppShell() {
  const { user, logout } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const theme = useTheme()
  const isMobile = useMediaQuery(theme.breakpoints.down('sm'))

  const handleLogout = async () => {
    await logout()
    navigate('/login')
  }

  return (
    <Box minHeight="100vh" bgcolor="background.default">
      <AppBar
        position="sticky"
        elevation={0}
        color="inherit"
        sx={{
          borderBottom: '1px solid',
          borderColor: 'divider',
          backdropFilter: 'blur(12px)',
          bgcolor: 'rgba(255,255,255,0.94)',
        }}
      >
        <Toolbar sx={{ minHeight: { xs: 60, md: 68 } }}>
          <Stack direction="row" spacing={1.25} alignItems="center">
            <Box
              sx={{
                width: 38,
                height: 38,
                borderRadius: 2.5,
                display: 'grid',
                placeItems: 'center',
                bgcolor: 'primary.main',
                color: 'primary.contrastText',
              }}
            >
              <ConstructionRoundedIcon fontSize="small" />
            </Box>

            <Box>
              <Typography fontWeight={900} lineHeight={1.1}>
                ساخت‌یار
              </Typography>
              {!isMobile && (
                <Typography variant="caption" color="text.secondary">
                  مدیریت هوشمند مشارکت در ساخت
                </Typography>
              )}
            </Box>
          </Stack>

          <Box sx={{ flexGrow: 1 }} />

          {!isMobile && (
            <Stack direction="row" spacing={1} alignItems="center">
              <Button component={Link} to="/cases" color="inherit">
                پرونده‌ها
              </Button>

              <Stack direction="row" spacing={1} alignItems="center" mx={1}>
                <Avatar
                  sx={{
                    width: 32,
                    height: 32,
                    bgcolor: 'primary.50',
                    color: 'primary.main',
                  }}
                >
                  <PersonRoundedIcon fontSize="small" />
                </Avatar>
                <Typography variant="body2" fontWeight={700}>
                  {user?.displayName}
                </Typography>
              </Stack>

              <Button
                color="inherit"
                startIcon={<LogoutRoundedIcon />}
                onClick={handleLogout}
              >
                خروج
              </Button>
            </Stack>
          )}
        </Toolbar>
      </AppBar>

      <Container
        maxWidth={false}
        sx={{
          maxWidth: '1440px',
          py: { xs: 2, sm: 3, md: 4 },
          px: { xs: 1.5, sm: 2.5, md: 3 },
          pb: { xs: 10, sm: 4 },
        }}
      >
        <Outlet />
      </Container>

      {isMobile && (
        <BottomNavigation
          showLabels
          value={location.pathname.startsWith('/cases') ? 'cases' : false}
          onChange={(_, value) => {
            if (value === 'cases') navigate('/cases')
            if (value === 'logout') handleLogout()
          }}
          sx={{
            position: 'fixed',
            left: 0,
            right: 0,
            bottom: 0,
            zIndex: 1300,
            borderTop: '1px solid',
            borderColor: 'divider',
            height: 68,
            bgcolor: 'rgba(255,255,255,0.97)',
            backdropFilter: 'blur(12px)',
          }}
        >
          <BottomNavigationAction
            value="cases"
            label="پرونده‌ها"
            icon={<FolderRoundedIcon />}
          />
          <BottomNavigationAction
            value="logout"
            label="خروج"
            icon={<LogoutRoundedIcon />}
          />
        </BottomNavigation>
      )}
    </Box>
  )
}
