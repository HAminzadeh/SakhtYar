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

const unit = (value: any) => typeof value === 'number' ? value * 8 : value

function pickResponsive(value: any) {
  if (value == null || typeof value !== 'object' || Array.isArray(value)) return value
  const width = typeof window === 'undefined' ? 1200 : window.innerWidth
  if (width >= 1200 && value.xl != null) return value.xl
  if (width >= 992 && value.lg != null) return value.lg
  if (width >= 768 && value.md != null) return value.md
  if (width >= 576 && value.sm != null) return value.sm
  return value.xs ?? value.sm ?? value.md ?? value.lg ?? value.xl
}

function sxToStyle(sx: any): React.CSSProperties {
  if (!sx || typeof sx !== 'object' || Array.isArray(sx)) return {}
  const s: AnyProps = { ...sx }
  const out: AnyProps = {}
  const direct = [
    'width','height','minWidth','maxWidth','minHeight','maxHeight','display',
    'position','top','right','bottom','left','zIndex','overflow','overflowX','overflowY',
    'opacity','cursor','textAlign','flex','flexGrow','flexShrink','flexBasis','flexWrap',
    'alignItems','alignSelf','justifyContent','placeItems','gap','rowGap','columnGap',
    'fontSize','fontWeight','lineHeight','letterSpacing','whiteSpace','background',
    'backgroundColor','color','border','borderTop','borderBottom','borderLeft','borderRight',
    'borderColor','borderRadius','boxShadow','backdropFilter','transition',
  ]
  for (const key of direct) {
    if (s[key] != null && typeof s[key] !== 'object') out[key] = pickResponsive(s[key])
  }
  if (s.bgcolor != null) out.backgroundColor = pickResponsive(s.bgcolor)
  if (s.p != null) out.padding = unit(pickResponsive(s.p))
  if (s.pt != null) out.paddingTop = unit(pickResponsive(s.pt))
  if (s.pb != null) out.paddingBottom = unit(pickResponsive(s.pb))
  if (s.pl != null) out.paddingLeft = unit(pickResponsive(s.pl))
  if (s.pr != null) out.paddingRight = unit(pickResponsive(s.pr))
  if (s.px != null) out.paddingInline = unit(pickResponsive(s.px))
  if (s.py != null) out.paddingBlock = unit(pickResponsive(s.py))
  if (s.m != null) out.margin = unit(pickResponsive(s.m))
  if (s.mt != null) out.marginTop = unit(pickResponsive(s.mt))
  if (s.mb != null) out.marginBottom = unit(pickResponsive(s.mb))
  if (s.ml != null) out.marginLeft = unit(pickResponsive(s.ml))
  if (s.mr != null) out.marginRight = unit(pickResponsive(s.mr))
  if (s.mx != null) out.marginInline = unit(pickResponsive(s.mx))
  if (s.my != null) out.marginBlock = unit(pickResponsive(s.my))
  return out
}

function mergeStyle(props: AnyProps, extra?: React.CSSProperties) {
  return { ...sxToStyle(props.sx), ...extra, ...(props.style || {}) }
}

function clean(props: AnyProps) {
  const {
    sx, spacing, direction, alignItems, justifyContent, gap, bgcolor,
    mt, mb, mx, my, p, px, py, pt, pb, component, fullWidth,
    startIcon, endIcon, severity, variant, gutterBottom, fontWeight,
    color, display, borderTop, borderColor, size, container, item,
    ...rest
  } = props
  return rest
}

function useViewport() {
  const [width, setWidth] = useState(() =>
    typeof window === 'undefined' ? 1200 : window.innerWidth,
  )
  useEffect(() => {
    const fn = () => setWidth(window.innerWidth)
    window.addEventListener('resize', fn)
    return () => window.removeEventListener('resize', fn)
  }, [])
  return width
}

export function Box({ component: Component = 'div', children, ...props }: AnyProps) {
  return <Component {...clean(props)} style={mergeStyle(props)}>{children}</Component>
}

export function Stack({
  children,
  direction = 'column',
  spacing,
  gap,
  alignItems,
  justifyContent,
  ...props
}: AnyProps) {
  const width = useViewport()
  const resolve = (v: any) => {
    if (!v || typeof v !== 'object' || Array.isArray(v)) return v
    if (width >= 1200 && v.xl != null) return v.xl
    if (width >= 992 && v.lg != null) return v.lg
    if (width >= 768 && v.md != null) return v.md
    if (width >= 576 && v.sm != null) return v.sm
    return v.xs ?? 'column'
  }

  return (
    <div
      {...clean(props)}
      style={mergeStyle(props, {
        display: 'flex',
        flexDirection: resolve(direction),
        gap: gap != null ? unit(resolve(gap)) : spacing != null ? unit(resolve(spacing)) : undefined,
        alignItems: resolve(alignItems),
        justifyContent: resolve(justifyContent),
      })}
    >
      {children}
    </div>
  )
}

