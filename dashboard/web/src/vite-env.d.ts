/// <reference types="vite/client" />

interface ImportMetaEnv {
  readonly VITE_API_URL?: string;
  readonly VITE_TENANT_ID?: string;
  readonly VITE_API_KEY?: string;
  readonly VITE_WORKSPACE_ID?: string;
  readonly VITE_DATA_PLANE_URL?: string;
  readonly VITE_PRIVATE_LLM_URL?: string;
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}
