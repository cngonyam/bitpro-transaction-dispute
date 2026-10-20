import { CommonModule } from '@angular/common';
import { Component, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Dispute, DisputeCreateRequest, Transaction, TransactionDisputeService } from './transaction-dispute.service';

export type DashboardTab = 'transactions' | 'new-dispute' | 'track-dispute' | 'dispute-history' | 'profile';

interface UserProfile {
  fullName: string;
  role: string;
  email: string;
  phone: string;
  department: string;
  region: string;
}

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './app.html',
  styleUrl: './app.css',
})
export class App implements OnInit {
  activeTab: DashboardTab = 'transactions';
  transactions: Transaction[] = [];
  disputes: Dispute[] = [];
  selectedTransaction: Transaction | null = null;
  selectedDispute: Dispute | null = null;
  historyFilter: 'ALL' | 'OPEN' | 'IN_REVIEW' | 'RESOLVED' = 'ALL';
  isSubmitting = false;
  successMessage = '';
  errorMessage = '';

  profile: UserProfile = {
    fullName: 'Ndumiso Ngonyama',
    role: 'Dispute Operations Lead',
    email: 'ndumiso.ngonyama@bitpro.co.za',
    phone: '+27 11 555 0123',
    department: 'Customer Operations',
    region: 'Johannesburg, South Africa',
  };

  form: DisputeCreateRequest = {
    transactionReference: '',
    accountNumber: '',
    disputedAmount: 0,
    reasonCode: '',
    description: '',
  };

  constructor(private api: TransactionDisputeService) {}

  ngOnInit(): void {
    this.loadTransactions();
    this.loadDisputes();
  }

  get filteredDisputes(): Dispute[] {
    if (this.historyFilter === 'ALL') {
      return this.disputes;
    }

    return this.disputes.filter((dispute) => dispute.status.toUpperCase() === this.historyFilter);
  }

  get openDisputeCount(): number {
    return this.disputes.filter((dispute) => dispute.status.toUpperCase() === 'OPEN').length;
  }

  get inReviewDisputeCount(): number {
    return this.disputes.filter((dispute) => dispute.status.toUpperCase() === 'IN_REVIEW').length;
  }

  get resolvedDisputeCount(): number {
    return this.disputes.filter((dispute) => dispute.status.toUpperCase() === 'RESOLVED').length;
  }

  loadTransactions(): void {
    this.api.getTransactions().subscribe({
      next: (data) => {
        this.transactions = data;
      },
      error: () => {
        this.errorMessage = 'Unable to load transactions right now.';
      },
    });
  }

  loadDisputes(): void {
    this.api.getDisputes().subscribe({
      next: (data) => {
        this.disputes = data;
        if (!this.selectedDispute && data.length > 0) {
          this.selectedDispute = data[0];
        }
      },
      error: () => {
        this.errorMessage = 'Unable to load disputes right now.';
      },
    });
  }

  setActiveTab(tab: DashboardTab): void {
    this.activeTab = tab;
    this.errorMessage = '';
    this.successMessage = '';
  }

  selectTransaction(transaction: Transaction): void {
    this.selectedTransaction = transaction;
    this.form.transactionReference = transaction.transactionReference;
    this.form.accountNumber = transaction.accountNumber;
    this.form.disputedAmount = transaction.amount;
    this.form.reasonCode = 'MERCHANT_ERROR';
    this.form.description = `Dispute for ${transaction.merchantName}`;
    this.activeTab = 'new-dispute';
    this.errorMessage = '';
    this.successMessage = '';
  }

  selectDisputeToTrack(dispute: Dispute): void {
    this.selectedDispute = dispute;
    this.activeTab = 'track-dispute';
  }

  submitDispute(): void {
    if (!this.form.transactionReference || !this.form.accountNumber || !this.form.reasonCode) {
      this.errorMessage = 'Please select a transaction and complete the dispute details.';
      return;
    }

    this.isSubmitting = true;
    this.errorMessage = '';
    this.successMessage = '';

    this.api.createDispute(this.form).subscribe({
      next: () => {
        this.isSubmitting = false;
        this.successMessage = 'Dispute submitted successfully.';
        this.form = {
          transactionReference: '',
          accountNumber: '',
          disputedAmount: 0,
          reasonCode: '',
          description: '',
        };
        this.selectedTransaction = null;
        this.loadDisputes();
      },
      error: () => {
        this.isSubmitting = false;
        this.errorMessage = 'The dispute could not be submitted. Please check the form and try again.';
      },
    });
  }

  updateProfile(): void {
    this.successMessage = 'Profile updated successfully.';
    this.errorMessage = '';
  }

  getDisputeProgress(status?: string): number {
    const normalized = (status ?? '').toUpperCase();

    switch (normalized) {
      case 'OPEN':
        return 25;
      case 'IN_REVIEW':
        return 60;
      case 'RESOLVED':
        return 100;
      default:
        return 15;
    }
  }

  getDisputeTimeline(status?: string): string[] {
    const normalized = (status ?? '').toUpperCase();

    if (normalized === 'RESOLVED') {
      return ['Submitted', 'In review', 'Final review', 'Resolved'];
    }

    if (normalized === 'IN_REVIEW') {
      return ['Submitted', 'In review', 'Awaiting decision'];
    }

    return ['Submitted', 'Open', 'Under review'];
  }

  formatAmount(value: number | string): string {
    return new Intl.NumberFormat('en-ZA', {
      style: 'currency',
      currency: 'ZAR',
    }).format(Number(value));
  }

  formatDate(value?: string): string {
    if (!value) {
      return '—';
    }

    return new Date(value).toLocaleString();
  }
}
