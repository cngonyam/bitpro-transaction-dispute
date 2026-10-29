import { TestBed } from '@angular/core/testing';
import { App } from './app';
import { PortalStore } from './portal.store';
import { TransactionDisputeService } from './transaction-dispute.service';
import { createFakeApi, debitTransaction } from './testing/test-data';

describe('App shell', () => {
  async function render() {
    const api = createFakeApi();
    TestBed.configureTestingModule({
      imports: [App],
      providers: [{ provide: TransactionDisputeService, useValue: api }],
    });
    const store = TestBed.inject(PortalStore);
    const fixture = TestBed.createComponent(App);
    await fixture.whenStable();
    return { fixture, store, api, el: fixture.nativeElement as HTMLElement };
  }

  it('shows the login screen until a session exists', async () => {
    const { el } = await render();

    expect(el.querySelector('app-login')).not.toBeNull();
    expect(el.querySelector('nav.tabs')).toBeNull();
  });

  it('signs in, shows the dashboard and navigates between tabs', async () => {
    const { fixture, el } = await render();

    el.querySelector('form')!.dispatchEvent(new Event('submit'));
    await fixture.whenStable();

    expect(el.querySelector('app-login')).toBeNull();
    expect(el.querySelector('app-transactions')).not.toBeNull();
    expect(el.textContent).toContain('Welcome back, agent-user.');

    const tabs = Array.from(el.querySelectorAll<HTMLButtonElement>('nav.tabs button'));
    expect(tabs).toHaveLength(5);
    tabs[3].click();
    await fixture.whenStable();

    expect(el.querySelector('app-dispute-history')).not.toBeNull();
    expect(el.querySelector('app-transactions')).toBeNull();
  });

  it('logs out back to the login screen', async () => {
    const { fixture, store, el } = await render();
    store.transactions.set([debitTransaction]);
    store.isLoggedIn.set(true);
    await fixture.whenStable();

    el.querySelector<HTMLButtonElement>('.logout-button')!.click();
    await fixture.whenStable();

    expect(el.querySelector('app-login')).not.toBeNull();
    expect(store.transactions()).toEqual([]);
  });
});
