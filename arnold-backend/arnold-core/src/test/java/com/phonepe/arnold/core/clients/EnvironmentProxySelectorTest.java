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

package com.phonepe.arnold.core.clients;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.URI;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EnvironmentProxySelectorTest {

    @Test
    void selectsProxyByRequestSchemeAndPrefersLowerCaseVariables() {
        final ProxySelector selector = EnvironmentProxySelector.fromEnvironment(Map.of(
                "http_proxy", "http://lower-http-proxy:8080",
                "HTTP_PROXY", "http://upper-http-proxy:8080",
                "https_proxy", "http://https-proxy:8443"
        ), null);

        assertProxy(selector, "http://external.example/path", "lower-http-proxy", 8080);
        assertProxy(selector, "https://external.example/path", "https-proxy", 8443);
    }

    @Test
    void bypassesProxyForExactHostsDomainSuffixesAndMatchingPorts() {
        final ProxySelector selector = EnvironmentProxySelector.fromEnvironment(Map.of(
                "https_proxy", "http://proxy:8888",
                "no_proxy", "localhost,.internal.example,api.example.com:8443"
        ), null);

        assertDirect(selector, "https://localhost/status");
        assertDirect(selector, "https://internal.example/status");
        assertDirect(selector, "https://service.internal.example/status");
        assertProxy(selector, "https://notinternal.example/status", "proxy", 8888);
        assertProxy(selector, "https://internal.example.external.example/status", "proxy", 8888);
        assertDirect(selector, "https://api.example.com:8443/status");
        assertProxy(selector, "https://api.example.com/status", "proxy", 8888);
    }

    @Test
    void supportsAllProxyAndWildcardNoProxy() {
        final ProxySelector allProxySelector = EnvironmentProxySelector.fromEnvironment(
                Map.of("ALL_PROXY", "socks5://socks-proxy:1081"), null);
        assertProxy(allProxySelector, "https://external.example", "socks-proxy", 1081);

        final ProxySelector directSelector = EnvironmentProxySelector.fromEnvironment(Map.of(
                "http_proxy", "proxy:8888",
                "NO_PROXY", "*"
        ), null);
        assertDirect(directSelector, "http://external.example");
    }

    @Test
    void delegatesWhenNoEnvironmentProxyApplies() {
        final Proxy fallbackProxy = new Proxy(Proxy.Type.HTTP,
                InetSocketAddress.createUnresolved("fallback-proxy", 3128));
        final ProxySelector fallback = new FixedProxySelector(fallbackProxy);
        final ProxySelector selector = EnvironmentProxySelector.fromEnvironment(Map.of(), fallback);

        assertEquals(List.of(fallbackProxy), selector.select(URI.create("https://external.example")));
    }

    @Test
    void rejectsCredentialsInProxyUrl() {
        assertThrows(IllegalArgumentException.class, () -> EnvironmentProxySelector.fromEnvironment(
                Map.of("https_proxy", "http://user:password@proxy:8888"), null));
    }

    @Test
    void usesUpperCaseHttpsProxyAndDefaultPort() {
        final ProxySelector selector = EnvironmentProxySelector.fromEnvironment(
                Map.of("HTTPS_PROXY", "http://proxy"), null);
        assertProxy(selector, "https://github.com/login/device/code", "proxy", 80);
        assertProxy(selector, "https://api.github.com/user", "proxy", 80);
        assertProxy(selector, "https://api.business.githubcopilot.com/models", "proxy", 80);
    }

    private static void assertDirect(final ProxySelector selector, final String uri) {
        assertEquals(List.of(Proxy.NO_PROXY), selector.select(URI.create(uri)));
    }

    private static void assertProxy(final ProxySelector selector,
                                    final String uri,
                                    final String expectedHost,
                                    final int expectedPort) {
        final Proxy proxy = selector.select(URI.create(uri)).get(0);
        final InetSocketAddress address = (InetSocketAddress) proxy.address();
        assertEquals(expectedHost, address.getHostString());
        assertEquals(expectedPort, address.getPort());
    }

    private static final class FixedProxySelector extends ProxySelector {
        private final Proxy proxy;

        private FixedProxySelector(final Proxy proxy) {
            this.proxy = proxy;
        }

        @Override
        public List<Proxy> select(final URI uri) {
            return List.of(proxy);
        }

        @Override
        public void connectFailed(final URI uri,
                                  final SocketAddress socketAddress,
                                  final IOException exception) {
            // Nothing to do in this test fallback.
        }
    }
}
