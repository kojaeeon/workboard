export interface QueryWorkItem {
  table: string | null
  service: string | null
  serviceNm: string | null
  package_path: string | null
}
export interface QueryResult {
  id: string
  title: string
  databaseType: string
  columns: {
    key: string
    label: string
    jdbcType: number
    typeName: string
    nullable: boolean
    precision: number
    scale: number
  }[]
  rows: Record<string, unknown>[]
  truncated: boolean
  elapsedMs: number
  workItem: QueryWorkItem
  codegen: { mode: string; mapping: Record<string, string> }
}
