import React, {
  Children,
  cloneElement,
  isValidElement,
  useEffect,
  useState,
  type ChangeEvent,
  type KeyboardEvent,
  type ReactNode,
} from 'react'
import {
  Alert as AntAlert,
  Avatar as AntAvatar,
  Button as AntButton,
  Card as AntCard,
  Checkbox as AntCheckbox,
  Divider as AntDivider,
  Input,
  Modal,
  Progress,
  Select,
  Skeleton as AntSkeleton,
  Spin,
  Tabs as AntTabs,
  Tag,
  Tooltip as AntTooltip,
  Typography as AntTypography,
} from 'antd'

type AnyProps = Record<string, any>
type InputEvent = ChangeEvent<HTMLInputElement | HTMLTextAreaElement>
type InputKeyEvent = KeyboardEvent<HTMLInputElement | HTMLTextAreaElement>

const colors: Record<string, string> = {
  'primary.main': '#2563EB',
  'primary.contrastText': '#FFFFFF',
  'primary.50': '#EFF6FF',
  'text.primary': '#101828',
  'text.secondary': '#667085',
  divider: '#E4E7EC',
  'background.default': '#F6F8FC',
  'background.paper': '#FFFFFF',
  'grey.50': '#F9FAFB',
  'grey.100': '#F2F4F7',
}

const spacing = (value: any) =>
  typeof value === 'number' ? value * 8 : value

const radius = (value: any) =>
  typeof value === 'number' ? value * 4 : value

const resolveToken = (value: any) =>
  typeof value === 'string' ? colors[value] ?? value : value

function useViewport() {
  const [width, setWidth] = useState(() =>
    typeof window === 'undefined' ? 1200 : window.innerWidth,
  )

  useEffect(() => {
    const onResize = () => setWidth(window.innerWidth)
    window.addEventListener('resize', onResize)
    return () => window.removeEventListener('resize', onResize)
  }, [])

  return width
}

function responsive(value: any, width: number) {
  if (value == null || typeof value !== 'object' || Array.isArray(value)) {
    return value
  }

  if (width >= 1200 && value.xl != null) return value.xl
  if (width >= 992 && value.lg != null) return value.lg
  if (width >= 768 && value.md != null) return value.md
  if (width >= 576 && value.sm != null) return value.sm

  return value.xs ?? value.sm ?? value.md ?? value.lg ?? value.xl
}

function systemStyle(source: AnyProps | undefined, width: number): React.CSSProperties {
  if (!source || typeof source !== 'object' || Array.isArray(source)) return {}

  const s = source
  const out: AnyProps = {}

  const plain = [
    'width',
    'height',
    'minWidth',
    'maxWidth',
    'minHeight',
    'maxHeight',
    'display',
    'position',
    'top',
    'right',
    'bottom',
    'left',
    'zIndex',
    'overflow',
    'overflowX',
    'overflowY',
    'opacity',
    'cursor',
    'textAlign',
    'flex',
    'flexGrow',
    'flexShrink',
    'flexBasis',
    'flexWrap',
    'alignItems',
    'alignSelf',
    'justifyContent',
    'placeItems',
    'fontSize',
    'fontWeight',
    'lineHeight',
    'letterSpacing',
    'whiteSpace',
    'background',
    'boxShadow',
    'backdropFilter',
    'transition',
  ]

  for (const key of plain) {
    if (s[key] != null) {
      out[key] = responsive(s[key], width)
    }
  }

  for (const key of ['color', 'backgroundColor', 'borderColor']) {
    if (s[key] != null) {
      out[key] = resolveToken(responsive(s[key], width))
    }
  }

  if (s.bgcolor != null) {
    out.backgroundColor = resolveToken(responsive(s.bgcolor, width))
  }

  for (const key of ['gap', 'rowGap', 'columnGap']) {
    if (s[key] != null) {
      out[key] = spacing(responsive(s[key], width))
    }
  }

  if (s.borderRadius != null) {
    out.borderRadius = radius(responsive(s.borderRadius, width))
  }

  for (const key of ['border', 'borderTop', 'borderBottom', 'borderLeft', 'borderRight']) {
    if (s[key] != null) {
      const value = responsive(s[key], width)
      out[key] = typeof value === 'number' ? `${value}px solid` : value
    }
  }

  const paddingMap: Record<string, string> = {
    p: 'padding',
    pt: 'paddingTop',
    pb: 'paddingBottom',
    pl: 'paddingLeft',
    pr: 'paddingRight',
    px: 'paddingInline',
    py: 'paddingBlock',
  }

  const marginMap: Record<string, string> = {
    m: 'margin',
    mt: 'marginTop',
    mb: 'marginBottom',
    ml: 'marginLeft',
    mr: 'marginRight',
    mx: 'marginInline',
    my: 'marginBlock',
  }

  for (const [key, cssKey] of Object.entries(paddingMap)) {
    if (s[key] != null) {
      out[cssKey] = spacing(responsive(s[key], width))
    }
  }

  for (const [key, cssKey] of Object.entries(marginMap)) {
    if (s[key] != null) {
      out[cssKey] = spacing(responsive(s[key], width))
    }
  }

  return out
}

