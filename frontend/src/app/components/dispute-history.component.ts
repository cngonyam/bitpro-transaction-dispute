import { NgClass } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { PortalStore } from '../portal.store';
import { formatAmount, formatDate, statusClass } from '../portal.utils';

type HistoryFilter = 'ALL' | 'OPEN' | 'IN_REVIEW' | 'RESOLVED' | 'REVERSED';

@Component({
  selector: 'app-dispute-history',
  standalone: true,
  imports: [NgClass],
  template: `
    <section class="panel">
      <div class="panel-header">
        <h2>Dispute history</h2>
        <div class="filter-group">
          @for (option of filters; track option.value) {
            <button type="button" [class.active]="filter() === option.value" (click)="filter.set(option.value)">{{ option.label }}</button>
          }
        </div>
      </div>

      <div class="table-wrap">
        <table>
          <thead>
            <tr>
              <th>Dispute ID</th>
              <th>Transaction</th>
              <th>Account</th>
              <th>Amount</th>
              <th>Status</th>
              <th>Updated</th>
              <th>Action</th>
            </tr>
          </thead>
          <tbody>
            @for (dispute of filteredDisputes(); track dispute.id) {
              <tr>
                <td>{{ dispute.disputeId }}</td>
                <td>{{ dispute.transactionReference }}</td>
                <td>{{ dispute.accountNumber }}</td>
                <td>{{ formatAmount(dispute.disputedAmount) }}</td>
                <td><span class="badge" [ngClass]="statusClass(dispute.status)">{{ dispute.status }}</span></td>
                <td>{{ formatDate(dispute.updatedAt ?? dispute.createdAt) }}</td>
                <td><button type="button" class="link-button" (click)="store.selectDispute(dispute)">Track</button></td>
              </tr>
            }
          </tbody>
        </table>
      </div>
    </section>
  `,
})
export class DisputeHistoryComponent {
  protected readonly store = inject(PortalStore);

  protected readonly filter = signal<HistoryFilter>('ALL');
  protected readonly filters: { value: HistoryFilter; label: string }[] = [
    { value: 'ALL', label: 'All' },
    { value: 'OPEN', label: 'Open' },
    { value: 'IN_REVIEW', label: 'In review' },
    { value: 'RESOLVED', label: 'Resolved' },
    { value: 'REVERSED', label: 'Reversed' },
  ];

  protected readonly filteredDisputes = computed(() => {
    const filter = this.filter();
    const disputes = this.store.disputes();
    return filter === 'ALL' ? disputes : disputes.filter((dispute) => dispute.status.toUpperCase() === filter);
  });

  protected readonly formatAmount = formatAmount;
  protected readonly formatDate = formatDate;
  protected readonly statusClass = statusClass;
}
