import {
  Box,
  Card,
  CardContent,
  Stack,
  Typography,
} from '@mui/material'
import type { ReactNode } from 'react'

export function SectionCard({
  title,
  description,
  icon,
  action,
  children,
}: {
  title: string
  description?: string
  icon?: ReactNode
  action?: ReactNode
  children: ReactNode
}) {
  return (
    <Card>
      <CardContent sx={{ p: { xs: 2, sm: 2.5, md: 3 } }}>
        <Stack spacing={2.5}>
          <Stack
            direction={{ xs: 'column', sm: 'row' }}
            alignItems={{ xs: 'stretch', sm: 'center' }}
            justifyContent="space-between"
            gap={1.5}
          >
            <Stack direction="row" spacing={1.5} alignItems="center">
              {icon && (
                <Box
                  sx={{
                    width: 42,
                    height: 42,
                    borderRadius: 2.5,
                    display: 'grid',
                    placeItems: 'center',
                    bgcolor: 'primary.50',
                    color: 'primary.main',
                    flexShrink: 0,
                  }}
                >
                  {icon}
                </Box>
              )}

              <Box>
                <Typography variant="h6">{title}</Typography>
                {description && (
                  <Typography
                    variant="body2"
                    color="text.secondary"
                    mt={0.25}
                  >
                    {description}
                  </Typography>
                )}
              </Box>
            </Stack>

            {action}
          </Stack>

          {children}
        </Stack>
      </CardContent>
    </Card>
  )
}
