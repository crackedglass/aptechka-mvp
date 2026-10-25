import {useState} from 'react';
import type {Me} from './api/index.ts';
import {AuthScreen} from './live/AuthScreen.tsx';
import {LiveApp} from './live/LiveApp.tsx';

// Everything lives on the backend: sign in first, then the account-backed app.
// Credentials stay in memory, so a reload asks again.
export function Root() {
  const [me, setMe] = useState<Me | null>(null);
  return me ? <LiveApp initialMe={me} onLogout={() => setMe(null)} /> : <AuthScreen onSignedIn={setMe} />;
}