const systemKeys = new Set([
  'sx','spacing','direction','alignItems','alignSelf','justifyContent','gap',
  'rowGap','columnGap','bgcolor','m','mt','mb','ml','mr','mx','my',
  'p','px','py','pt','pb','pl','pr','component','fullWidth','startIcon',
  'endIcon','severity','variant','gutterBottom','fontWeight','color','display',
  'border','borderTop','borderBottom','borderLeft','borderRight','borderColor',
  'borderRadius','size','container','item','width','height','minWidth','maxWidth',
  'minHeight','maxHeight','position','top','right','bottom','left','zIndex',
  'overflow','overflowX','overflowY','opacity','cursor','textAlign','flex',
  'flexGrow','flexShrink','flexBasis','flexWrap','placeItems','fontSize',
  'lineHeight','letterSpacing','whiteSpace','background','backgroundColor',
  'boxShadow','backdropFilter','transition','scrollButtons',
  'allowScrollButtonsMobile','iconPosition','showLabels','elevation','maxWidth',
])

function clean(props: AnyProps) {
  const rest: AnyProps = {}
  for (const [key, value] of Object.entries(props)) {
    if (!systemKeys.has(key)) rest[key] = value
  }
  return rest
}

function mergedStyle(
  props: AnyProps,
  width: number,
  extra?: React.CSSProperties,
): React.CSSProperties {
  return {
    ...systemStyle(props, width),
    ...systemStyle(props.sx, width),
    ...extra,
    ...(props.style ?? {}),
  }
}

export function Box({
  component: Component = 'div',
  children,
  ...props
}: AnyProps) {
  const width = useViewport()
  return (
    <Component {...clean(props)} style={mergedStyle(props, width)}>
      {children}
    </Component>
  )
}

export function Stack({
  component: Component = 'div',
  children,
  direction = 'column',
  spacing: spacingValue,
  gap,
  alignItems,
  justifyContent,
  ...props
}: AnyProps) {
  const width = useViewport()
  const resolvedDirection = responsive(direction, width)
  const resolvedGap =
    gap != null
      ? spacing(responsive(gap, width))
      : spacingValue != null
        ? spacing(responsive(spacingValue, width))
        : undefined

  return (
    <Component
      {...clean(props)}
      style={mergedStyle(props, width, {
        display: 'flex',
        flexDirection: resolvedDirection,
        gap: resolvedGap,
        alignItems: responsive(alignItems, width),
        justifyContent: responsive(justifyContent, width),
      })}
    >
      {children}
    </Component>
  )
}

export function Grid({
  children,
  container,
  spacing: spacingValue,
  size,
  ...props
}: AnyProps) {
  const width = useViewport()

  if (container) {
    const gap = spacing(responsive(spacingValue ?? 0, width))
    return (
      <div
        {...clean(props)}
        style={mergedStyle(props, width, {
          display: 'grid',
          gridTemplateColumns: 'repeat(12, minmax(0, 1fr))',
          gap,
        })}
      >
        {children}
      </div>
    )
  }

  const resolved =
    typeof size === 'number' ? size : responsive(size ?? 12, width) ?? 12

  return (
    <div
      {...clean(props)}
      style={mergedStyle(props, width, {
        gridColumn: `span ${Math.max(1, Math.min(12, Number(resolved)))} / span ${Math.max(1, Math.min(12, Number(resolved)))}`,
        minWidth: 0,
      })}
    >
      {children}
    </div>
  )
}

