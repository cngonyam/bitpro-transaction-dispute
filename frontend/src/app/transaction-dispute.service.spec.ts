import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { App } from './app';
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

  it('assigns the agent role immediately after an agent login so reversal is allowed', () => {
    const api = {
      clearToken: jasmine.createSpy('clearToken'),
      setToken: jasmine.createSpy('setToken'),
      login: jasmine.createSpy('login').and.returnValue(of({ token: 'abc', username: 'agent-user' })),
      getProfile: jasmine.createSpy('getProfile').and.returnValue(of({
        fullName: 'Agent User',
        role: 'AGENT',
        email: 'agent-user@bitpro.co.za',
        phone: '+27 11 111 1111',
        department: 'Customer Operations',
        region: 'Johannesburg, South Africa',
      })),
      getTransactions: jasmine.createSpy('getTransactions').and.returnValue(of([])),
      getDisputes: jasmine.createSpy('getDisputes').and.returnValue(of([])),
      getAccounts: jasmine.createSpy('getAccounts').and.returnValue(of([])),
      reverseDispute: jasmine.createSpy('reverseDispute'),
    } as any;

    const app = new App(api, { detectChanges: jasmine.createSpy('detectChanges') } as any);
    app.loginForm = { username: 'agent-user', password: 'agent-pass123' };

    app.login();

    expect(app.profile.role).toBe('AGENT');
    expect(app.isLoggedIn).toBeTrue();
  });
});
