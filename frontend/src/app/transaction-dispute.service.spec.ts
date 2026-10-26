import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { TransactionDisputeService } from './transaction-dispute.service';

describe('TransactionDisputeService', () => {
  let service: TransactionDisputeService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule],
      providers: [TransactionDisputeService],
    });

    service = TestBed.inject(TransactionDisputeService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('extracts the content array from paginated backend responses', () => {
    service.setToken('abc');

    let transactions: any[] | undefined;
    service.getTransactions().subscribe((result) => {
      transactions = result;
    });

    const request = httpMock.expectOne('/api/v1/transactions');
    expect(request.request.headers.get('Authorization')).toBe('Bearer abc');

    request.flush({
      content: [
        { id: 1, transactionReference: 'TX-1', accountNumber: 'ACC-1001', amount: -50, currency: 'ZAR', type: 'DEBIT', merchantName: 'Acme', status: 'SETTLED', transactionDate: '2026-10-01T00:00:00' },
      ],
      totalElements: 1,
    });

    expect(transactions).toEqual([
      { id: 1, transactionReference: 'TX-1', accountNumber: 'ACC-1001', amount: -50, currency: 'ZAR', type: 'DEBIT', merchantName: 'Acme', status: 'SETTLED', transactionDate: '2026-10-01T00:00:00' },
    ]);
  });

  it('adds an idempotency key to dispute creation requests', () => {
    service.setToken('abc');

    const payload = {
      transactionReference: 'TX-1',
      accountNumber: 'ACC-1001',
      disputedAmount: 100,
      reasonCode: 'MERCHANT_ERROR',
      disputeType: 'CHARGEBACK',
      description: 'Issue',
    };

    service.createDispute(payload as any).subscribe((result) => {
      expect(result).toEqual({
        id: 7,
        disputeId: 'DSP-1',
        transactionReference: 'TX-1',
        accountNumber: 'ACC-1001',
        disputedAmount: 100,
        reasonCode: 'MERCHANT_ERROR',
        status: 'OPEN',
      });
    });

    const request = httpMock.expectOne('/api/v1/disputes');
    expect(request.request.headers.get('Authorization')).toBe('Bearer abc');
    expect(request.request.headers.get('X-Idempotency-Key')).toBeTruthy();

    request.flush({
      id: 7,
      disputeId: 'DSP-1',
      transactionReference: 'TX-1',
      accountNumber: 'ACC-1001',
      disputedAmount: 100,
      reasonCode: 'MERCHANT_ERROR',
      status: 'OPEN',
    });
  });
});
