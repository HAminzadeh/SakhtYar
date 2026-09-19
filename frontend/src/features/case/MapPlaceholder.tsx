import LocationOnRoundedIcon from '@mui/icons-material/LocationOnRounded'
import {
  Box,
  Card,
  CardContent,
  Stack,
  Typography,
} from '@mui/material'

export function MapPlaceholder() {
  return (
    <Card variant="outlined">
      <CardContent>
        <Box
          sx={{
            minHeight: 320,
            display: 'grid',
            placeItems: 'center',
            border: 1,
            borderStyle: 'dashed',
            borderColor: 'divider',
            borderRadius: 2,
            bgcolor: 'grey.50',
          }}
        >
          <Stack spacing={1.5} alignItems="center" textAlign="center" p={3}>
            <LocationOnRoundedIcon
              color="primary"
              sx={{ fontSize: 48 }}
            />
            <Typography variant="h6" fontWeight={700}>
              نقشه ملک
            </Typography>
            <Typography color="text.secondary" maxWidth={520}>
              زیرساخت مختصات در مدل Property آماده شده است. اتصال نقشه،
              انتخاب نقطه روی نقشه و سرویس Neshan در Iteration بعدی اضافه
              می‌شود.
            </Typography>
          </Stack>
        </Box>
      </CardContent>
    </Card>
  )
}
