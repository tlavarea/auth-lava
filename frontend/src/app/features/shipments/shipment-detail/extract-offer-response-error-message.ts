export function extractOfferResponseErrorMessage(error: unknown): string {
  if (error && typeof error === 'object' && 'status' in error && (error as { status: number }).status === 501) {
    return "This feature isn't available yet.";
  }
  return 'Something went wrong submitting your response. Please try again.';
}