export function Typography({
  variant,
  children,
  component,
  color,
  fontWeight,
  gutterBottom,
  ...props
}: AnyProps) {
  const width = useViewport()
  const style = mergedStyle(props, width, {
    color: color ? resolveToken(color) : undefined,
    fontWeight,
    marginBottom: gutterBottom ? 8 : undefined,
  })

  if (component) {
    const C = component
    return <C {...clean(props)} style={style}>{children}</C>
  }

  if (variant === 'h4') {
    return <AntTypography.Title level={2} style={style}>{children}</AntTypography.Title>
  }
  if (variant === 'h5') {
    return <AntTypography.Title level={3} style={style}>{children}</AntTypography.Title>
  }
  if (variant === 'h6') {
    return <AntTypography.Title level={5} style={style}>{children}</AntTypography.Title>
  }
  if (variant === 'caption') {
    return (
      <AntTypography.Text
        type={color === 'text.secondary' ? 'secondary' : undefined}
        style={{ fontSize: 12, ...style }}
      >
        {children}
      </AntTypography.Text>
    )
  }

  return (
    <AntTypography.Text
      type={color === 'text.secondary' ? 'secondary' : undefined}
      style={style}
    >
      {children}
    </AntTypography.Text>
  )
}

export function Button({
  variant,
  color,
  size,
  startIcon,
  endIcon,
  fullWidth,
  component: Component,
  to,
  children,
  type: htmlType,
  ...props
}: AnyProps) {
  const width = useViewport()

  const button = (
    <AntButton
      {...clean(props)}
      danger={color === 'error'}
      type={
        variant === 'contained'
          ? 'primary'
          : variant === 'text'
            ? 'text'
            : 'default'
      }
      htmlType={
        htmlType === 'submit' || htmlType === 'reset'
          ? htmlType
          : 'button'
      }
      size={
        size === 'large'
          ? 'large'
          : size === 'small'
            ? 'small'
            : 'middle'
      }
      icon={startIcon}
      style={mergedStyle(
        props,
        width,
        fullWidth ? { width: '100%' } : undefined,
      )}
    >
      {children}
      {endIcon ? <span style={{ marginInlineStart: 6 }}>{endIcon}</span> : null}
    </AntButton>
  )

  if (Component) {
    return (
      <Component to={to} style={{ textDecoration: 'none' }}>
        {button}
      </Component>
    )
  }

  return button
}

export function IconButton({ children, color, ...props }: AnyProps) {
  const width = useViewport()
  return (
    <AntButton
      {...clean(props)}
      danger={color === 'error'}
      type="text"
      shape="circle"
      icon={children}
      style={mergedStyle(props, width)}
    />
  )
}

export function Card({ children, variant, ...props }: AnyProps) {
  const width = useViewport()
  return (
    <AntCard
      {...clean(props)}
      bordered={variant === 'outlined' || props.bordered !== false}
      styles={{ body: { padding: 0 } }}
      style={mergedStyle(props, width)}
    >
      {children}
    </AntCard>
  )
}

export function CardContent({ children, ...props }: AnyProps) {
  const width = useViewport()
  const hasOwnPadding =
    props?.sx?.p != null ||
    props?.sx?.px != null ||
    props?.sx?.py != null ||
    props?.p != null ||
    props?.px != null ||
    props?.py != null

  return (
    <div
      {...clean(props)}
      style={mergedStyle(
        props,
        width,
        hasOwnPadding ? undefined : { padding: width < 576 ? 16 : 24 },
      )}
    >
      {children}
    </div>
  )
}

export function CardActionArea({ children, onClick, ...props }: AnyProps) {
  const width = useViewport()
  return (
    <div
      role="button"
      tabIndex={0}
      onClick={onClick}
      {...clean(props)}
      style={mergedStyle(props, width, { cursor: 'pointer' })}
    >
      {children}
    </div>
  )
}

export function Paper({ children, ...props }: AnyProps) {
  const width = useViewport()
  return (
    <div
      {...clean(props)}
      style={mergedStyle(props, width, {
        background: '#fff',
      })}
    >
      {children}
    </div>
  )
}

export function Alert({
  severity = 'info',
  children,
  onClose,
  ...props
}: AnyProps) {
  const type =
    severity === 'error'
      ? 'error'
      : severity === 'warning'
        ? 'warning'
        : severity === 'success'
          ? 'success'
          : 'info'

  return (
    <AntAlert
      {...clean(props)}
      type={type}
      showIcon
      closable={Boolean(onClose)}
      onClose={onClose}
      message={children}
    />
  )
}