export function Grid({ children, container, spacing, size, ...props }: AnyProps) {
  const width = useViewport()
  const resolveSize = () => {
    if (typeof size === 'number') return size
    if (!size) return 12
    if (width >= 1200 && size.xl != null) return size.xl
    if (width >= 992 && size.lg != null) return size.lg
    if (width >= 768 && size.md != null) return size.md
    if (width >= 576 && size.sm != null) return size.sm
    return size.xs ?? 12
  }

  if (container) {
    return (
      <div
        {...clean(props)}
        style={mergeStyle(props, {
          display: 'flex',
          flexWrap: 'wrap',
          gap: unit(pickResponsive(spacing ?? 0)),
        })}
      >
        {children}
      </div>
    )
  }

  const n = resolveSize()
  return (
    <div
      {...clean(props)}
      style={mergeStyle(props, {
        flex: `0 0 calc(${(n / 12) * 100}% - 12px)`,
        maxWidth: `calc(${(n / 12) * 100}% - 12px)`,
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
  const style = mergeStyle(props, {
    color: color === 'text.secondary' ? '#667085' : undefined,
    fontWeight,
    marginBottom: gutterBottom ? 8 : undefined,
  })

  if (component) {
    const C = component
    return <C {...clean(props)} style={style}>{children}</C>
  }
  if (variant === 'h4') return <AntTypography.Title level={2} style={style}>{children}</AntTypography.Title>
  if (variant === 'h5') return <AntTypography.Title level={3} style={style}>{children}</AntTypography.Title>
  if (variant === 'h6') return <AntTypography.Title level={5} style={style}>{children}</AntTypography.Title>
  if (variant === 'caption') return <AntTypography.Text type="secondary" style={{ fontSize: 12, ...style }}>{children}</AntTypography.Text>
  return <AntTypography.Text style={style}>{children}</AntTypography.Text>
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
  ...props
}: AnyProps) {
  const button = (
    <AntButton
      {...clean(props)}
      danger={color === 'error'}
      type={variant === 'contained' ? 'primary' : variant === 'text' ? 'text' : 'default'}
      size={size === 'large' ? 'large' : size === 'small' ? 'small' : 'middle'}
      icon={startIcon}
      style={mergeStyle(props, fullWidth ? { width: '100%' } : undefined)}
    >
      {children}
      {endIcon ? <span style={{ marginInlineStart: 6 }}>{endIcon}</span> : null}
    </AntButton>
  )

  if (Component) {
    return <Component to={to} style={{ textDecoration: 'none' }}>{button}</Component>
  }

  return button
}

export function IconButton({ children, color, ...props }: AnyProps) {
  return (
    <AntButton
      {...clean(props)}
      danger={color === 'error'}
      type="text"
      shape="circle"
      icon={children}
      style={mergeStyle(props)}
    />
  )
}

export function Card({ children, variant, ...props }: AnyProps) {
  return (
    <AntCard
      {...clean(props)}
      bordered={variant === 'outlined' || props.bordered !== false}
      style={mergeStyle(props)}
    >
      {children}
    </AntCard>
  )
}

export function CardContent({ children, ...props }: AnyProps) {
  return <div {...clean(props)} style={mergeStyle(props)}>{children}</div>
}

export function CardActionArea({ children, onClick, ...props }: AnyProps) {
  return (
    <div
      role="button"
      tabIndex={0}
      onClick={onClick}
      {...clean(props)}
      style={mergeStyle(props, { cursor: 'pointer' })}
    >
      {children}
    </div>
  )
}

export function Paper({ children, ...props }: AnyProps) {
  return <AntCard {...clean(props)} style={mergeStyle(props)}>{children}</AntCard>
}

export function Alert({ severity = 'info', children, onClose, ...props }: AnyProps) {
  const type =
    severity === 'error' ? 'error' :
    severity === 'warning' ? 'warning' :
    severity === 'success' ? 'success' : 'info'

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
  const antColor =
    color === 'success' ? 'success' :
    color === 'warning' ? 'warning' :
    color === 'error' ? 'error' :
    color === 'primary' ? 'blue' : undefined

  return (
    <Tag
      {...clean(props)}
      color={antColor}
      icon={icon}
      onClick={onClick}
      style={mergeStyle(props, onClick ? { cursor: 'pointer' } : undefined)}
    >
      {label}
    </Tag>
  )
}

export function CircularProgress({ size = 24 }: { size?: number }) {
  return <Spin size={size >= 32 ? 'large' : 'small'} />
}

export function LinearProgress({ value = 0, ...props }: AnyProps) {
  return <Progress percent={value} showInfo={false} strokeLinecap="round" style={mergeStyle(props)} />
}

export function Divider(props: AnyProps) {
  return <AntDivider {...clean(props)} style={mergeStyle(props)} />
}

export function Avatar({ children, ...props }: AnyProps) {
  return <AntAvatar {...clean(props)} style={mergeStyle(props)}>{children}</AntAvatar>
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
  return <AntCheckbox {...clean(props)} checked={checked} onChange={onChange as any} />
}

export function FormControlLabel({ control, label, ...props }: AnyProps) {
  return (
    <label
      {...clean(props)}
      style={mergeStyle(props, { display: 'flex', gap: 8, alignItems: 'center' })}
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
    value: value ?? '',
    placeholder,
    disabled,
    status: error ? 'error' : undefined,
    style: fullWidth === false ? undefined : { width: '100%' },
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
    <div style={mergeStyle(props, { width: fullWidth === false ? undefined : '100%' })}>
      {label ? <div style={{ marginBottom: 6, fontSize: 13, fontWeight: 600 }}>{label}</div> : null}
      {control}
      {helperText ? (
        <div style={{ marginTop: 4, fontSize: 12, color: error ? '#ff4d4f' : '#667085' }}>
          {helperText}
        </div>
      ) : null}
    </div>
  )
}

export function DialogTitle({ children }: { children?: ReactNode }) { return <>{children}</> }
;(DialogTitle as AnyProps).__dialogPart = 'title'

export function DialogContent({ children }: { children?: ReactNode }) { return <>{children}</> }
;(DialogContent as AnyProps).__dialogPart = 'content'

export function DialogActions({ children }: { children?: ReactNode; sx?: any }) { return <>{children}</> }
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
        actions
          ? <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 8 }}>{actions}</div>
          : null
      }
      width={maxWidth === 'sm' ? 620 : maxWidth === 'md' ? 820 : 720}
      destroyOnHidden
    >
      {content}
    </Modal>
  )
}

