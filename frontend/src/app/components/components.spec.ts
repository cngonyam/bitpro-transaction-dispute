import { ComponentFixture, TestBed } from '@angular/core/testing';
import { PortalStore } from '../portal.store';
import { TransactionDisputeService } from '../transaction-dispute.service';
import { FakeApi, createFakeApi, creditTransaction, debitTransaction, dispute } from '../testing/test-data';
import { DisputeHistoryComponent } from './dispute-history.component';
import { LoginComponent } from './login.component';
import { SubmitDisputeComponent } from './submit-dispute.component';
import { TrackDisputeComponent } from './track-dispute.component';
import { TransactionsComponent } from './transactions.component';

function setup<T>(component: new () => T, prepare?: (store: PortalStore) => void): { fixture: ComponentFixture<T>; store: PortalStore; api: FakeApi; el: HTMLElement } {
  const api = createFakeApi();
  TestBed.configureTestingModule({
    imports: [component],
    providers: [{ provide: TransactionDisputeService, useValue: api }],
  });
  const store = TestBed.inject(PortalStore);
  prepare?.(store);
  const fixture = TestBed.createComponent(component);
  return { fixture, store, api, el: fixture.nativeElement as HTMLElement };
}

function buttonByText(el: HTMLElement, text: string): HTMLButtonElement | undefined {
  return Array.from(el.querySelectorAll('button')).find((b) => b.textContent?.trim() === text);
}

describe('LoginComponent', () => {
  it('signs in with the typed credentials', async () => {
    const { fixture, api, el } = setup(LoginComponent);
    await fixture.whenStable();

    const [username, password] = Array.from(el.querySelectorAll('input'));
    expect(username.value).toBe('lead-user');
    expect(password.value).toBe('lead-pass123');

    el.querySelector('form')!.dispatchEvent(new Event('submit'));
    await fixture.whenStable();

    expect(api.login).toHaveBeenCalledWith('lead-user', 'lead-pass123');
  });

  it('fills the form from a demo user', async () => {
    const { fixture, el } = setup(LoginComponent);
    await fixture.whenStable();

    buttonByText(el, 'Customer')!.click();
    await fixture.whenStable();

    const [username] = Array.from(el.querySelectorAll('input'));
    expect(username.value).toBe('customer-user');
  });

  it('shows login errors', async () => {
    const { fixture, store, el } = setup(LoginComponent);
    store.errorMessage.set('Bad credentials');
    await fixture.whenStable();

    expect(el.querySelector('.notice.error')?.textContent).toContain('Bad credentials');
  });
});

describe('TransactionsComponent', () => {
  it('lists transactions and disables disputes on credits', async () => {
    const { fixture, store, el } = setup(TransactionsComponent);
    store.transactions.set([debitTransaction, creditTransaction]);
    await fixture.whenStable();

    const rows = el.querySelectorAll('tbody tr');
    expect(rows).toHaveLength(2);
    const buttons = el.querySelectorAll<HTMLButtonElement>('tbody button');
    expect(buttons[0].disabled).toBe(false);
    expect(buttons[0].textContent?.trim()).toBe('Dispute');
    expect(buttons[1].disabled).toBe(true);
    expect(buttons[1].textContent?.trim()).toBe('Not eligible');
  });

  it('starts a dispute for the clicked transaction', async () => {
    const { fixture, store, el } = setup(TransactionsComponent);
    store.transactions.set([debitTransaction]);
    await fixture.whenStable();

    el.querySelector<HTMLButtonElement>('tbody button')!.click();

    expect(store.selectedTransaction()).toBe(debitTransaction);
    expect(store.activeTab()).toBe('new-dispute');
  });
});

describe('SubmitDisputeComponent', () => {
  it('prefills the form from the selected transaction and submits it', async () => {
    const { fixture, api, el } = setup(SubmitDisputeComponent, (store) => {
      store.transactions.set([debitTransaction]);
      store.selectedTransaction.set(debitTransaction);
    });
    await fixture.whenStable();

    const inputs = Array.from(el.querySelectorAll('input'));
    expect(inputs[0].value).toBe('TX-1');
    expect(inputs[1].value).toBe('ACC-1001');

    el.querySelector('form')!.dispatchEvent(new Event('submit'));
    await fixture.whenStable();

    expect(api.createDispute).toHaveBeenCalledWith(expect.objectContaining({
      transactionReference: 'TX-1',
      disputedAmount: 100,
      reasonCode: 'MERCHANT_ERROR',
      description: 'Dispute for Acme',
    }));
  });

  it('shows a validation error when nothing is selected', async () => {
    const { fixture, store, el } = setup(SubmitDisputeComponent);
    await fixture.whenStable();

    el.querySelector('form')!.dispatchEvent(new Event('submit'));
    await fixture.whenStable();

    expect(store.errorMessage()).toContain('select a transaction');
  });
});

describe('TrackDisputeComponent', () => {
  async function render(status: string) {
    const ctx = setup(TrackDisputeComponent);
    ctx.store.disputes.set([dispute({ status })]);
    ctx.store.selectedDispute.set(dispute({ status }));
    await ctx.fixture.whenStable();
    return ctx;
  }

  it('offers review on an open dispute only', async () => {
    const { el } = await render('OPEN');

    expect(buttonByText(el, 'Review dispute')).toBeDefined();
    expect(buttonByText(el, 'Approve dispute')).toBeUndefined();
    expect(buttonByText(el, 'Reverse dispute')).toBeUndefined();
  });

  it('offers approval once the dispute is in review', async () => {
    const { el } = await render('IN_REVIEW');

    expect(buttonByText(el, 'Approve dispute')).toBeDefined();
  });

  it('offers reversal only after approval, and posts the chosen reason', async () => {
    const { fixture, api, store, el } = await render('PROVISIONAL_CREDIT_ISSUED');
    store.currentUsername.set('lead-user');

    expect(buttonByText(el, 'Review dispute')).toBeUndefined();
    buttonByText(el, 'Reverse dispute')!.click();
    await fixture.whenStable();

    expect(api.reverseDispute).toHaveBeenCalledWith(7, 'MERCHANT_ERROR');
  });

  it('shows the audit trail and an empty state', async () => {
    const { fixture, store, el } = await render('OPEN');
    expect(el.textContent).toContain('No audit events recorded yet.');

    store.disputeHistory.set([{ id: 1, disputeId: 7, action: 'CREATED', actor: 'customer-user' }]);
    await fixture.whenStable();

    expect(el.querySelectorAll('.history-item')).toHaveLength(1);
    expect(el.textContent).toContain('customer-user');
  });
});

describe('DisputeHistoryComponent', () => {
  it('filters disputes by status', async () => {
    const { fixture, store, el } = setup(DisputeHistoryComponent);
    store.disputes.set([dispute({ id: 1, disputeId: 'A' }), dispute({ id: 2, disputeId: 'B', status: 'RESOLVED' })]);
    await fixture.whenStable();
    expect(el.querySelectorAll('tbody tr')).toHaveLength(2);

    buttonByText(el, 'Resolved')!.click();
    await fixture.whenStable();

    const rows = el.querySelectorAll('tbody tr');
    expect(rows).toHaveLength(1);
    expect(rows[0].textContent).toContain('B');
  });
});
