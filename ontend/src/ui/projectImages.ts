import type { CaseItem } from '../api/types'

export const PROJECT_IMAGES = [
  '/assets/sakhtyar/projects/project-01.jpg',
  '/assets/sakhtyar/projects/project-02.jpg',
  '/assets/sakhtyar/projects/project-03.jpg',
  '/assets/sakhtyar/projects/project-04.jpg',
  '/assets/sakhtyar/projects/project-05.jpg',
  '/assets/sakhtyar/projects/project-06.jpg',
  '/assets/sakhtyar/projects/project-07.jpg',
  '/assets/sakhtyar/projects/project-08.jpg',
  '/assets/sakhtyar/projects/project-09.jpg',
  '/assets/sakhtyar/projects/project-10.jpg',
] as const

function seededIndex(id: string) {
  const marker = '10000000-0000-0000-0000-0000000000'
  if (!id.startsWith(marker)) return null

  const suffix = Number(id.slice(-2))
  if (!Number.isFinite(suffix) || suffix < 1) return null

  return (suffix - 1) % PROJECT_IMAGES.length
}

export function projectImage(
  item: Pick<CaseItem, 'id' | 'coverImageUrl'>,
  fallbackIndex = 0,
) {
  const seedIndex = seededIndex(item.id)
  if (seedIndex != null) return PROJECT_IMAGES[seedIndex]

  if (item.coverImageUrl?.trim()) {
    return item.coverImageUrl.trim()
  }

  return PROJECT_IMAGES[
    Math.abs(fallbackIndex) % PROJECT_IMAGES.length
  ]
}
