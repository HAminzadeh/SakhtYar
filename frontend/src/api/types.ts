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

export type PropertyItem = {
  id: string
  caseId: string
  province?: string | null
  city?: string | null
  district?: string | null
  neighborhood?: string | null
  address?: string | null
  landAreaM2?: number | null
  registryMainNo?: string | null
  registrySubNo?: string | null
  registrySection?: string | null
  postalCode?: string | null
  latitude?: number | null
  longitude?: number | null
  createdAt: string
  updatedAt: string
}

export type OwnerItem = {
  id: string
  propertyId: string
  firstName: string
  lastName: string
  nationalId?: string | null
  mobile?: string | null
  ownershipNumerator: number
  ownershipDenominator: number
  primaryContact: boolean
  createdAt: string
  updatedAt: string
}
