/*
 * Copyright (c) 2026 Original Author(s), PhonePe India Pvt. Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

'use strict';

const crypto = require('node:crypto');

const SESSION_COOKIE = 'arnold_session';
const FLOW_COOKIE = 'arnold_auth_flow';
const FLOW_TTL_SECONDS = 600;

const b64url = (input) => Buffer.from(input).toString('base64url');

/** Signs an HS256 JWT. */
function signJwt(claims, secret) {
  const header = b64url(JSON.stringify({ alg: 'HS256', typ: 'JWT' }));
  const payload = b64url(JSON.stringify(claims));
  const signature = crypto.createHmac('sha256', secret).update(`${header}.${payload}`).digest('base64url');
  return `${header}.${payload}.${signature}`;
}

/** Verifies an HS256 JWT and returns its claims, or null when invalid or expired. */
function verifyJwt(token, secret, { issuer, audience } = {}) {
  if (typeof token !== 'string') return null;
  const parts = token.split('.');
  if (parts.length !== 3) return null;
  const expected = crypto.createHmac('sha256', secret).update(`${parts[0]}.${parts[1]}`).digest();
  const actual = Buffer.from(parts[2], 'base64url');
  if (actual.length !== expected.length || !crypto.timingSafeEqual(actual, expected)) return null;
  let header;
  let claims;
  try {
    header = JSON.parse(Buffer.from(parts[0], 'base64url').toString('utf8'));
    claims = JSON.parse(Buffer.from(parts[1], 'base64url').toString('utf8'));
  } catch {
    return null;
  }
  if (header.alg !== 'HS256') return null;
  const now = Math.floor(Date.now() / 1000);
  if (typeof claims.exp !== 'number' || claims.exp <= now) return null;
  if (issuer && claims.iss !== issuer) return null;
  if (audience && claims.aud !== audience) return null;
  return claims;
}

function parseCookies(header) {
  const cookies = {};
  if (!header) return cookies;
  for (const part of header.split(';')) {
    const index = part.indexOf('=');
    if (index < 0) continue;
    const name = part.slice(0, index).trim();
    const value = part.slice(index + 1).trim();
    try {
      cookies[name] = decodeURIComponent(value);
    } catch {
      cookies[name] = value;
    }
  }
  return cookies;
}

function serializeCookie(name, value, { maxAge, secure, path = '/' }) {
  const attributes = [`${name}=${encodeURIComponent(value)}`, `Path=${path}`, 'HttpOnly', 'SameSite=Lax'];
  if (maxAge !== undefined) attributes.push(`Max-Age=${maxAge}`);
  if (secure) attributes.push('Secure');
  return attributes.join('; ');
}

function appendCookie(res, cookie) {
  const existing = res.getHeader('Set-Cookie');
  const list = existing ? (Array.isArray(existing) ? existing : [existing]) : [];
  res.setHeader('Set-Cookie', [...list, cookie]);
}

const pkcePair = () => {
  const verifier = crypto.randomBytes(32).toString('base64url');
  const challenge = crypto.createHash('sha256').update(verifier).digest('base64url');
  return { verifier, challenge };
};

async function fetchJson(url, options = {}) {
  const response = await fetch(url, { ...options, signal: AbortSignal.timeout(15000) });
  if (!response.ok) {
    throw new Error(`Request to ${new URL(url).host} failed with status ${response.status}`);
  }
  return response.json();
}

/** Generic OpenID Connect provider (Google, GitLab, Keycloak, Okta, Auth0, ...). */
function oidcProvider(definition) {
  let discovery;
  const discover = async () => {
    if (!discovery) {
      const issuer = definition.issuer.replace(/\/$/, '');
      discovery = await fetchJson(`${issuer}/.well-known/openid-configuration`);
    }
    return discovery;
  };
  return {
    id: definition.id,
    label: definition.label || definition.id,
    async authorizationUrl({ redirectUri, state, challenge }) {
      const metadata = await discover();
      const url = new URL(metadata.authorization_endpoint);
      url.searchParams.set('response_type', 'code');
      url.searchParams.set('client_id', definition.clientId);
      url.searchParams.set('redirect_uri', redirectUri);
      url.searchParams.set('scope', (definition.scopes || ['openid', 'email', 'profile']).join(' '));
      url.searchParams.set('state', state);
      url.searchParams.set('code_challenge', challenge);
      url.searchParams.set('code_challenge_method', 'S256');
      return url.toString();
    },
    async identity({ code, redirectUri, verifier }) {
      const metadata = await discover();
      const tokens = await fetchJson(metadata.token_endpoint, {
        method: 'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded', Accept: 'application/json' },
        body: new URLSearchParams({
          grant_type: 'authorization_code',
          code,
          redirect_uri: redirectUri,
          client_id: definition.clientId,
          client_secret: definition.clientSecret,
          code_verifier: verifier,
        }),
      });
      const profile = await fetchJson(metadata.userinfo_endpoint, {
        headers: { Authorization: `Bearer ${tokens.access_token}`, Accept: 'application/json' },
      });
      if (definition.requireVerifiedEmail !== false && profile.email && profile.email_verified === false) {
        throw new Error('Email address is not verified');
      }
      const groupsClaim = definition.groupsClaim || 'groups';
      return {
        subject: String(profile.sub),
        email: profile.email,
        name: profile.name || profile.preferred_username,
        groups: Array.isArray(profile[groupsClaim]) ? profile[groupsClaim].map(String) : [],
      };
    },
  };
}

