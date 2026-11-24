import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { routes } from './app.routes';
import { TokenStorageService } from './features/auth/services/token-storage.service';

/** The real route table: which layout and page each URL gets, signed in or not. */
describe('routes', () => {
  async function open(url: string, signedIn: boolean) {
    TestBed.configureTestingModule({
      providers: [provideRouter(routes), provideHttpClient(), provideHttpClientTesting()],
    });
    if (signedIn) {
      TestBed.inject(TokenStorageService).saveAccessToken('token');
    }
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl(url);
    const root = harness.routeNativeElement?.ownerDocument ?? document;
    return {
      layout: (harness.fixture.nativeElement as HTMLElement).querySelector(
        'nb-main-layout, nb-public-layout',
      )?.tagName,
      heading: (harness.fixture.nativeElement as HTMLElement).querySelector('h1')?.textContent,
      title: root.title,
    };
  }

  it('signed in, "/" is the accounts page in the signed-in layout', async () => {
    const page = await open('/', true);
    expect(page.layout).toBe('NB-MAIN-LAYOUT');
    expect(page.heading).toBe('Your accounts');
  });

  it('signed out, "/" goes to sign in', async () => {
    const page = await open('/', false);
    expect(page.layout).toBe('NB-PUBLIC-LAYOUT');
    expect(page.heading).toBe('Sign in to online banking');
  });

  it('"/register" is the registration page, without the signed-in layout', async () => {
    const page = await open('/register', false);
    expect(page.layout).toBe('NB-PUBLIC-LAYOUT');
    expect(page.heading).toBe('Register for online banking');
  });

  it('signed in, the sign-in page sends you to your accounts', async () => {
    const page = await open('/login', true);
    expect(page.heading).toBe('Your accounts');
  });

  it('unknown URLs end at the start page', async () => {
    const page = await open('/no-such-page', false);
    expect(page.heading).toBe('Sign in to online banking');
  });
});
