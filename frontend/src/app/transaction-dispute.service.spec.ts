import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { TransactionDisputeService } from './transaction-dispute.service';

describe('TransactionDisputeService', () => {
  let service: TransactionDisputeService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    service = TestBed.inject(TransactionDisputeService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('extracts the content array from paginated responses and sends the bearer token', () => {
    service.setToken('abc');
    let result: unknown;
    service.getTransactions().subscribe((value) => (result = value));

    const request = httpMock.expectOne('/api/v1/transactions');
    expect(request.request.headers.get('Authorization')).toBe('Bearer abc');
    request.flush({ content: [{ id: 1 }], totalElements: 1 });

    expect(result).toEqual([{ id: 1 }]);
  });

  it('sends no Authorization header after the token is cleared', () => {
    service.setToken('abc');
    service.clearToken();
    service.getDisputes().subscribe();

    const request = httpMock.expectOne('/api/v1/disputes');
    expect(request.request.headers.has('Authorization')).toBe(false);
    request.flush({ content: [] });
  });

  it('adds a deterministic idempotency key to dispute creation', () => {
    const payload = {
      transactionReference: 'TX-1',
      accountNumber: 'ACC-1001',
      disputedAmount: 100,
      reasonCode: 'MERCHANT_ERROR',
      disputeType: 'CHARGEBACK' as const,
      description: 'Issue',
    };

    service.createDispute(payload).subscribe();
    service.createDispute(payload).subscribe();

    const [first, second] = httpMock.match('/api/v1/disputes');
    const key = first.request.headers.get('X-Idempotency-Key');
    expect(key).toMatch(/^create-dispute-[0-9a-f]{8}$/);
    expect(second.request.headers.get('X-Idempotency-Key')).toBe(key);
    first.flush({});
    second.flush({});
  });

  it('keys reversals by dispute and reason', () => {
    service.reverseDispute(7, 'FRAUD').subscribe();
    service.reverseDispute(7, 'DUPLICATE_DEBIT').subscribe();

    const [fraud, duplicate] = httpMock.match('/api/v1/disputes/7/reverse');
    expect(fraud.request.body).toEqual({ reasonCode: 'FRAUD' });
    expect(fraud.request.headers.get('X-Idempotency-Key')).not.toBe(duplicate.request.headers.get('X-Idempotency-Key'));
    fraud.flush({});
    duplicate.flush({});
  });

  it('retries a failed read once before surfacing the error', () => {
    vi.useFakeTimers();
    let failed = false;
    service.getProfile().subscribe({ error: () => (failed = true) });

    httpMock.expectOne('/api/v1/profile').flush('boom', { status: 500, statusText: 'Server Error' });
    vi.advanceTimersByTime(250);
    httpMock.expectOne('/api/v1/profile').flush('boom', { status: 500, statusText: 'Server Error' });

    expect(failed).toBe(true);
    vi.useRealTimers();
  });
});
