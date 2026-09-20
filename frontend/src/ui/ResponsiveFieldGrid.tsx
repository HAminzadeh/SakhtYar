import { Grid } from '@mui/material'
import type { ReactNode } from 'react'

export function ResponsiveFieldGrid({
  children,
}: {
  children: ReactNode
}) {
  return (
    <Grid container spacing={{ xs: 1.5, sm: 2 }}>
      {children}
    </Grid>
  )
}

export function FieldCell({
  children,
  wide = false,
}: {
  children: ReactNode
  wide?: boolean
}) {
  return (
    <Grid
      size={{
        xs: 12,
        sm: wide ? 12 : 6,
        lg: wide ? 12 : 4,
      }}
    >
      {children}
    </Grid>
  )
}