export function Chip({ label, color, icon, onClick, ...props }: AnyProps) {
  const width = useViewport()
  const antColor =
    color === 'success'
      ? 'success'
      : color === 'warning'
        ? 'warning'
        : color === 'error'
          ? 'error'
          : color === 'primary'
            ? 'blue'
            : undefined

  return (
    <Tag
      {...clean(props)}
      color={antColor}
      icon={icon}
      onClick={onClick}
      style={mergedStyle(
        props,
        width,
        onClick ? { cursor: 'pointer' } : undefined,
      )}
    >
      {label}
    </Tag>
  )
}

export function CircularProgress({ size = 24 }: { size?: number }) {
  return <Spin size={size >= 32 ? 'large' : 'small'} />
}

export function LinearProgress({ value = 0, ...props }: AnyProps) {
  const width = useViewport()
  return (
    <Progress
      percent={value}
      showInfo={false}
      strokeLinecap="round"
      style={mergedStyle(props, width)}
    />
  )
}

export function Divider(props: AnyProps) {
  const width = useViewport()
  return <AntDivider {...clean(props)} style={mergedStyle(props, width)} />
}

export function Avatar({ children, ...props }: AnyProps) {
  const width = useViewport()
  return (
    <AntAvatar {...clean(props)} style={mergedStyle(props, width)}>
      {children}
    </AntAvatar>
  )
}

export function Tooltip({ title, children, ...props }: AnyProps) {
  return <AntTooltip title={title} {...clean(props)}>{children}</AntTooltip>
}

export function Checkbox({
  checked,
  onChange,
  ...props
}: {
  checked?: boolean
  onChange?: (event: { target: { checked: boolean } }) => void
} & AnyProps) {
  return (
    <AntCheckbox
      {...clean(props)}
      checked={checked}
      onChange={onChange as any}
    />
  )
}

export function FormControlLabel({ control, label, ...props }: AnyProps) {
  const width = useViewport()
  return (
    <label
      {...clean(props)}
      style={mergedStyle(props, width, {
        display: 'flex',
        gap: 8,
        alignItems: 'center',
      })}
    >
      {control}
      <span>{label}</span>
    </label>
  )
}

export function MenuItem(_: { value: string; children: ReactNode }) {
  return null
}
;(MenuItem as AnyProps).__menuItem = true

type TextFieldProps = {
  label?: ReactNode
  value?: string | number | null
  onChange?: (event: InputEvent) => void
  onKeyDown?: (event: InputKeyEvent) => void
  type?: string
  multiline?: boolean
  minRows?: number
  helperText?: ReactNode
  error?: boolean
  select?: boolean
  children?: ReactNode
  fullWidth?: boolean
  placeholder?: string
  disabled?: boolean
  slotProps?: AnyProps
  [key: string]: any
}

export function TextField({
  label,
  value,
  onChange,
  onKeyDown,
  type,
  multiline,
  minRows,
  helperText,
  error,
  select,
  children,
  fullWidth,
  placeholder,
  disabled,
  slotProps,
  ...props
}: TextFieldProps) {
  const common: AnyProps = {
    ...clean(props),
    ...(value !== undefined ? { value: value ?? '' } : {}),
    placeholder,
    disabled,
    status: error ? 'error' : undefined,
    style: { width: fullWidth === false ? undefined : '100%' },
  }

  let control: ReactNode

  if (select) {
    const options = Children.toArray(children)
      .filter(isValidElement)
      .map((child: any) => ({
        value: child.props.value,
        label: child.props.children,
      }))

    control = (
      <Select
        {...common}
        options={options}
        onChange={(next) => {
          onChange?.({
            target: { value: String(next) },
          } as unknown as InputEvent)
        }}
      />
    )
  } else if (multiline) {
    control = (
      <Input.TextArea
        {...common}
        autoSize={{ minRows: minRows ?? 3 }}
        onChange={onChange as any}
        onKeyDown={onKeyDown as any}
      />
    )
  } else if (type === 'password') {
    control = (
      <Input.Password
        {...common}
        onChange={onChange as any}
        onKeyDown={onKeyDown as any}
      />
    )
  } else {
    control = (
      <Input
        {...common}
        type={type}
        onChange={onChange as any}
        onKeyDown={onKeyDown as any}
        min={slotProps?.htmlInput?.min}
        step={slotProps?.htmlInput?.step}
      />
    )
  }

  return (
    <div style={{ width: fullWidth === false ? undefined : '100%' }}>
      {label ? (
        <div
          className="sakhtyar-field-label"
          style={{ marginBottom: 7, fontWeight: 700 }}
        >
          {label}
        </div>
      ) : null}
      {control}
      {helperText ? (
        <div
          style={{
            marginTop: 5,
            fontSize: 12,
            color: error ? '#DC2626' : '#667085',
          }}
        >
          {helperText}
        </div>
      ) : null}
    </div>
  )
}

