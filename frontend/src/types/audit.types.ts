export type ActorType = 'USER' | 'SYSTEM'

export interface AuditRecord {
  id: string
  entityType: string
  entityId: string
  previousState?: string
  newState: string
  actorId?: string
  actorType: ActorType
  eventTimestamp: string
  metadata?: string
}