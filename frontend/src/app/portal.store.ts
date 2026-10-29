import { Injectable, computed, inject, signal } from '@angular/core';
import { finalize } from 'rxjs';
import {
  AccountSummary,
  Dispute,
  DisputeCreateRequest,
  DisputeHistoryEntry,
  Transaction,
  TransactionDisputeService,
  UserProfile,
} from './transaction-dispute.service';
import {
  DashboardTab,
  accountBalance,
  availableBalance,
  canReverseDisputes,
  extractApiMessages,
  inferRoleForUsername,
  isCreditTransaction,
  isDisputeDisabled,
  resolveApiError,
} from './portal.utils';

const DEFAULT_PROFILE: UserProfile = {
  fullName: 'Dispute Operations Lead',
  role: 'AGENT',
  email: 'lead-user@bitpro.co.za',
  phone: '+27 11 555 0123',
  department: 'Customer Operations',
  region: 'Johannesburg, South Africa',
};

/** Application state and workflows shared by all portal components. */
@Injectable({ providedIn: 'root' })
export class PortalStore {
  private readonly api = inject(TransactionDisputeService);

  readonly isLoggedIn = signal(false);
  readonly currentUsername = signal('');
  readonly activeTab = signal<DashboardTab>('transactions');
  readonly profile = signal<UserProfile>({ ...DEFAULT_PROFILE });
  readonly transactions = signal<Transaction[]>([]);
  readonly disputes = signal<Dispute[]>([]);
  readonly accountSummary = signal<AccountSummary | null>(null);
  readonly selectedTransaction = signal<Transaction | null>(null);
  readonly selectedDispute = signal<Dispute | null>(null);
  readonly disputeHistory = signal<DisputeHistoryEntry[]>([]);
  readonly successMessage = signal('');
  readonly errorMessage = signal('');

  readonly isLoggingIn = signal(false);
  readonly isSubmitting = signal(false);
  readonly isReviewing = signal(false);
  readonly isApproving = signal(false);
  readonly isReversing = signal(false);

  readonly openDisputeCount = computed(() => this.countByStatus('OPEN'));
  readonly inReviewDisputeCount = computed(() => this.countByStatus('IN_REVIEW'));
  readonly resolvedDisputeCount = computed(() => this.countByStatus('RESOLVED'));
  readonly availableBalance = computed(() => availableBalance(this.transactions(), this.accountSummary()));
  readonly accountBalance = computed(() => accountBalance(this.transactions(), this.accountSummary()));

  constructor() {
    this.api.clearToken();
  }

  setActiveTab(tab: DashboardTab): void {
    this.activeTab.set(tab);
    this.clearMessages();
  }

  login(username: string, password: string): void {
    const name = (username ?? '').trim();

    if (this.isLoggingIn()) {
      return;
    }
    if (!name || !password) {
      this.errorMessage.set('Please enter both username and password.');
      return;
    }

    this.isLoggingIn.set(true);
    this.clearMessages();

    this.api.login(name, password)
      .pipe(finalize(() => this.isLoggingIn.set(false)))
      .subscribe({
        next: (response) => {
          this.api.setToken(response.token);
          this.currentUsername.set(response.username);
          this.profile.update((profile) => ({ ...profile, role: inferRoleForUsername(response.username) }));
          this.isLoggedIn.set(true);
          this.successMessage.set(`Welcome back, ${response.username}.`);
          this.refreshDashboard();
        },
        error: (error) => {
          this.isLoggedIn.set(false);
          this.api.clearToken();
          this.errorMessage.set(resolveApiError(error, 'Invalid username or password. Please try the dev credentials.'));
        },
      });
  }

  logout(): void {
    this.api.clearToken();
    this.isLoggedIn.set(false);
    this.currentUsername.set('');
    this.activeTab.set('transactions');
    this.transactions.set([]);
    this.disputes.set([]);
    this.accountSummary.set(null);
    this.selectedDispute.set(null);
    this.selectedTransaction.set(null);
    this.disputeHistory.set([]);
    this.errorMessage.set('');
    this.successMessage.set('You have been logged out.');
  }

  refreshDashboard(): void {
    this.loadProfile();
    this.loadAccountSummary();
    this.loadTransactions();
    this.loadDisputes();
  }