export function Tab(_: { value: string; label: ReactNode; icon?: ReactNode; [key: string]: any }) {
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
  const items = Children.toArray(children)
    .filter(isValidElement)
    .map((child: any) => ({
      key: String(child.props.value),
      label: (
        <span>
          {child.props.icon ? <span style={{ marginInlineEnd: 6 }}>{child.props.icon}</span> : null}
          {child.props.label}
        </span>
      ),
    }))

  return (
    <AntTabs
      activeKey={String(value)}
      onChange={(key) => onChange?.(undefined, key)}
      items={items}
      {...clean(props)}
    />
  )
}

export function Skeleton({ height, ...props }: { height?: number; [key: string]: any }) {
  return <AntSkeleton.Node active style={{ width: '100%', height: height ?? 120, ...mergeStyle(props) }} />
}

export function AppBar({ children, ...props }: AnyProps) {
  return (
    <header
      {...clean(props)}
      style={mergeStyle(props, {
        position: 'sticky',
        top: 0,
        zIndex: 1000,
        background: '#fff',
      })}
    >
      {children}
    </header>
  )
}

export function Toolbar({ children, ...props }: AnyProps) {
  return (
    <div
      {...clean(props)}
      style={mergeStyle(props, {
        display: 'flex',
        alignItems: 'center',
        minHeight: 64,
        paddingInline: 20,
      })}
    >
      {children}
    </div>
  )
}

export function Container({ children, ...props }: AnyProps) {
  return (
    <main
      {...clean(props)}
      style={mergeStyle(props, {
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
  return (
    <nav
      {...clean(props)}
      style={mergeStyle(props, {
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
  return <div {...clean(props)} style={mergeStyle(props)}>{children}</div>
}

export function ListItem({ children, secondaryAction, divider, ...props }: AnyProps) {
  return (
    <div
      {...clean(props)}
      style={mergeStyle(props, {
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

export function ListItemButton({ children, divider, onClick, ...props }: AnyProps) {
  return (
    <div
      role="button"
      tabIndex={0}
      onClick={onClick}
      {...clean(props)}
      style={mergeStyle(props, {
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
      <div style={{ fontWeight: 600 }}>{primary}</div>
      {secondary ? <div style={{ color: '#667085', fontSize: 12, marginTop: 4 }}>{secondary}</div> : null}
    </div>
  )
}

export function TableContainer({ children, ...props }: AnyProps) {
  return <div {...clean(props)} style={mergeStyle(props, { overflowX: 'auto' })}>{children}</div>
}

export function Table({ children, ...props }: AnyProps) {
  return (
    <table
      {...clean(props)}
      style={mergeStyle(props, {
        width: '100%',
        borderCollapse: 'collapse',
      })}
    >
      {children}
    </table>
  )
}

export function TableHead({ children }: AnyProps) { return <thead>{children}</thead> }
export function TableBody({ children }: AnyProps) { return <tbody>{children}</tbody> }
export function TableRow({ children, ...props }: AnyProps) { return <tr {...clean(props)}>{children}</tr> }

export function TableCell({ children, align, colSpan, ...props }: AnyProps) {
  return (
    <td
      colSpan={colSpan}
      style={mergeStyle(props, {
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