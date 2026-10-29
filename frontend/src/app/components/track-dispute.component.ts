import { NgClass } from '@angular/common';
import { Component, effect, inject, signal, untracked } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { PortalStore } from '../portal.store';
import { disputeProgress, disputeTimeline, formatAmount, formatDate, statusClass } from '../portal.utils';

@Component({
  selector: 'app-track-dispute',
  standalone: true,
  imports: [FormsModule, NgClass],
  template: `
    <section class="panel">
      <div class="panel-header">
        <h2>Track dispute status</h2>
        <span>{{ store.disputes().length }} cases</span>
      </div>

      <div class="status-overview">
        <div class="metric-box metric-open">
          <span>Open</span>
          <strong>{{ store.openDisputeCount() }}</strong>
        </div>
        <div class="metric-box metric-in-review">
          <span>In review</span>
          <strong>{{ store.inReviewDisputeCount() }}</strong>
        </div>
        <div class="metric-box metric-resolved">
          <span>Resolved</span>
          <strong>{{ store.resolvedDisputeCount() }}</strong>
        </div>
      </div>

      <div class="two-column-layout">
        <div class="sub-panel">
          <h3>Dispute list</h3>
          <div class="status-list">
            @for (dispute of store.disputes(); track dispute.id) {
              <button
                type="button"
                class="status-row"
                [class.active]="store.selectedDispute()?.id === dispute.id"
                [ngClass]="statusClass(dispute.status)"
                (click)="store.selectDispute(dispute)">
                <div>
                  <strong>{{ dispute.disputeId }}</strong>
                  <small>{{ dispute.transactionReference }}</small>
                </div>
                <span class="badge" [ngClass]="statusClass(dispute.status)">{{ dispute.status }}</span>
              </button>
            }
          </div>
        </div>

        <div class="sub-panel">
          @if (store.selectedDispute(); as dispute) {
            <div class="status-detail">
              <div class="status-header-row">
                <div>
                  <p class="muted-label">Case reference</p>
                  <h3>{{ dispute.disputeId }}</h3>
                </div>
                <span class="badge" [ngClass]="statusClass(dispute.status)">{{ dispute.status }}</span>
              </div>

              <div class="progress-meta">
                <span>Progress</span>
                <strong>{{ disputeProgress(dispute.status) }}%</strong>
              </div>
              <div class="progress-bar">
                <span [style.width.%]="disputeProgress(dispute.status)"></span>
              </div>

              <div class="status-grid-detail">
                <div><span>Account</span><strong>{{ dispute.accountNumber }}</strong></div>
                <div><span>Amount</span><strong>{{ formatAmount(dispute.disputedAmount) }}</strong></div>
                <div><span>Reason</span><strong>{{ dispute.reasonCode }}</strong></div>
                <div><span>Managed by</span><strong>{{ dispute.assignedToFullName || 'Unassigned' }}</strong></div>
                <div><span>Created</span><strong>{{ formatDate(dispute.createdAt) }}</strong></div>
                <div><span>Reversed by</span><strong>{{ dispute.reversedByUsername || '—' }}</strong></div>
                <div><span>Reversal role</span><strong>{{ dispute.reversedByRole || '—' }}</strong></div>
              </div>

              <div class="status-detail-section">
                <h4>Audit trail</h4>
                @if (store.disputeHistory().length === 0) {
                  <p class="muted-text">No audit events recorded yet.</p>
                } @else {
                  <div class="history-list">
                    @for (event of store.disputeHistory(); track event.id) {
                      <div class="history-item">
                        <div class="history-head">
                          <strong>{{ event.action || 'Action' }}</strong>
                          <span>{{ formatDate(event.changedAt) }}</span>
                        </div>
                        <div class="history-meta">
                          <span>Actor: {{ event.actor || 'Unknown' }}</span>
                          <span>Role: {{ event.actorRole || 'Unknown' }}</span>
                        </div>
                        <div class="history-meta">
                          <span>From: {{ event.fromStatus || '—' }}</span>
                          <span>To: {{ event.toStatus || '—' }}</span>
                        </div>
                        <div class="history-meta">
                          <span>Reason: {{ event.reasonCode || '—' }}</span>
                        </div>
                      </div>
                    }
                  </div>
                }
              </div>

              <div class="reverse-action-box">
                @if (dispute.status === 'OPEN' || dispute.status === 'IN_REVIEW') {
                  <div class="workflow-action-group">
                    <label>
                      Review decision
                      <select [(ngModel)]="reviewDecision" name="reviewDecision">
                        <option value="APPROVE">Approve</option>
                        <option value="REJECT">Reject</option>
                      </select>
                    </label>

                    <label>
                      Review note
                      <textarea [(ngModel)]="reviewNote" name="reviewNote" rows="3" placeholder="Add review note"></textarea>
                    </label>

                    <button type="button" class="primary-button" [disabled]="store.isReviewing()" (click)="review()">
                      {{ store.isReviewing() ? 'Reviewing...' : 'Review dispute' }}
                    </button>
                  </div>
                }

                @if (dispute.status === 'IN_REVIEW') {
                  <div class="workflow-action-group">
                    <label>
                      Approval note
                      <textarea [(ngModel)]="approvalNote" name="approvalNote" rows="3" placeholder="Add approval note"></textarea>
                    </label>

                    <button type="button" class="primary-button" [disabled]="store.isApproving()" (click)="approve()">
                      {{ store.isApproving() ? 'Approving...' : 'Approve dispute' }}
                    </button>
                  </div>
                }

                @if (dispute.status === 'PROVISIONAL_CREDIT_ISSUED') {
                  <label>
                    Reversal reason
                    <select [(ngModel)]="reverseReason" name="reverseReason">
                      <option value="">Select reason</option>
                      <option value="MERCHANT_ERROR">Merchant error</option>
                      <option value="UNAUTHORISED_DEBIT">Unauthorised debit</option>
                      <option value="DUPLICATE_DEBIT">Duplicate debit</option>
                      <option value="FRAUD">Fraud</option>
                      <option value="CANCELLATION">Cancellation</option>
                    </select>
                  </label>

                  <button type="button" class="primary-button" [disabled]="store.isReversing()" (click)="reverse()">
                    {{ store.isReversing() ? 'Reversing...' : 'Reverse dispute' }}
                  </button>
                }
              </div>

              <div class="timeline">
                @for (step of disputeTimeline(dispute.status); track step; let i = $index) {
                  <div class="timeline-item" [class.done]="i <= disputeProgress(dispute.status) / 35">
                    <span class="timeline-dot"></span>
                    <div>
                      <strong>{{ step }}</strong>
                      <small>{{ i === 0 ? 'Received' : i === 1 ? 'Stage underway' : 'Final stage' }}</small>
                    </div>
                  </div>
                }
              </div>
            </div>
          }
        </div>
      </div>
    </section>
  `,
})
export class TrackDisputeComponent {
  protected readonly store = inject(PortalStore);

  protected readonly reviewDecision = signal('APPROVE');
  protected readonly reviewNote = signal('');
  protected readonly approvalNote = signal('');
  protected readonly reverseReason = signal('');

  protected readonly formatAmount = formatAmount;
  protected readonly formatDate = formatDate;
  protected readonly statusClass = statusClass;
  protected readonly disputeProgress = disputeProgress;
  protected readonly disputeTimeline = disputeTimeline;

  constructor() {
    effect(() => {
      const dispute = this.store.selectedDispute();
      untracked(() => {
        this.reverseReason.set(dispute?.reasonCode || 'MERCHANT_ERROR');
        this.reviewDecision.set(dispute?.reviewDecision || 'APPROVE');
        this.reviewNote.set(dispute?.reviewDecision ? `Reviewed: ${dispute.reviewDecision}` : '');
        this.approvalNote.set(dispute?.approvalNote || '');
      });
    });
  }

  protected review(): void {
    this.store.reviewDispute(this.reviewDecision(), this.reviewNote());
  }

  protected approve(): void {
    this.store.approveDispute(this.approvalNote());
  }

  protected reverse(): void {
    this.store.reverseDispute(this.reverseReason());
  }
}
