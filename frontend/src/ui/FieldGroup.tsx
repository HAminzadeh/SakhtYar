import { Box, Divider, Stack, Typography } from '@mui/material'
import type { ReactNode } from 'react'

export function FieldGroup({
  title,
  description,
  children,
}: {
  title: string
  description?: string
  children: ReactNode
}) {
  return (
    <Box>
      <Stack spacing={0.35} mb={1.5}>
        <Typography fontWeight={800}>{title}</Typography>
        {description && (
          <Typography variant="body2" color="text.secondary">
            {description}
          </Typography>
        )}
      </Stack>
      <Divider sx={{ mb: 2 }} />
      {children}
    </Box>
  )
}
