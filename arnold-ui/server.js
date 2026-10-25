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

const next = require('next');
const config = require('config');
const express = require('express');
const proxy = require('express-http-proxy');
const path = require('path');
const { createAuth } = require('./auth');

const dev = process.env.NODE_ENV === 'development' || config.get('application.devMode');
const serverHost = config.get('server.host');
const serverPort = config.get('server.port');
const app = next({ dev, hostname: serverHost, port: serverPort });
const handle = app.getRequestHandler();

if (dev) process.env.NODE_TLS_REJECT_UNAUTHORIZED = '0';

const resolveApiTarget = () => {
  // In deployment, check for API URL in environment variables
  const apiUrl = process.env.ARNOLD_API_URL;

  if (apiUrl) {
    // If it doesn't have protocol, add https (default for production)
    // Remove any trailing slashes
    let url = apiUrl.trim();
    if (url.endsWith('/')) {
      url = url.slice(0, -1);
    }
    if (!url.startsWith('http')) {
      url = `https://${url}`;
    }
    console.log(`[API Target] Using from environment variable: ${url}`);
    return url;
  }

  // Fall back to config file
  let apiHost = config.get('api.host');
  const apiDomain = config.get('api.domain');
  const apiPort = config.get('api.port');

  console.log(`[API Target] Config values - host: ${apiHost}, domain: ${apiDomain}, port: ${apiPort}`);

  // If api.host is localhost, construct from domain + port instead
  if (apiHost && apiHost.startsWith('localhost') && apiDomain && apiPort) {
    apiHost = `${apiDomain}:${apiPort}`;
    console.log(`[API Target] Constructed from domain+port: ${apiHost}`);
  }

  // If apiHost already has protocol, use it as-is
  if (/^https?:\/\//i.test(apiHost)) {
    console.log(`[API Target] Using from config (has protocol): ${apiHost}`);
    return apiHost;
  }

  // Determine protocol and port based on hostname
  // Localhost = HTTP, everything else = HTTPS (deployed backends use HTTPS)
  const isLocalhost = apiHost && (apiHost.includes('localhost') || apiHost.includes('127.0.0.1'));

  if (isLocalhost) {
    // Local development: use HTTP with specified port
    const apiTarget = `http://${apiHost}`;
    console.log(`[API Target] Local development: ${apiTarget}`);
    return apiTarget;
  } else {
    // Deployed backend: use HTTPS on port 443 (default HTTPS port)
    // Backend is on HTTPS:443, not HTTP:8080
    // Remove port 8080 if present (backend is on 443, not 8080)
    const hostWithoutPort = apiDomain || apiHost.split(':')[0];
    const apiTarget = `https://${hostWithoutPort}`;
    console.log(`[API Target] Deployed backend (HTTPS:443): ${apiTarget}`);
    return apiTarget;
  }
};

const isPublicPath = (req) => {
  if (req.method === 'OPTIONS') return true;
  const requestPath = req.path || req.url || '';
  return requestPath === '/healthcheck'
    || requestPath.startsWith('/auth')
    || requestPath.startsWith('/_next')
    || requestPath.startsWith('/favicon')
    || requestPath.startsWith('/docs');
};

const createPageGuard = (auth) => (req, res, next) => {
  if (auth.mode === 'dev' || isPublicPath(req) || auth.session(req)) return next();
  const target = encodeURIComponent(req.originalUrl || '/');
  return res.redirect(`/auth/login?successRedirect=${target}`);
};

const requireApiSession = (auth) => (req, res, next) => {
  if (auth.mode === 'dev' || auth.bearer(req)) return next();
  return res.status(401).json({ success: false, errorCode: 'SESSION_EXPIRED', errorMessage: 'Sign in required' });
};

app
  .prepare()
  .then(() => {
    const server = express();
    const auth = createAuth(config.get('auth'));
    const apiTarget = resolveApiTarget();
    if (auth.mode === 'dev') {
      console.warn('[auth] Development mode: no sign-in; the backend must have auth.dev.enabled=true');
    }
    auth.routes(server);

    server.get('/healthcheck', (_req, res) =>
      res.status(200).json({ status: 'ok' }),
    );

    // Serve static documentation from MkDocs build
    const docsPath = path.join(__dirname, 'public', 'docs');
    server.use('/docs', express.static(docsPath, {
      index: 'index.html',
      extensions: ['html'],
    }));

    // Configure proxy with error handling
    console.log(`[Proxy] Setting up /api proxy to: ${apiTarget}`);

    server.use(
      '/api',
      requireApiSession(auth),
      proxy(apiTarget, {
        proxyReqOptDecorator: (proxyReqOpts, srcReq) => {
          delete proxyReqOpts.headers.authorization;
          delete proxyReqOpts.headers.cookie;
          const token = auth.bearer(srcReq);
          if (token) {
            proxyReqOpts.headers.authorization = `Bearer ${token}`;
          }
          return proxyReqOpts;
        },
        // Handle proxy errors gracefully
        proxyErrorHandler: (err, res, next) => {
          console.error('[Proxy Error]', err.message, err.code, `Target: ${apiTarget}`);
          if (err.code === 'ECONNREFUSED') {
            res.status(503).json({
              error: 'Backend service unavailable',
              message: 'Could not connect to backend API',
              target: apiTarget
            });
          } else if (err.code === 'ECONNRESET' || err.code === 'ETIMEDOUT') {
            res.status(504).json({
              error: 'Backend timeout',
              message: 'Backend did not respond in time'
            });
          } else {
            res.status(502).json({
              error: 'Proxy error',
              message: err.message,
              code: err.code
            });
          }
        },
      }),
    );

    server.use(createPageGuard(auth));

    server.all('*', (req, res) => handle(req, res));

    server.listen(serverPort, (err) => {
      if (err) {
        throw err;
      }

      console.log(`> Ready on http://${serverHost}:${serverPort}`);
    });
  })
  .catch((err) => {
    console.error('Error starting server', err);
    process.exit(1);
  });
