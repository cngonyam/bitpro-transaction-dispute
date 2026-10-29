import { Component, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { PortalStore } from '../portal.store';
import { DisputeCreateRequest } from '../transaction-dispute.service';

@Component({
  selector: 'app-submit-dispute',
  standalone: true,
  imports: [FormsModule],
  template: `
    <section class="panel form-panel">
      <div class="panel-header">
        <h2>Submit dispute</h2>
        @if (store.selectedTransaction(); as selected) {
          <span>Selected: {{ selected.transactionReference }}</span>
        }
      </div>

      <form class="dispute-form" (ngSubmit)="submit()">
        <div class="field-grid">
          <label>
            Transaction reference
            <input type="text" [(ngModel)]="form.transactionReference" name="transactionReference" readonly />
          </label>

          <label>
            Account number
            <input type="text" [(ngModel)]="form.accountNumber" name="accountNumber" required />
          </label>

          <label>
            Disputed amount
            <input type="number" step="0.01" [(ngModel)]="form.disputedAmount" name="disputedAmount" required />
          </label>

          <label>
            Dispute type
            <select [(ngModel)]="form.disputeType" name="disputeType" required>
              <option value="CHARGEBACK">Bank / payment dispute</option>
            </select>
          </label>

          <label>
            Reason code
            <select [(ngModel)]="form.reasonCode" name="reasonCode" required>
              <option value="">Select reason</option>
              <option value="MERCHANT_ERROR">Merchant error</option>
              <option value="DUPLICATE">Duplicate transaction</option>
              <option value="FRAUD">Fraudulent transaction</option>
              <option value="NOT_RECEIVED">Not received</option>
              <option value="UNAUTHORIZED_DEBIT">Unauthorized debit</option>
            </select>
          </label>
        </div>

        <div class="field-grid">
          @if (form.disputeType === 'CHARGEBACK') {
            <label>
              Provisional credit issued
              <select [(ngModel)]="form.provisionalCreditIssued" name="provisionalCreditIssued">
                <option [ngValue]="false">No</option>
                <option [ngValue]="true">Yes</option>
              </select>
            </label>

            <label>
              Merchant evidence provided
              <select [(ngModel)]="form.merchantEvidenceProvided" name="merchantEvidenceProvided">
                <option [ngValue]="false">No</option>
                <option [ngValue]="true">Yes</option>
              </select>
            </label>
          }

          <label>
            Decision outcome
            <select [(ngModel)]="form.decisionOutcome" name="decisionOutcome">
              <option value="">Select outcome</option>
              <option value="AUTHORIZED">Authorized</option>
              <option value="UNAUTHORIZED">Unauthorized</option>
              <option value="REVERSED">Reversed</option>
            </select>
          </label>
        </div>

        <label>
          Description
          <textarea [(ngModel)]="form.description" name="description" rows="4" placeholder="Describe the issue"></textarea>
        </label>

        <button type="submit" class="primary-button" [disabled]="store.isSubmitting()">
          {{ store.isSubmitting() ? 'Submitting...' : 'Submit dispute' }}
        </button>
      </form>
    </section>
  `,
})
export class SubmitDisputeComponent {
  protected readonly store = inject(PortalStore);

  protected form: DisputeCreateRequest = this.buildForm();

  protected submit(): void {
    this.store.submitDispute(this.form);
  }

  private buildForm(): DisputeCreateRequest {
    const transaction = this.store.selectedTransaction();

    return {
      transactionReference: transaction?.transactionReference ?? '',
      accountNumber: transaction?.accountNumber ?? '',
      disputedAmount: transaction ? Math.abs(Number(transaction.amount ?? 0)) : 0,
      reasonCode: transaction ? 'MERCHANT_ERROR' : '',
      disputeType: 'CHARGEBACK',
      description: transaction ? `Dispute for ${transaction.merchantName}` : '',
      provisionalCreditIssued: false,
      merchantEvidenceProvided: false,
      decisionOutcome: '',
    };
  }
}