  loadTransactions(): void {
    this.api.getTransactions().subscribe({
      next: (transactions) => this.transactions.set(transactions),
      error: (error) => this.errorMessage.set(resolveApiError(error, 'Unable to load transactions right now.')),
    });
  }

  loadDisputes(): void {
    this.api.getDisputes().subscribe({
      next: (disputes) => {
        this.disputes.set(disputes);
        if (!this.selectedDispute() && disputes.length > 0) {
          this.selectedDispute.set(disputes[0]);
        }
      },
      error: (error) => this.errorMessage.set(resolveApiError(error, 'Unable to load the portal data right now.')),
    });
  }

  selectTransaction(transaction: Transaction): void {
    if (isCreditTransaction(transaction)) {
      this.fail('Credit transactions are not eligible for dispute submission.');
      return;
    }
    if (isDisputeDisabled(transaction)) {
      this.fail('Reversed transactions are not eligible for dispute submission.');
      return;
    }

    this.selectedTransaction.set(transaction);
    this.activeTab.set('new-dispute');
    this.errorMessage.set('');
    this.successMessage.set('Transaction selected. Please review and submit the dispute details.');
  }

  selectDispute(dispute: Dispute): void {
    this.selectedDispute.set(dispute);
    this.disputeHistory.set([]);
    this.activeTab.set('track-dispute');

    this.api.getDisputeHistory(dispute.id).subscribe({
      next: (history) => this.disputeHistory.set(history ?? []),
      error: () => this.disputeHistory.set([]),
    });
  }

  submitDispute(request: DisputeCreateRequest): void {
    if (this.isSubmitting()) {
      return;
    }

    const transactionReference = (request.transactionReference ?? '').trim();
    const accountNumber = (request.accountNumber ?? '').trim();
    const reasonCode = (request.reasonCode ?? '').trim();

    if (!transactionReference || !accountNumber || !reasonCode) {
      this.errorMessage.set('Please select a transaction and complete the dispute type and details.');
      return;
    }

    const transaction = this.transactions().find((tx) => tx.transactionReference === transactionReference)
      ?? this.selectedTransaction();

    if (!transaction) {
      this.errorMessage.set('Please select a valid transaction before submitting the dispute.');
      return;
    }
    if (isCreditTransaction(transaction)) {
      this.errorMessage.set('Credit transactions cannot be disputed.');
      return;
    }

    const payload: DisputeCreateRequest = {
      ...request,
      transactionReference,
      accountNumber,
      disputeType: 'CHARGEBACK',
      reasonCode,
      description: (request.description ?? '').trim() || `Dispute for ${transaction.merchantName}`,
      provisionalCreditIssued: Boolean(request.provisionalCreditIssued),
      merchantEvidenceProvided: Boolean(request.merchantEvidenceProvided),
      decisionOutcome: request.decisionOutcome?.trim() ?? '',
    };

    this.isSubmitting.set(true);
    this.clearMessages();

    this.api.createDispute(payload).subscribe({
      next: (created) => {
        this.isSubmitting.set(false);
        this.successMessage.set('Dispute submitted successfully.');
        this.selectedTransaction.set(null);
        this.selectedDispute.set(created ?? null);
        this.activeTab.set('track-dispute');
        this.loadAccountSummary();
        this.loadTransactions();

        this.api.getDisputes().subscribe({
          next: (disputes) => {
            this.disputes.set(disputes);
            this.selectedDispute.set(
              disputes.find((dispute) => dispute.disputeId === created?.disputeId) ?? created ?? disputes[0] ?? null,
            );
          },
          error: (error) => this.errorMessage.set(resolveApiError(error, 'Unable to reload the updated dispute list.')),
        });
      },
      error: (error) => {
        this.isSubmitting.set(false);
        this.errorMessage.set(resolveApiError(error, 'The dispute could not be submitted. Please check the form and try again.'));
      },
    });
  }

