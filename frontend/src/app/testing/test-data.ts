import { of } from 'rxjs';
import { Dispute, Transaction, TransactionDisputeService, UserProfile } from '../transaction-dispute.service';

export const debitTransaction: Transaction = {
  id: 1,
  transactionReference: 'TX-1',
  accountNumber: 'ACC-1001',
  amount: 100,
  currency: 'ZAR',
  type: 'DEBIT',
  merchantName: 'Acme',
  status: 'SETTLED',
  transactionDate: '2026-10-01T00:00:00',
};

export const creditTransaction: Transaction = {
  ...debitTransaction,
  id: 2,
  transactionReference: 'TX-2',
  type: 'CREDIT',
  amount: 40,
};

export const reversedTransaction: Transaction = {
  ...debitTransaction,
  id: 3,
  transactionReference: 'TX-3',
  status: 'REVERSED',
};

export function dispute(overrides: Partial<Dispute> = {}): Dispute {
  return {
    id: 7,
    disputeId: 'DSP-7',
    transactionReference: 'TX-1',
    accountNumber: 'ACC-1001',
    disputedAmount: 100,
    reasonCode: 'MERCHANT_ERROR',
    status: 'OPEN',
    ...overrides,
  };
}

export const agentProfile: UserProfile = {
  fullName: 'Agent User',
  role: 'AGENT',
  email: 'agent-user@bitpro.co.za',
  phone: '+27 11 111 1111',
  department: 'Customer Operations',
  region: 'Johannesburg, South Africa',
};

type Api = TransactionDisputeService;

/** A fully stubbed API; individual tests override the returns they care about. */
export function createFakeApi() {
  return {
    clearToken: vi.fn<Api['clearToken']>(),
    setToken: vi.fn<Api['setToken']>(),
    login: vi.fn<Api['login']>(() => of({ token: 'jwt', username: 'agent-user' })),
    getProfile: vi.fn<Api['getProfile']>(() => of(agentProfile)),
    getTransactions: vi.fn<Api['getTransactions']>(() => of([debitTransaction, creditTransaction])),
    getDisputes: vi.fn<Api['getDisputes']>(() => of([dispute()])),
    getAccounts: vi.fn<Api['getAccounts']>(() => of([])),
    getDisputeHistory: vi.fn<Api['getDisputeHistory']>(() => of([])),
    createDispute: vi.fn<Api['createDispute']>(() => of(dispute())),
    reviewDispute: vi.fn<Api['reviewDispute']>(() => of(dispute({ status: 'IN_REVIEW' }))),
    approveDispute: vi.fn<Api['approveDispute']>(() => of(dispute({ status: 'PROVISIONAL_CREDIT_ISSUED' }))),
    reverseDispute: vi.fn<Api['reverseDispute']>(() => of(dispute({ status: 'RESOLVED' }))),
    updateProfile: vi.fn<Api['updateProfile']>((profile) => of(profile)),
  };
}

export type FakeApi = ReturnType<typeof createFakeApi>;