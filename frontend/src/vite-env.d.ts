/// <reference types="vite/client" />

interface ImportMetaEnv {
  readonly VITE_NESHAN_MAP_API_KEY?: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}
