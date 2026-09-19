export type Me = {
  username: string
  displayName: string
  role: string
}

export type CaseStatus = 'DRAFT' | 'ACTIVE' | 'ARCHIVED'

export type CaseItem = {
  id: string
  title: string
  status: CaseStatus
  description?: string | null
  city?: string | null
  district?: string | null
  address?: string | null
  landAreaM2?: number | null
  createdBy: string
  createdAt: string
  updatedAt: string
}

export type DocumentItem = {
  id: string
  caseId: string
  originalFilename: string
  contentType?: string | null
  sizeBytes: number
  sha256: string
  uploadedBy: string
  uploadedAt: string
}
