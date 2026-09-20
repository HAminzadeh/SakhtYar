export type Permission =
  | 'CASE_READ'
  | 'CASE_WRITE'
  | 'PROPERTY_READ'
  | 'PROPERTY_WRITE'
  | 'OWNER_READ'
  | 'OWNER_WRITE'
  | 'DOCUMENT_READ'
  | 'DOCUMENT_WRITE'
  | 'AGENT_USE'
  | 'GLOSSARY_MANAGE'
  | 'USER_MANAGE'
  | 'AUDIT_READ'

export type UserRole =
  | 'ADMIN'
  | 'PROJECT_MANAGER'
  | 'ANALYST'
  | 'LEGAL_EXPERT'
  | 'READ_ONLY'

export type UserStatus = 'PENDING' | 'ACTIVE' | 'SUSPENDED'

export type Me = {
  id: string
  username: string
  displayName: string
  email?: string | null
  mobile?: string | null
  role: UserRole
  status: UserStatus
  permissions: Permission[]
  lastLoginAt?: string | null
}

export type UserAdminItem = {
  id: string
  username: string
  displayName: string
  email?: string | null
  mobile?: string | null
  role: UserRole
  status: UserStatus
  permissions: Permission[]
  failedLoginAttempts: number
  lockedUntil?: string | null
  lastLoginAt?: string | null
  passwordChangedAt: string
  createdAt: string
  updatedAt: string
}

export type AuthSessionItem = {
  id: string
  clientType: 'WEB' | 'MOBILE' | string
  deviceName?: string | null
  userAgent?: string | null
  ipAddress?: string | null
  createdAt: string
  expiresAt: string
  lastUsedAt: string
  revokedAt?: string | null
  active: boolean
}

export type RegistrationResponse = {
  id: string
  username: string
  displayName: string
  status: UserStatus
  message: string
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
  frontageM?: number | null
  passageWidthM?: number | null
  buildingAreaM2?: number | null
  constructionYear?: number | null
  existingFloors?: number | null
  existingUnits?: number | null
  orientation?: string | null
  propertyType?: string | null
  buildingCondition?: string | null
  registryMainNo?: string | null
  registrySubNo?: string | null
  registrySection?: string | null
  postalCode?: string | null
  latitude?: number | null
  longitude?: number | null
  attributesSchemaVersion: string
  attributes: Record<string, unknown>
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

export type GeoSearchResult = {
  title?: string | null
  address?: string | null
  neighborhood?: string | null
  city?: string | null
  category?: string | null
  latitude: number
  longitude: number
}

export type ReverseGeocodeResult = {
  formattedAddress?: string | null
  province?: string | null
  city?: string | null
  district?: string | null
  neighborhood?: string | null
  routeName?: string | null
  place?: string | null
}

export type GeoStatus = {
  providerAvailable: boolean
  spatialDatabase: string
}

export type NearbyProperty = {
  caseId: string
  caseTitle: string
  address?: string | null
  latitude: number
  longitude: number
  distanceMeters: number
}

export type AgentStatus =
  | 'SUCCESS'
  | 'PARTIAL'
  | 'NEEDS_INPUT'
  | 'FAILED'

export type AgentResult = {
  agent: string
  status: AgentStatus
  message: string
  data: Record<string, unknown>
  warnings: string[]
  missingFields: string[]
  confidence: number
  completedAt: string
}

export type AgentWorkflowResult = {
  schemaVersion: string
  requestId: string
  conversationId: string
  caseId?: string | null
  intent: string
  workflow: string
  status: AgentStatus
  message: string
  inputs: Record<string, unknown>
  assumptions: string[]
  results: Record<string, AgentResult>
  missingFields: string[]
}

export type AgentMessage = {
  id: string
  conversationId: string
  role: 'USER' | 'ASSISTANT' | string
  content: string
  payloadText?: string | null
  createdAt: string
}

export type GlossaryEntry = {
  id: string
  term: string
  normalizedTerm: string
  meaning: string
  aliases: string[]
  status: 'DRAFT' | 'APPROVED' | 'REJECTED'
  createdBy?: string | null
  approvedBy?: string | null
  createdAt?: string | null
  updatedAt?: string | null
}
