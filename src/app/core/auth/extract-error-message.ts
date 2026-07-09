import { HttpErrorResponse } from '@angular/common/http';

export function extractErrorMessage(error: unknown): string {
  if (error instanceof HttpErrorResponse) {
    const body = error.error as { error?: string } | null;
    if (body?.error) {
      return body.error;
    }
  }
  return 'Something went wrong. Please try again.';
}
