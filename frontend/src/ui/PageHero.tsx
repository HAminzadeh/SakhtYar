import type { ReactNode } from 'react'
import { Typography } from 'antd'

export function PageHero({
  image,
  title,
  subtitle,
  icon,
}: {
  image: string
  title: string
  subtitle: string
  icon: ReactNode
}) {
  return (
    <section className="sakhtyar-split-hero">
      <div
        className="sakhtyar-split-hero-media"
        style={{ backgroundImage: `url("${image}")` }}
      />
      <div className="sakhtyar-split-hero-info">
        <span className="sakhtyar-split-hero-icon">{icon}</span>
        <div>
          <Typography.Title level={1}>{title}</Typography.Title>
          <Typography.Paragraph>{subtitle}</Typography.Paragraph>
        </div>
      </div>
    </section>
  )
}
