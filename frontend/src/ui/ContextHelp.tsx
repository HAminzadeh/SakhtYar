import { InfoCircleOutlined, ReadOutlined } from '@ant-design/icons'
import { Button, Popover, Typography } from 'antd'
import { useNavigate } from 'react-router-dom'
import { helpTopic } from './helpTopics'

export function ContextHelp({ topic, label = 'راهنمای این بخش' }: { topic: string; label?: string }) {
  const navigate = useNavigate()
  const guide = helpTopic(topic)
  if (!guide) return null
  return <Popover trigger="click" placement="bottomRight" content={
    <div style={{ maxWidth: 350, direction: 'rtl', textAlign: 'right' }}>
      <Typography.Text strong>{guide.title}</Typography.Text>
      <Typography.Paragraph style={{ marginTop: 8 }}>{guide.summary}</Typography.Paragraph>
      <Typography.Text type="secondary">{guide.steps[0]?.detail}</Typography.Text>
      <div style={{ marginTop: 12 }}><Button type="link" icon={<ReadOutlined />}
        onClick={() => navigate(`/help?topic=${encodeURIComponent(guide.key)}`)}>
        مشاهده راهنمای کامل و مرحله‌به‌مرحله
      </Button></div>
    </div>
  }>
    <Button type="default" icon={<InfoCircleOutlined />} aria-label={label}>{label}</Button>
  </Popover>
}
