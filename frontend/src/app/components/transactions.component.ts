import { Component, inject } from '@angular/core';
import { PortalStore } from '../portal.store';
import { formatAmount, isDisputeDisabled } from '../portal.utils';

@Component({
  selector: 'app-transactions',
  standalone: true,
  template: `
    <section class="panel">
      <div class="panel-header">
        <h2>Transactions</h2>
        <span>{{ store.transactions().length }} records</span>
      </div>

      <div class="panel-summary">
        <div class="mini-metric">
          <span>Available balance</span>
          <strong>{{ formatAmount(store.availableBalance()) }}</strong>
        </div>
        <div class="mini-metric">
          <span>Balance</span>
          <strong>{{ formatAmount(store.accountBalance()) }}</strong>
        </div>
      </div>

      <div class="table-wrap">
        <table>
          <thead>
            <tr>
              <th>Reference</th>
              <th>Account</th>
              <th>Value</th>
              <th>Merchant</th>
              <th>Amount</th>
              <th>Status</th>
              <th>Action</th>
            </tr>
          </thead>
          <tbody>
            @for (transaction of store.transactions(); track transaction.id) {
              <tr>
                <td>{{ transaction.transactionReference }}</td>
                <td>{{ transaction.accountNumber }}</td>
                <td>{{ transaction.description || (transaction.status.toUpperCase() === 'PENDING' ? formatAmount(transaction.amount, transaction.type) : '—') }}</td>
                <td>{{ transaction.merchantName }}</td>
                <td>{{ formatAmount(transaction.amount, transaction.type) }}</td>
                <td><span class="badge status-badge" [class.status-reversed]="(transaction.status || '').toUpperCase() === 'REVERSED'">{{ transaction.status }}</span></td>
                <td>
                  <button
                    type="button"
                    class="link-button"
                    [disabled]="isDisputeDisabled(transaction)"
                    [attr.aria-disabled]="isDisputeDisabled(transaction)"
                    (click)="store.selectTransaction(transaction)">
                    {{ isDisputeDisabled(transaction) ? 'Not eligible' : 'Dispute' }}
                  </button>
                </td>
              </tr>
            }
          </tbody>
        </table>
      </div>
    </section>
  `,
})
export class TransactionsComponent {
  protected readonly store = inject(PortalStore);
  protected readonly formatAmount = formatAmount;
  protected readonly isDisputeDisabled = isDisputeDisabled;
}
