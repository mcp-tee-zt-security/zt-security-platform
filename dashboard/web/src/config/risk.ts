export const RISK_THRESHOLDS = {
  critical: 85,
  high: 65,
  medium: 40,
} as const;

export const DASHBOARD_REFRESH_INTERVAL_MS = 5_000;
export const DATA_PLANE_REFRESH_INTERVAL_MS = 3_000;
export const DATA_PLANE_URL = import.meta.env.VITE_DATA_PLANE_URL || 'http://localhost:8091';
export const PRIVATE_LLM_DEFAULT_URL =
  import.meta.env.VITE_PRIVATE_LLM_URL || 'http://localhost:8000';
export const POLICY_EXAMPLE_AMOUNT = 10_000_000;
