export class ZtSecurityError extends Error {
  constructor(message: string) {
    super(message);
    this.name = "ZtSecurityError";
  }
}

export class ZtSecurityHttpError extends ZtSecurityError {
  constructor(
    public readonly status: number,
    message: string,
    public readonly requestId?: string,
  ) {
    super(`HTTP ${status}: ${message}`);
  }
}
