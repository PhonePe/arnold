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

package com.phonepe.arnold.core.clients;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Selects HTTP proxies from the conventional proxy environment variables.
 * Lower-case variables take precedence over their upper-case equivalents.
 */
public final class EnvironmentProxySelector extends ProxySelector {

    private static final String HTTP = "http";
    private static final String HTTPS = "https";

    private final Proxy httpProxy;
    private final Proxy httpsProxy;
    private final Proxy allProxy;
    private final List<NoProxyRule> noProxyRules;
    private final ProxySelector fallback;

    private EnvironmentProxySelector(final Map<String, String> environment,
                                     final ProxySelector fallback) {
        this.httpProxy = parseProxy(variable(environment, "http_proxy", "HTTP_PROXY"));
        this.httpsProxy = parseProxy(variable(environment, "https_proxy", "HTTPS_PROXY"));
        this.allProxy = parseProxy(variable(environment, "all_proxy", "ALL_PROXY"));
        this.noProxyRules = parseNoProxy(variable(environment, "no_proxy", "NO_PROXY"));
        this.fallback = fallback;
    }

    public static ProxySelector fromEnvironment(final ProxySelector fallback) {
        return fromEnvironment(System.getenv(), fallback);
    }

    static ProxySelector fromEnvironment(final Map<String, String> environment,
                                         final ProxySelector fallback) {
        return new EnvironmentProxySelector(environment, fallback);
    }

    @Override
    public List<Proxy> select(final URI uri) {
        Objects.requireNonNull(uri, "uri");

        if (uri.getHost() == null || isExcluded(uri)) {
            return List.of(Proxy.NO_PROXY);
        }

        final Proxy configuredProxy = switch (uri.getScheme().toLowerCase(Locale.ROOT)) {
            case HTTP -> httpProxy != null ? httpProxy : allProxy;
            case HTTPS -> httpsProxy != null ? httpsProxy : allProxy;
            default -> allProxy;
        };
        if (configuredProxy != null) {
            return List.of(configuredProxy);
        }
        return fallback == null ? List.of(Proxy.NO_PROXY) : fallback.select(uri);
    }

    @Override
    public void connectFailed(final URI uri, final SocketAddress socketAddress, final IOException exception) {
        if (fallback != null) {
            fallback.connectFailed(uri, socketAddress, exception);
        }
    }

    private boolean isExcluded(final URI uri) {
        final int port = uri.getPort() >= 0 ? uri.getPort() : defaultPort(uri.getScheme());
        return noProxyRules.stream().anyMatch(rule -> rule.matches(uri.getHost(), port));
    }

    private static int defaultPort(final String scheme) {
        if (HTTPS.equalsIgnoreCase(scheme)) {
            return 443;
        }
        return HTTP.equalsIgnoreCase(scheme) ? 80 : -1;
    }

    private static String variable(final Map<String, String> environment,
                                   final String lowerCaseName,
                                   final String upperCaseName) {
        final String lowerCaseValue = environment.get(lowerCaseName);
        if (lowerCaseValue != null && !lowerCaseValue.isBlank()) {
            return lowerCaseValue.trim();
        }
        final String upperCaseValue = environment.get(upperCaseName);
        return upperCaseValue == null || upperCaseValue.isBlank() ? null : upperCaseValue.trim();
    }

    private static Proxy parseProxy(final String value) {
        if (value == null) {
            return null;
        }

        final URI uri;
        try {
            uri = URI.create(value.contains("://") ? value : HTTP + "://" + value);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Invalid proxy environment variable");
        }
        if (uri.getHost() == null || uri.getUserInfo() != null) {
            throw new IllegalArgumentException("Proxy environment variable must contain a host and no credentials");
        }

        final Proxy.Type type = switch (uri.getScheme().toLowerCase(Locale.ROOT)) {
            case HTTP, HTTPS -> Proxy.Type.HTTP;
            case "socks", "socks5" -> Proxy.Type.SOCKS;
            default -> throw new IllegalArgumentException("Unsupported proxy scheme: " + uri.getScheme());
        };
        final int defaultProxyPort = type == Proxy.Type.SOCKS ? 1080 : 80;
        final int port = uri.getPort() >= 0 ? uri.getPort() : defaultProxyPort;
        return new Proxy(type, InetSocketAddress.createUnresolved(uri.getHost(), port));
    }

    private static List<NoProxyRule> parseNoProxy(final String value) {
        if (value == null) {
            return List.of();
        }

        final List<NoProxyRule> rules = new ArrayList<>();
        for (String entry : value.split(",")) {
            final String token = entry.trim();
            if (!token.isEmpty()) {
                rules.add(NoProxyRule.parse(token));
            }
        }
        return List.copyOf(rules);
    }

    private record NoProxyRule(String host, int port, boolean matchesEverything) {

        private static NoProxyRule parse(final String value) {
            if ("*".equals(value)) {
                return new NoProxyRule("", -1, true);
            }

            String host = value;
            int port = -1;
            if (value.startsWith("[")) {
                final int closingBracket = value.indexOf(']');
                if (closingBracket < 0) {
                    throw new IllegalArgumentException("Invalid IPv6 entry in no_proxy");
                }
                host = value.substring(1, closingBracket);
                if (closingBracket + 1 < value.length()) {
                    if (value.charAt(closingBracket + 1) != ':') {
                        throw new IllegalArgumentException("Invalid IPv6 entry in no_proxy");
                    }
                    port = parsePort(value.substring(closingBracket + 2));
                }
            } else {
                final int firstColon = value.indexOf(':');
                final int lastColon = value.lastIndexOf(':');
                if (firstColon > 0 && firstColon == lastColon) {
                    host = value.substring(0, firstColon);
                    port = parsePort(value.substring(firstColon + 1));
                }
            }

            host = normalizeHost(host);
            if (host.isEmpty()) {
                throw new IllegalArgumentException("Invalid empty host in no_proxy");
            }
            return new NoProxyRule(host, port, false);
        }

        private boolean matches(final String candidateHost, final int candidatePort) {
            if (matchesEverything) {
                return true;
            }
            if (port >= 0 && port != candidatePort) {
                return false;
            }
            final String normalizedCandidate = normalizeHost(candidateHost);
            return normalizedCandidate.equals(host) || normalizedCandidate.endsWith("." + host);
        }

        private static int parsePort(final String value) {
            try {
                final int parsed = Integer.parseInt(value);
                if (parsed < 1 || parsed > 65535) {
                    throw new IllegalArgumentException("Port in no_proxy is out of range");
                }
                return parsed;
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException("Invalid port in no_proxy", exception);
            }
        }

        private static String normalizeHost(final String value) {
            String normalized = value.toLowerCase(Locale.ROOT);
            if (normalized.startsWith("*.")) {
                normalized = normalized.substring(2);
            } else if (normalized.startsWith(".")) {
                normalized = normalized.substring(1);
            }
            while (normalized.endsWith(".")) {
                normalized = normalized.substring(0, normalized.length() - 1);
            }
            return normalized;
        }
    }
}
