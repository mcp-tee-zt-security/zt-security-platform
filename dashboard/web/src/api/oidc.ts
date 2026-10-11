import Keycloak from 'keycloak-js';

// Public browser client: authorization code + PKCE. Tokens stay in adapter memory.
const issuer = new URL(import.meta.env.VITE_OIDC_ISSUER || 'http://localhost:8089/realms/zt-mcp-demo');
const realmPath = issuer.pathname.match(/^(.*)\/realms\/([^/]+)\/?$/);
if (!realmPath) throw new Error('OIDC issuer must be a Keycloak realm URL');
const keycloak = new Keycloak({url:issuer.origin + realmPath[1],realm:realmPath[2],
  clientId:import.meta.env.VITE_OIDC_CLIENT_ID || 'zt-dashboard'});
let initialization:Promise<boolean>|undefined;
const redirectUri = () => window.location.origin + window.location.pathname + '?zt_login=mcp';
export function initializeOidc() {
  return initialization ??= keycloak.init({pkceMethod:'S256',responseMode:'query',
    checkLoginIframe:false,redirectUri:redirectUri()});
}
export async function loginOidc() {
  await initializeOidc();
  await keycloak.login({redirectUri:redirectUri(),prompt:'login'});
}
export async function logoutOidc() {
  await initializeOidc();
  try { await keycloak.logout({redirectUri:redirectUri()}); }
  finally { keycloak.clearToken(); }
}
export async function oidcToken():Promise<string> {
  await initializeOidc();
  if (!keycloak.authenticated) throw new Error('Keycloak login required; no development-key fallback');
  try { await keycloak.updateToken(30); }
  catch { keycloak.clearToken(); throw new Error('Keycloak session expired; sign in again'); }
  if (!keycloak.token) throw new Error('Keycloak access token unavailable');
  return keycloak.token;
}
export function oidcUser() { return keycloak.tokenParsed?.preferred_username || ''; }
