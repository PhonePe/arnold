/*
 * Copyright (c) 2026 Original Author(s), PhonePe Limited
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

const assert = require('node:assert/strict');
const { test } = require('node:test');
const { createAuth, signJwt, verifyJwt } = require('../auth');

const SECRET = '0123456789abcdef0123456789abcdef';

test('session tokens round-trip and reject tampering, expiry and wrong audience', () => {
  const now = Math.floor(Date.now() / 1000);
  const token = signJwt({ iss: 'arnold-ui', aud: 'arnold', sub: 'github:1', exp: now + 60 }, SECRET);
  assert.equal(verifyJwt(token, SECRET, { issuer: 'arnold-ui', audience: 'arnold' }).sub, 'github:1');
  assert.equal(verifyJwt(token, `${SECRET}x`), null);
  assert.equal(verifyJwt(`${token.slice(0, -2)}xx`, SECRET), null);
  assert.equal(verifyJwt(token, SECRET, { audience: 'other' }), null);
  const expired = signJwt({ sub: 'x', exp: now - 1 }, SECRET);
  assert.equal(verifyJwt(expired, SECRET), null);
});

test('auth configuration is validated', () => {
  assert.throws(() => createAuth({ mode: 'oauth', sessionSecret: 'short', providers: [] }), /32 bytes/);
  assert.throws(() => createAuth({ mode: 'oauth', sessionSecret: SECRET, providers: [] }), /at least one/);
  assert.throws(() => createAuth({ mode: 'oauth', sessionSecret: SECRET, providers: [{ id: 'x', type: 'saml' }] }), /Unsupported/);
  assert.equal(createAuth({ mode: 'dev' }).mode, 'dev');
});

test('session cookie is exposed as bearer only when valid', () => {
  const auth = createAuth({
    mode: 'oauth',
    sessionSecret: SECRET,
    providers: [{ id: 'github', type: 'github', clientId: 'id', clientSecret: 'secret' }],
  });
  const now = Math.floor(Date.now() / 1000);
  const token = signJwt({ iss: 'arnold-ui', aud: 'arnold', sub: 'github:1', exp: now + 60 }, SECRET);
  assert.equal(auth.bearer({ headers: { cookie: `arnold_session=${token}` } }), token);
  assert.equal(auth.bearer({ headers: { cookie: 'arnold_session=forged.token.value' } }), null);
  assert.equal(auth.bearer({ headers: {} }), null);
});

test('login redirects to the provider with PKCE and a signed flow cookie', async () => {
  const auth = createAuth({
    mode: 'oauth',
    publicUrl: 'https://arnold.example.com',
    sessionSecret: SECRET,
    providers: [{ id: 'github', type: 'github', clientId: 'client', clientSecret: 'secret' }],
  });
  const handlers = {};
  auth.routes({ get: (route, handler) => { handlers[route] = handler; }, post() {} });
  const headers = {};
  let location;
  await new Promise((resolve) => handlers['/auth/login'](
    { query: { successRedirect: 'https://evil.example.org/steal' }, headers: {} },
    { getHeader: (name) => headers[name], setHeader: (name, value) => { headers[name] = value; }, redirect: (url) => { location = url; resolve(); } },
    (error) => { throw error; },
  ));
  const url = new URL(location);
  assert.equal(url.origin, 'https://github.com');
  assert.equal(url.searchParams.get('client_id'), 'client');
  assert.equal(url.searchParams.get('redirect_uri'), 'https://arnold.example.com/auth/callback/github');
  assert.equal(url.searchParams.get('code_challenge_method'), 'S256');
  const flowCookie = headers['Set-Cookie'][0];
  assert.match(flowCookie, /^arnold_auth_flow=.*HttpOnly; SameSite=Lax/);
  assert.match(flowCookie, /Secure/);
  const flow = verifyJwt(decodeURIComponent(flowCookie.split(';')[0].split('=')[1]), SECRET);
  assert.equal(flow.redirect, '/');
  assert.equal(flow.state, url.searchParams.get('state'));
});