export function DialogTitle({ children }: { children?: ReactNode }) {
  return <>{children}</>
}
;(DialogTitle as AnyProps).__dialogPart = 'title'

export function DialogContent({ children }: { children?: ReactNode }) {
  return <>{children}</>
}
;(DialogContent as AnyProps).__dialogPart = 'content'

export function DialogActions({
  children,
}: {
  children?: ReactNode
  sx?: any
}) {
  return <>{children}</>
}
;(DialogActions as AnyProps).__dialogPart = 'actions'

export function Dialog({
  open,
  onClose,
  children,
  maxWidth,
}: {
  open: boolean
  onClose?: () => void
  children?: ReactNode
  maxWidth?: string
  [key: string]: any
}) {
  let title: ReactNode = null
  let content: ReactNode = null
  let actions: ReactNode = null

  Children.forEach(children, (child: any) => {
    const part = child?.type?.__dialogPart
    if (part === 'title') title = child.props.children
    else if (part === 'content') content = child.props.children
    else if (part === 'actions') actions = child.props.children
  })

  return (
    <Modal
      open={open}
      onCancel={onClose}
      title={title}
      footer={
        actions ? (
          <div
            style={{
              display: 'flex',
              justifyContent: 'flex-end',
              gap: 8,
            }}
          >
            {actions}
          </div>
        ) : null
      }
      width={
        maxWidth === 'xs'
          ? 460
          : maxWidth === 'sm'
            ? 620
            : maxWidth === 'md'
              ? 820
              : 720
      }
      destroyOnHidden
    >
      {content}
    </Modal>
  )
}

export function Tab(_: {
  value: string
  label: ReactNode
  icon?: ReactNode
  [key: string]: any
}) {
  return null
}
;(Tab as AnyProps).__tab = true

export function Tabs({
  value,
  onChange,
  children,
  ...props
}: {
  value: string
  onChange?: (event: unknown, value: any) => void
  children?: ReactNode
  [key: string]: any
}) {
  const width = useViewport()
  const items = Children.toArray(children)
    .filter(isValidElement)
    .map((child: any) => ({
      key: String(child.props.value),
      label: (
        <span
          style={{
            display: 'inline-flex',
            alignItems: 'center',
            gap: 7,
            whiteSpace: 'nowrap',
          }}
        >
          {child.props.icon}
          {child.props.label}
        </span>
      ),
    }))

  return (
    <AntTabs
      activeKey={String(value)}
      onChange={(key) => onChange?.(undefined, key)}
      items={items}
      tabBarGutter={width < 576 ? 4 : 12}
      style={mergedStyle(props, width)}
    />
  )
}

export function Skeleton({
  height,
  ...props
}: {
  height?: number
  [key: string]: any
}) {
  const width = useViewport()
  return (
    <AntSkeleton.Node
      active
      style={{
        width: '100%',
        height: height ?? 120,
        ...mergedStyle(props, width),
      }}
    />
  )
}

export function AppBar({ children, ...props }: AnyProps) {
  const width = useViewport()
  return (
    <header
      {...clean(props)}
      style={mergedStyle(props, width, {
        position: 'sticky',
        top: 0,
        zIndex: 1000,
        background: 'rgba(255,255,255,.96)',
      })}
    >
      {children}
    </header>
  )
}

export function Toolbar({ children, ...props }: AnyProps) {
  const width = useViewport()
  return (
    <div
      {...clean(props)}
      style={mergedStyle(props, width, {
        display: 'flex',
        alignItems: 'center',
        minHeight: 64,
        paddingInline: width < 576 ? 14 : 22,
      })}
    >
      {children}
    </div>
  )
}

export function Container({ children, ...props }: AnyProps) {
  const width = useViewport()
  return (
    <main
      {...clean(props)}
      style={mergedStyle(props, width, {
        width: '100%',
        marginInline: 'auto',
      })}
    >
      {children}
    </main>
  )
}

