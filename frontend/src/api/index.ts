// Service layer entry point. Views import services from here, never the raw client.
import {createClient, type ApiClient} from './client.ts';
import {authService} from './services/auth.ts';
import {careService} from './services/care.ts';
import {healthService} from './services/health.ts';

export {ApiError, createClient} from './client.ts';
export type {ApiClient, ClientOptions, Credentials, RequestOptions} from './client.ts';
export type * from './types.ts';

export function createApi(client: ApiClient) {
  return {client, auth: authService(client), care: careService(client), health: healthService(client)};
}

export type Api = ReturnType<typeof createApi>;

// Empty base URL means same origin: the Vite dev server and nginx proxy /api and
// /actuator to the backend. Set VITE_API_URL only when the API lives on another host.
export const api = createApi(createClient({baseUrl: import.meta.env?.VITE_API_URL ?? ''}));
