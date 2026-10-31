export class ArnoldApiError extends Error {
  constructor(message, { status, errorCode, details, cause } = {}) {
    super(message, { cause });
    this.name = 'ArnoldApiError';
    this.status = status;
    this.errorCode = errorCode;
    this.details = details;
  }
}
