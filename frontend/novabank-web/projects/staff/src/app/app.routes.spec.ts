import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { routes } from './app.routes';

/** The real route table: which layout, page and tab title each URL gets. */
describe('staff routes', () => {
  async function open(url: string) {
    TestBed.configureTestingModule({ providers: [provideRouter(routes)] });
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl(url);
    const page = harness.fixture.nativeElement as HTMLElement;
    return {
      layout: page.querySelector('nb-signed-out-layout')?.tagName,
      heading: page.querySelector('h1')?.textContent?.trim(),
      strip: page.querySelector('.strip')?.textContent,
      title: document.title,
    };
  }

  it('"/" is staff sign-in, in the back-office layout', async () => {
    const page = await open('/');
    expect(page.layout).toBe('NB-SIGNED-OUT-LAYOUT');
    expect(page.heading).toBe('Sign in to the back-office');
    expect(page.title).toBe('Sign in — NovaBank back-office');
  });

  it('every page says it is the internal back-office', async () => {
    const page = await open('/sign-in');
    expect(page.strip).toContain('NovaBank staff back-office · Internal');
  });

  it('says sign-in is not available yet, as a polite status', async () => {
    await open('/sign-in');
    const alert = document.querySelector('nb-alert');
    expect(alert?.getAttribute('role')).toBe('status');
    expect(alert?.textContent).toContain("Staff sign-in isn't available yet.");
  });

  it('an unknown address is "Page not found", with a way back', async () => {
    const page = await open('/no-such-page');
    expect(page.heading).toBe('Page not found');
    expect(page.title).toBe('Page not found — NovaBank back-office');
    expect(document.querySelector('a[href="/sign-in"]')).not.toBeNull();
  });
});