/** GitHub (github.com or GitHub Enterprise) OAuth app. */
function githubProvider(definition) {
  const webUrl = (definition.baseUrl || 'https://github.com').replace(/\/$/, '');
  const apiUrl = (definition.apiUrl || (webUrl === 'https://github.com' ? 'https://api.github.com' : `${webUrl}/api/v3`))
    .replace(/\/$/, '');
  const api = (path, token) => fetchJson(`${apiUrl}${path}`, {
    headers: { Authorization: `Bearer ${token}`, Accept: 'application/vnd.github+json' },
  });
  return {
    id: definition.id,
    label: definition.label || 'GitHub',
    async authorizationUrl({ redirectUri, state, challenge }) {
      const url = new URL(`${webUrl}/login/oauth/authorize`);
      url.searchParams.set('client_id', definition.clientId);
      url.searchParams.set('redirect_uri', redirectUri);
      url.searchParams.set('scope', (definition.scopes || ['read:user', 'user:email', 'read:org']).join(' '));
      url.searchParams.set('state', state);
      url.searchParams.set('code_challenge', challenge);
      url.searchParams.set('code_challenge_method', 'S256');
      return url.toString();
    },
    async identity({ code, redirectUri, verifier }) {
      const tokens = await fetchJson(`${webUrl}/login/oauth/access_token`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded', Accept: 'application/json' },
        body: new URLSearchParams({
          client_id: definition.clientId,
          client_secret: definition.clientSecret,
          code,
          redirect_uri: redirectUri,
          code_verifier: verifier,
        }),
      });
      if (!tokens.access_token) throw new Error('GitHub did not return an access token');
      const user = await api('/user', tokens.access_token);
      const emails = await api('/user/emails', tokens.access_token).catch(() => []);
      const primary = emails.find((entry) => entry.primary && entry.verified) || emails.find((entry) => entry.verified);
      const orgs = await api('/user/orgs', tokens.access_token).catch(() => []);
      const groups = orgs.map((org) => org.login);
      if (definition.allowedOrganizations?.length
        && !groups.some((org) => definition.allowedOrganizations.includes(org))) {
        throw new Error('User is not a member of an allowed GitHub organization');
      }
      return {
        subject: String(user.id),
        email: primary?.email || user.email || undefined,
        name: user.name || user.login,
        groups,
      };
    },
  };
}

const PROVIDER_FACTORIES = { oidc: oidcProvider, github: githubProvider };

/**
 * Builds the authentication layer for the UI server.
 *
 * Sessions are HS256 JWTs (issuer `arnold-ui`, audience `arnold`) held in an http-only cookie and forwarded to the
 * backend as bearer tokens; the backend must trust the same issuer and secret.
 */
