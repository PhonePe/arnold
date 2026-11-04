const next = require('next');
const config = require('config');
const express = require('express');
const proxy = require('express-http-proxy');
const path = require('path');
const { initializeClient, middlewares } = require('@phonepe/olympus-node-client');

const noopMiddleware = (_req, _res, next) => next();
const ensureMiddleware = (fn) =>
  typeof fn === 'function' ? fn : noopMiddleware;

const humanTokenMiddleware = ensureMiddleware(
  (middlewares?.withHumanUserToken) ||
    (middlewares?.isAuthenticated),
);
const authMiddleware = ensureMiddleware(
  middlewares?.isAuthenticated,
);
const passThroughMiddleware =
  typeof (middlewares?.isAuthenticatedPassThrough) === 'function'
    ? middlewares.isAuthenticatedPassThrough
    : null;

const dev = process.env.NODE_ENV === 'development' || config.get('application.devMode');
const serverHost = config.get('server.host');
const serverPort = config.get('server.port');
const app = next({ dev, hostname: serverHost, port: serverPort });
const handle = app.getRequestHandler();

if (dev) process.env.NODE_TLS_REJECT_UNAUTHORIZED = '0';

const DEFAULT_LOGIN_PATH = '/login';
const DEFAULT_AUTH_BASE_PATH = '/auth';
const normalizeBaseUrl = (url) => {
  if (!url) {
    return 'http://localhost:3000/';
  }

  return url.endsWith('/') ? url : `${url}/`;
};
const normalizeBasePath = (path = DEFAULT_AUTH_BASE_PATH) =>
  path.startsWith('/') ? path : `/${path}`;
const normalizeSubPath = (path = DEFAULT_LOGIN_PATH) =>
  path.replace(/^\//, '');
const getModeParam = (req) => {
  const { mode } = req.query || {};
  if (Array.isArray(mode)) {
    return mode[0];
  }
  return mode;
};

const shouldBypassAuth = (req) => {
  if (req.method === 'OPTIONS') {
    return true;
  }
  const path = req.path || req.url || '';
  if (path === '/healthcheck') return true;
  if (path.startsWith('/auth')) return true;
  if (path.startsWith('/redirect')) return true;
  if (path.startsWith('/_next')) return true;
  if (path.startsWith('/favicon')) return true;
  if (path.startsWith('/docs')) return true;
  return false;
};

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

const parseCookies = (header) => {
  if (!header) {
    return {};
  }

  return header.split(';').reduce((acc, part) => {
    if (!part) {
      return acc;
    }
    const [rawName, ...rawValue] = part.split('=');
    if (!rawName) {
      return acc;
    }
    const name = rawName.trim();
    const value = rawValue.join('=').trim();
    try {
      acc[name] = decodeURIComponent(value);
    } catch (_error) {
      acc[name] = value;
    }
    return acc;
  }, {});
};

const parseRequestCookies = (req, _res, next) => {
  const header = req.headers.cookie;
  req.cookies = parseCookies(header);
  return next();
};

const createLoginUrlBuilder = (olympusConfig) => {
  const baseUrl = normalizeBaseUrl(
    olympusConfig.nodeServerUrl || olympusConfig.homePage,
  );
  const basePath = normalizeBasePath(
    olympusConfig.routerConfig?.basePath || DEFAULT_AUTH_BASE_PATH,
  ).replace(/\/$/, '');
  const loginPath = normalizeSubPath(
    olympusConfig.routerConfig?.loginPath || DEFAULT_LOGIN_PATH,
  );
  const loginEndpoint = `${basePath}/${loginPath}`;

  return (req) => {
    let successRedirect = baseUrl;
    try {
      successRedirect = new URL(req.originalUrl || '/', baseUrl).toString();
    } catch (error) {
      console.error('Failed to resolve success redirect URL', error);
    }

    const loginUrl = new URL(loginEndpoint, baseUrl);
    loginUrl.searchParams.set('successRedirect', successRedirect);
    loginUrl.searchParams.set('loginRedirectUrl', successRedirect);

    const modeParam = getModeParam(req);
    if (modeParam) {
      loginUrl.searchParams.set('mode', modeParam);
    }

    return loginUrl.toString();
  };
};

const GITLAB_WEBHOOK_PATH = '/gitlab-callbacks/v1/events';

// GitLab webhooks carry no Olympus credentials; the backend validates X-Gitlab-Token itself.
const isGitLabWebhook = (req) =>
  req.method === 'POST' && req.path === GITLAB_WEBHOOK_PATH && Boolean(req.headers['x-gitlab-token']);

// Session users get the Olympus token injected; requests without a session but
// carrying their own Authorization header are forwarded to the backend as-is.
const apiAuthMiddleware = (req, res, next) => {
  if (isGitLabWebhook(req)) {
    return next();
  }
  if (!passThroughMiddleware) {
    return req.headers.authorization ? next() : humanTokenMiddleware(req, res, next);
  }

  return passThroughMiddleware(req, res, (error) => {
    if (!error) {
      return humanTokenMiddleware(req, res, next);
    }
    if (req.headers.authorization) {
      return next();
    }
    return res.status(401).json(error);
  });
};

const createAuthGuard = (loginUrlBuilder) => (req, res, next) => {
  if (shouldBypassAuth(req)) {
    return next();
  }

  if (!passThroughMiddleware) {
    return authMiddleware(req, res, next);
  }

  return passThroughMiddleware(req, res, (error) => {
    if (error && error.errorCode === 'PR004') {
      const loginUrl = loginUrlBuilder(req);
      return res.redirect(loginUrl);
    }

    if (error) {
      console.error('Unexpected auth error', error);
      return res.status(401).json(error);
    }

    return next();
  });
};

app
  .prepare()
  .then(() => {
    const server = express();
    const olympusConfig = config.get('olympusIMClientConfig');
    const apiTarget = resolveApiTarget();
    const loginUrlBuilder = createLoginUrlBuilder(olympusConfig);
    const authGuard = createAuthGuard(loginUrlBuilder);

    const systemUserLoginCallback = (serverLoginStatus) => {
      console.log("Server login status", serverLoginStatus);
    };
    const getMultiSiteConfig = async (mode) => {
      return undefined;
    };
    const options = { systemUserLoginCallback, getMultiSiteConfig };

    initializeClient(server, olympusConfig, options);

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
      apiAuthMiddleware,
      proxy(apiTarget, {
        proxyReqOptDecorator: (proxyReqOpts, srcReq) => {
          if (srcReq.headers.authorization) {
            proxyReqOpts.headers.authorization = srcReq.headers.authorization;
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

    server.use(authGuard);

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
