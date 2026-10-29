import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { PortalStore } from './portal.store';
import { TransactionDisputeService } from './transaction-dispute.service';
import { FakeApi, agentProfile, createFakeApi, creditTransaction, debitTransaction, dispute, reversedTransaction } from './testing/test-data';

describe('PortalStore', () => {
  let api: FakeApi;
  let store: PortalStore;

  beforeEach(() => {
    api = createFakeApi();
    TestBed.configureTestingModule({ providers: [{ provide: TransactionDisputeService, useValue: api }] });
    store = TestBed.inject(PortalStore);
  });

  describe('login', () => {
    it('rejects empty credentials without calling the API', () => {
      store.login('  ', '');

      expect(api.login).not.toHaveBeenCalled();
      expect(store.errorMessage()).toBe('Please enter both username and password.');
      expect(store.isLoggedIn()).toBe(false);
    });

    it('stores the token, infers the role and loads the dashboard', () => {
      api.getProfile.mockReturnValue(of({ ...agentProfile, role: undefined as unknown as string }));

      store.login('agent-user', 'agent-pass123');

      expect(api.setToken).toHaveBeenCalledWith('jwt');
      expect(store.isLoggedIn()).toBe(true);
      expect(store.currentUsername()).toBe('agent-user');
      expect(store.profile().role).toBe('AGENT');
      expect(store.transactions()).toHaveLength(2);
      expect(store.selectedDispute()?.disputeId).toBe('DSP-7');
      expect(store.isLoggingIn()).toBe(false);
    });

    it('clears the session and shows the API message on failure', () => {
      api.login.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 401, error: { message: 'Bad credentials' } })));

      store.login('agent-user', 'wrong');

      expect(store.isLoggedIn()).toBe(false);
      expect(store.errorMessage()).toBe('Bad credentials');
      expect(api.clearToken).toHaveBeenCalled();
      expect(store.isLoggingIn()).toBe(false);
    });

    it('logout wipes sensitive state', () => {
      store.login('agent-user', 'agent-pass123');
      store.logout();

      expect(store.isLoggedIn()).toBe(false);
      expect(store.transactions()).toEqual([]);
      expect(store.disputes()).toEqual([]);
      expect(store.selectedDispute()).toBeNull();
      expect(store.currentUsername()).toBe('');
      expect(store.successMessage()).toBe('You have been logged out.');
    });
  });

  describe('selecting a transaction', () => {
    it('opens the submit tab for a debit', () => {
      store.selectTransaction(debitTransaction);

      expect(store.selectedTransaction()).toBe(debitTransaction);
      expect(store.activeTab()).toBe('new-dispute');
    });

    it.each([
      [creditTransaction, 'Credit transactions are not eligible for dispute submission.'],
      [reversedTransaction, 'Reversed transactions are not eligible for dispute submission.'],
    ])('refuses ineligible transactions', (transaction, message) => {
      store.selectTransaction(transaction);

      expect(store.errorMessage()).toBe(message);
      expect(store.selectedTransaction()).toBeNull();
      expect(store.activeTab()).toBe('transactions');
    });
  });

  describe('submitDispute', () => {
    const request = {
      transactionReference: 'TX-1',
      accountNumber: 'ACC-1001',
      disputedAmount: 100,
      reasonCode: 'MERCHANT_ERROR',
      disputeType: 'CHARGEBACK' as const,
      description: '   ',
    };

    beforeEach(() => store.login('customer-user', 'customer-pass123'));

    it('validates required fields', () => {
      store.submitDispute({ ...request, reasonCode: '' });

      expect(api.createDispute).not.toHaveBeenCalled();
      expect(store.errorMessage()).toContain('select a transaction');
    });

    it('refuses credit transactions', () => {
      api.getTransactions.mockReturnValue(of([creditTransaction]));
      store.loadTransactions();

      store.submitDispute({ ...request, transactionReference: 'TX-2' });

      expect(api.createDispute).not.toHaveBeenCalled();
      expect(store.errorMessage()).toBe('Credit transactions cannot be disputed.');
    });

    it('requires a known transaction', () => {
      store.submitDispute({ ...request, transactionReference: 'UNKNOWN' });

      expect(api.createDispute).not.toHaveBeenCalled();
      expect(store.errorMessage()).toContain('valid transaction');
    });

    it('normalises the payload, selects the created dispute and moves to tracking', () => {
      const created = dispute({ id: 9, disputeId: 'DSP-9' });
      api.createDispute.mockReturnValue(of(created));
      api.getDisputes.mockReturnValue(of([dispute(), created]));

      store.submitDispute(request);

      expect(api.createDispute).toHaveBeenCalledWith(expect.objectContaining({
        description: 'Dispute for Acme',
        disputeType: 'CHARGEBACK',
        decisionOutcome: '',
      }));
      expect(store.selectedDispute()?.disputeId).toBe('DSP-9');
      expect(store.activeTab()).toBe('track-dispute');
      expect(store.successMessage()).toBe('Dispute submitted successfully.');
      expect(store.isSubmitting()).toBe(false);
    });

    it('surfaces API failures and re-enables the form', () => {
      api.createDispute.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 409, error: { message: 'Already disputed' } })));

      store.submitDispute(request);

      expect(store.errorMessage()).toBe('Already disputed');
      expect(store.isSubmitting()).toBe(false);
    });
  });

  describe('dispute workflow', () => {
    beforeEach(() => store.login('lead-user', 'lead-pass123'));

    it('review sends defaults for blank input and updates the selection', () => {
      store.reviewDispute('', '  ');

      expect(api.reviewDispute).toHaveBeenCalledWith(7, 'APPROVE', 'Reviewed by operations team.');
      expect(store.selectedDispute()?.status).toBe('IN_REVIEW');
      expect(store.isReviewing()).toBe(false);
    });

    it('approve sends the note', () => {
      store.approveDispute('Evidence verified');

      expect(api.approveDispute).toHaveBeenCalledWith(7, 'Evidence verified');
      expect(store.selectedDispute()?.status).toBe('PROVISIONAL_CREDIT_ISSUED');
    });

    it('reverse requires a reason', () => {
      store.reverseDispute('  ');

      expect(api.reverseDispute).not.toHaveBeenCalled();
      expect(store.errorMessage()).toContain('reversal reason');
    });

    it('reverse is blocked for customers', () => {
      store.logout();
      api.login.mockReturnValue(of({ token: 'jwt', username: 'customer-user' }));
      api.getProfile.mockReturnValue(of({ ...agentProfile, role: 'CUSTOMER' }));
      store.login('customer-user', 'customer-pass123');

      store.reverseDispute('FRAUD');

      expect(api.reverseDispute).not.toHaveBeenCalled();
      expect(store.errorMessage()).toContain('Only authorised');
    });

    it('reverse posts the reason and reports success', () => {
      store.reverseDispute('FRAUD');

      expect(api.reverseDispute).toHaveBeenCalledWith(7, 'FRAUD');
      expect(store.successMessage()).toContain('reversed and resolved');
      expect(store.errorMessage()).toBe('');
      expect(store.isReversing()).toBe(false);
    });

    it('reverse maps a conflict into a message', () => {
      api.reverseDispute.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 409, error: { message: 'Dispute already resolved' } })));

      store.reverseDispute('FRAUD');

      expect(store.errorMessage()).toBe('Dispute already resolved');
      expect(store.isReversing()).toBe(false);
    });

    it('actions need a selected dispute', () => {
      store.selectedDispute.set(null);

      store.reviewDispute('APPROVE', '');
      expect(store.errorMessage()).toBe('Please select a dispute to review.');
      store.approveDispute('');
      expect(store.errorMessage()).toBe('Please select a dispute to approve.');
      store.reverseDispute('FRAUD');
      expect(store.errorMessage()).toBe('Please select a dispute to reverse.');
    });

    it('selecting a dispute loads its audit trail', () => {
      const history = [{ id: 1, disputeId: 7, action: 'CREATED' }];
      api.getDisputeHistory.mockReturnValue(of(history));

      store.selectDispute(dispute());

      expect(store.activeTab()).toBe('track-dispute');
      expect(store.disputeHistory()).toEqual(history);
    });

    it('selecting a dispute tolerates a failed history call', () => {
      api.getDisputeHistory.mockReturnValue(throwError(() => new Error('nope')));

      store.selectDispute(dispute());

      expect(store.disputeHistory()).toEqual([]);
    });
  });

  describe('derived state and profile', () => {
    it('counts disputes by status', () => {
      store.disputes.set([dispute(), dispute({ id: 2, status: 'IN_REVIEW' }), dispute({ id: 3, status: 'RESOLVED' }), dispute({ id: 4, status: 'RESOLVED' })]);

      expect(store.openDisputeCount()).toBe(1);
      expect(store.inReviewDisputeCount()).toBe(1);
      expect(store.resolvedDisputeCount()).toBe(2);
    });

    it('updates the profile', () => {
      const updated = { ...agentProfile, fullName: 'Renamed' };

      store.updateProfile(updated);

      expect(store.profile().fullName).toBe('Renamed');
      expect(store.successMessage()).toBe('Profile updated successfully.');
    });

    it('falls back to the default profile when loading fails', () => {
      api.getProfile.mockReturnValue(throwError(() => new Error('down')));

      store.login('agent-user', 'agent-pass123');

      expect(store.profile().fullName).toBe('Dispute Operations Lead');
      expect(store.errorMessage()).toBe('down');
    });

    it('switching tabs clears messages', () => {
      store.errorMessage.set('x');
      store.successMessage.set('y');

      store.setActiveTab('profile');

      expect(store.errorMessage()).toBe('');
      expect(store.successMessage()).toBe('');
    });
  });
});