export function BottomNavigation({
  children,
  value,
  onChange,
  ...props
}: {
  children?: ReactNode
  value?: string | false
  onChange?: (event: unknown, value: string) => void
  [key: string]: any
}) {
  const width = useViewport()
  return (
    <nav
      {...clean(props)}
      style={mergedStyle(props, width, {
        display: 'flex',
        justifyContent: 'space-around',
      })}
    >
      {Children.map(children, (child: any) =>
        isValidElement(child)
          ? cloneElement(child as any, {
              currentValue: value,
              parentOnChange: onChange,
            })
          : child,
      )}
    </nav>
  )
}

export function BottomNavigationAction({
  value,
  label,
  icon,
  currentValue,
  parentOnChange,
}: {
  value: string
  label: ReactNode
  icon?: ReactNode
  currentValue?: string | false
  parentOnChange?: (event: unknown, value: string) => void
}) {
  return (
    <AntButton
      type={currentValue === value ? 'primary' : 'text'}
      icon={icon}
      onClick={(event) => parentOnChange?.(event, value)}
    >
      {label}
    </AntButton>
  )
}

export function List({ children, ...props }: AnyProps) {
  const width = useViewport()
  return <div {...clean(props)} style={mergedStyle(props, width)}>{children}</div>
}

export function ListItem({
  children,
  secondaryAction,
  divider,
  ...props
}: AnyProps) {
  const width = useViewport()
  return (
    <div
      {...clean(props)}
      style={mergedStyle(props, width, {
        display: 'flex',
        justifyContent: 'space-between',
        alignItems: 'center',
        padding: '10px 12px',
        borderBottom: divider ? '1px solid #f0f0f0' : undefined,
      })}
    >
      <div>{children}</div>
      {secondaryAction}
    </div>
  )
}

export function ListItemButton({
  children,
  divider,
  onClick,
  ...props
}: AnyProps) {
  const width = useViewport()
  return (
    <div
      role="button"
      tabIndex={0}
      onClick={onClick}
      {...clean(props)}
      style={mergedStyle(props, width, {
        cursor: 'pointer',
        padding: '10px 12px',
        borderBottom: divider ? '1px solid #f0f0f0' : undefined,
      })}
    >
      {children}
    </div>
  )
}

export function ListItemText({ primary, secondary }: AnyProps) {
  return (
    <div>
      <div style={{ fontWeight: 700 }}>{primary}</div>
      {secondary ? (
        <div style={{ color: '#667085', fontSize: 12, marginTop: 4 }}>
          {secondary}
        </div>
      ) : null}
    </div>
  )
}

export function TableContainer({ children, ...props }: AnyProps) {
  const width = useViewport()
  return (
    <div
      {...clean(props)}
      style={mergedStyle(props, width, { overflowX: 'auto' })}
    >
      {children}
    </div>
  )
}

export function Table({ children, ...props }: AnyProps) {
  const width = useViewport()
  return (
    <table
      {...clean(props)}
      style={mergedStyle(props, width, {
        width: '100%',
        borderCollapse: 'collapse',
      })}
    >
      {children}
    </table>
  )
}

export function TableHead({ children }: AnyProps) {
  return <thead>{children}</thead>
}
export function TableBody({ children }: AnyProps) {
  return <tbody>{children}</tbody>
}
export function TableRow({ children, ...props }: AnyProps) {
  return <tr {...clean(props)}>{children}</tr>
}

export function TableCell({ children, align, colSpan, ...props }: AnyProps) {
  const width = useViewport()
  return (
    <td
      colSpan={colSpan}
      style={mergedStyle(props, width, {
        padding: '12px 10px',
        borderBottom: '1px solid #f0f0f0',
        textAlign: align === 'left' ? 'left' : undefined,
      })}
    >
      {children}
    </td>
  )
}

export function useTheme() {
  return {
    breakpoints: {
      down: (key: string) =>
        key === 'sm' ? '(max-width: 575px)' : '(max-width: 767px)',
    },
  }
}

export function useMediaQuery(query: string) {
  const [match, setMatch] = useState(() =>
    typeof window !== 'undefined' && window.matchMedia(query).matches,
  )

  useEffect(() => {
    const media = window.matchMedia(query)
    const fn = () => setMatch(media.matches)
    media.addEventListener?.('change', fn)
    return () => media.removeEventListener?.('change', fn)
  }, [query])

  return match
}
