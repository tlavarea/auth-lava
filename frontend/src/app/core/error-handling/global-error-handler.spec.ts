import { GlobalErrorHandler } from './global-error-handler';

describe('GlobalErrorHandler', () => {
  let handler: GlobalErrorHandler;
  let consoleErrorSpy: ReturnType<typeof vi.spyOn>;

  beforeEach(() => {
    handler = new GlobalErrorHandler();
    consoleErrorSpy = vi.spyOn(console, 'error').mockImplementation(() => undefined);
  });

  afterEach(() => {
    consoleErrorSpy.mockRestore();
  });

  it('logs message and stack for an Error instance', () => {
    const error = new Error('boom');

    handler.handleError(error);

    expect(consoleErrorSpy).toHaveBeenCalledWith(
      '[GlobalErrorHandler]',
      expect.objectContaining({ message: 'boom', stack: error.stack })
    );
  });

  it('logs a stringified message for a non-Error value', () => {
    handler.handleError('something went wrong');

    expect(consoleErrorSpy).toHaveBeenCalledWith(
      '[GlobalErrorHandler]',
      expect.objectContaining({ message: 'something went wrong', stack: undefined })
    );
  });
});