  reviewDispute(decision: string, note: string): void {
    const dispute = this.selectedDispute();
    if (!dispute) {
      this.errorMessage.set('Please select a dispute to review.');
      return;
    }
    if (this.isReviewing()) {
      return;
    }

    this.isReviewing.set(true);
    this.clearMessages();

    this.api.reviewDispute(dispute.id, decision.trim() || 'APPROVE', note.trim() || 'Reviewed by operations team.').subscribe({
      next: (updated) => {
        this.isReviewing.set(false);
        this.selectedDispute.set(updated);
        this.successMessage.set(`Dispute ${updated.disputeId} has been marked for review.`);
        this.refreshDashboard();
      },
      error: (error) => {
        this.isReviewing.set(false);
        this.errorMessage.set(resolveApiError(error, 'Unable to review this dispute right now.'));
      },
    });
  }

  approveDispute(note: string): void {
    const dispute = this.selectedDispute();
    if (!dispute) {
      this.errorMessage.set('Please select a dispute to approve.');
      return;
    }
    if (this.isApproving()) {
      return;
    }

    this.isApproving.set(true);
    this.clearMessages();

    this.api.approveDispute(dispute.id, note.trim() || 'Approved by dispute operations lead.').subscribe({
      next: (updated) => {
        this.isApproving.set(false);
        this.selectedDispute.set(updated);
        this.successMessage.set(`Dispute ${updated.disputeId} has been approved and provisional credit issued.`);
        this.refreshDashboard();
      },
      error: (error) => {
        this.isApproving.set(false);
        this.errorMessage.set(resolveApiError(error, 'Unable to approve this dispute right now.'));
      },
    });
  }

  reverseDispute(reason: string): void {
    const dispute = this.selectedDispute();
    if (!dispute) {
      this.errorMessage.set('Please select a dispute to reverse.');
      return;
    }
    if (!canReverseDisputes(this.profile().role, this.currentUsername())) {
      this.errorMessage.set('Only authorised dispute agents or administrators can reverse a dispute.');
      return;
    }

    const reasonCode = (reason ?? '').trim();
    if (!reasonCode) {
      this.errorMessage.set('Please choose a reversal reason before resolving the dispute.');
      return;
    }
    if (this.isReversing()) {
      return;
    }

    this.isReversing.set(true);
    this.clearMessages();

    this.api.reverseDispute(dispute.id, reasonCode).subscribe({
      next: (updated) => {
        this.isReversing.set(false);
        this.selectedDispute.set(updated);
        this.successMessage.set(`Dispute ${updated.disputeId} has been reversed and resolved.`);
        this.errorMessage.set(extractApiMessages(updated).join('. '));
        this.refreshDashboard();
      },
      error: (error) => {
        this.isReversing.set(false);
        this.errorMessage.set(resolveApiError(error, 'Unable to reverse this dispute. Please try again.'));
      },
    });
  }

  updateProfile(profile: UserProfile): void {
    this.api.updateProfile(profile).subscribe({
      next: (updated) => {
        this.profile.set(updated);
        this.successMessage.set('Profile updated successfully.');
        this.errorMessage.set('');
      },
      error: (error) => this.errorMessage.set(resolveApiError(error, 'The profile could not be saved right now.')),
    });
  }

  private loadProfile(): void {
    this.api.getProfile().subscribe({
      next: (profile) => {
        this.profile.update((current) => ({
          ...current,
          ...profile,
          role: (profile?.role ?? inferRoleForUsername(this.currentUsername())).toUpperCase(),
        }));
      },
      error: (error) => {
        this.errorMessage.set(resolveApiError(error, 'Unable to load the current profile.'));
        this.profile.set({ ...DEFAULT_PROFILE });
      },
    });
  }

  private loadAccountSummary(): void {
    this.api.getAccounts().subscribe({
      next: (accounts) => this.accountSummary.set(accounts[0] ?? null),
      error: (error) => {
        this.accountSummary.set(null);
        this.errorMessage.set(resolveApiError(error, 'Unable to load the account summary.'));
      },
    });
  }

  private countByStatus(status: string): number {
    return this.disputes().filter((dispute) => dispute.status.toUpperCase() === status).length;
  }

  private fail(message: string): void {
    this.errorMessage.set(message);
    this.successMessage.set('');
  }

  private clearMessages(): void {
    this.errorMessage.set('');
    this.successMessage.set('');
  }
}
