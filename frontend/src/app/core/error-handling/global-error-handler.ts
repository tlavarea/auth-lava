import { ErrorHandler, Injectable } from '@angular/core';

// Single seam for uncaught errors (component/template throws, plus window 'error' and
// 'unhandledrejection' via provideBrowserGlobalErrorListeners) - swap the console.error below for
// a real APM SDK call here if one gets added later.
@Injectable()
export class GlobalErrorHandler implements ErrorHandler {
  handleError(error: unknown): void {
    console.error('[GlobalErrorHandler]', {
      message: error instanceof Error ? error.message : String(error),
      stack: error instanceof Error ? error.stack : undefined,
      url: location.href,
      timestamp: new Date().toISOString(),
    });
  }
}