function createAuth(settings) {
  const mode = settings.mode || 'oauth';
  const publicUrl = (settings.publicUrl || 'http://localhost:3000').replace(/\/$/, '');
  const secure = settings.secureCookies ?? publicUrl.startsWith('https://');
  const sessionTtl = settings.sessionTtlSeconds || 12 * 3600;
  const issuer = settings.issuer || 'arnold-ui';
  const audience = settings.audience || 'arnold';
  const allowedDomains = (settings.allowedEmailDomains || []).map((domain) => domain.toLowerCase());
  const secret = settings.sessionSecret;

  if (mode === 'dev') {
    return {
      mode,
      providers: [],
      routes: (_server) => {},
      session: () => ({ sub: 'dev' }),
      bearer: () => null,
    };
  }
  if (!secret || Buffer.byteLength(secret) < 32) {
    throw new Error('auth.sessionSecret must be at least 32 bytes');
  }
  const providers = (settings.providers || []).map((definition) => {
    const factory = PROVIDER_FACTORIES[definition.type];
    if (!factory) throw new Error(`Unsupported auth provider type: ${definition.type}`);
    if (!definition.id || !definition.clientId || !definition.clientSecret) {
      throw new Error(`Auth provider ${definition.id || definition.type} requires id, clientId and clientSecret`);
    }
    return factory(definition);
  });
  if (providers.length === 0) throw new Error('Configure at least one auth provider or set auth.mode to dev');
  const providersById = new Map(providers.map((provider) => [provider.id, provider]));

  const session = (req) => verifyJwt(parseCookies(req.headers.cookie)[SESSION_COOKIE], secret, { issuer, audience });
  const safeRedirect = (target) => {
    try {
      const url = new URL(target || '/', publicUrl);
      return url.origin === publicUrl ? `${url.pathname}${url.search}${url.hash}` : '/';
    } catch {
      return '/';
    }
  };
  const redirectUri = (providerId) => `${publicUrl}/auth/callback/${encodeURIComponent(providerId)}`;

  const startLogin = async (provider, req, res) => {
    const state = crypto.randomBytes(16).toString('base64url');
    const { verifier, challenge } = pkcePair();
    const flow = signJwt({
      state,
      verifier,
      provider: provider.id,
      redirect: safeRedirect(req.query.successRedirect),
      exp: Math.floor(Date.now() / 1000) + FLOW_TTL_SECONDS,
    }, secret);
    appendCookie(res, serializeCookie(FLOW_COOKIE, flow, { maxAge: FLOW_TTL_SECONDS, secure, path: '/auth' }));
    res.redirect(await provider.authorizationUrl({ redirectUri: redirectUri(provider.id), state, challenge }));
  };

  const loginPage = (req) => {
    const target = encodeURIComponent(req.query.successRedirect || '/');
    const escape = (value) => String(value).replace(/[&<>"']/g, (c) => `&#${c.charCodeAt(0)};`);
    const buttons = providers.map((provider) =>
      `<a href="/auth/login/${encodeURIComponent(provider.id)}?successRedirect=${target}">Continue with ${escape(provider.label)}</a>`)
      .join('');
    return `<!doctype html><html><head><meta charset="utf-8"><title>Sign in · Arnold</title>
<style>body{font-family:system-ui,sans-serif;display:grid;place-items:center;min-height:100vh;margin:0;background:#0b0d10;color:#e6e6e6}
main{display:flex;flex-direction:column;gap:12px;min-width:280px}h1{font-size:20px}a{padding:10px 14px;border:1px solid #333;border-radius:8px;color:inherit;text-decoration:none}a:hover{background:#1a1d22}</style>
</head><body><main><h1>Sign in to Arnold</h1>${buttons}</main></body></html>`;
  };

  const routes = (server) => {
    server.get('/auth/login', (req, res, next) => {
      if (providers.length === 1) return startLogin(providers[0], req, res).catch(next);
      return res.type('html').send(loginPage(req));
    });
    server.get('/auth/login/:provider', (req, res, next) => {
      const provider = providersById.get(req.params.provider);
      if (!provider) return res.status(404).send('Unknown provider');
      return startLogin(provider, req, res).catch(next);
    });
    server.get('/auth/callback/:provider', async (req, res) => {
      const flow = verifyJwt(parseCookies(req.headers.cookie)[FLOW_COOKIE], secret);
      appendCookie(res, serializeCookie(FLOW_COOKIE, '', { maxAge: 0, secure, path: '/auth' }));
      const provider = providersById.get(req.params.provider);
      if (!flow || !provider || flow.provider !== provider.id || typeof req.query.state !== 'string'
        || req.query.state !== flow.state || typeof req.query.code !== 'string') {
        return res.status(400).send('Sign-in request is invalid or has expired. Please try again.');
      }
      try {
        const identity = await provider.identity({
          code: req.query.code,
          redirectUri: redirectUri(provider.id),
          verifier: flow.verifier,
        });
        const domain = identity.email?.split('@')[1]?.toLowerCase();
        if (allowedDomains.length && !allowedDomains.includes(domain)) {
          return res.status(403).send('Your account is not allowed to sign in to Arnold.');
        }
        const now = Math.floor(Date.now() / 1000);
        const token = signJwt({
          iss: issuer,
          aud: audience,
          sub: `${provider.id}:${identity.subject}`,
          email: identity.email,
          name: identity.name,
          groups: identity.groups,
          iat: now,
          exp: now + sessionTtl,
        }, secret);
        appendCookie(res, serializeCookie(SESSION_COOKIE, token, { maxAge: sessionTtl, secure }));
        return res.redirect(flow.redirect || '/');
      } catch (error) {
        console.error('[auth] Sign-in failed', error.message);
        return res.status(403).send('Sign-in failed. Please try again or contact your administrator.');
      }
    });
    const logout = (_req, res) => {
      appendCookie(res, serializeCookie(SESSION_COOKIE, '', { maxAge: 0, secure }));
      res.redirect('/auth/login');
    };
    server.get('/auth/logout', logout);
    server.post('/auth/logout', logout);
  };

  return {
    mode,
    providers,
    routes,
    session,
    bearer: (req) => parseCookies(req.headers.cookie)[SESSION_COOKIE] && session(req)
      ? parseCookies(req.headers.cookie)[SESSION_COOKIE]
      : null,
  };
}

module.exports = { createAuth, signJwt, verifyJwt, parseCookies };
