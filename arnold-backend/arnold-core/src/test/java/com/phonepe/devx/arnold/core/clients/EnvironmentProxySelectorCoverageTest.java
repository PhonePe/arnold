package com.phonepe.devx.arnold.core.clients;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class EnvironmentProxySelectorCoverageTest {

    private static ProxySelector of(final Map<String, String> env) {
        return EnvironmentProxySelector.fromEnvironment(env, null);
    }

    private static Proxy first(final ProxySelector s, final String uri) {
        return s.select(URI.create(uri)).get(0);
    }

    @Test
    void fromEnvironmentUsingSystemEnvIsCreated() {
        assertNotNull(EnvironmentProxySelector.fromEnvironment(null));
    }

    @Test
    void nullUriIsRejectedAndHostlessUriIsDirect() {
        final ProxySelector s = of(Map.of("http_proxy", "proxy:1"));
        assertThrows(NullPointerException.class, () -> s.select(null));
        assertEquals(List.of(Proxy.NO_PROXY), s.select(URI.create("file:///tmp/x")));
    }

    @Test
    void httpFallsBackToAllProxyAndUnknownSchemeUsesAllProxy() {
        final ProxySelector s = of(Map.of("all_proxy", "socks://sox"));
        final Proxy http = first(s, "http://a.example");
        assertEquals(Proxy.Type.SOCKS, http.type());
        assertEquals(1080, ((InetSocketAddress) http.address()).getPort());
        assertEquals(Proxy.Type.SOCKS, first(s, "ftp://a.example").type());
    }

    @Test
    void directWhenNothingConfiguredAndNoFallback() {
        assertEquals(List.of(Proxy.NO_PROXY), of(Map.of("http_proxy", " ", "HTTP_PROXY", " "))
                .select(URI.create("https://a.example")));
    }

    @Test
    void parsesNoProxyVariants() {
        final ProxySelector s = of(Map.of(
                "http_proxy", "https://p:3128",
                "NO_PROXY", " ,*.wild.com, [::1]:8080,[fe80::1], dotted.com., ip6:thing::x "));
        assertEquals(Proxy.Type.HTTP, first(s, "http://other.example").type());
        assertEquals(Proxy.NO_PROXY, first(s, "http://a.wild.com"));
        assertEquals(Proxy.NO_PROXY, first(s, "http://dotted.com/"));
        assertEquals(Proxy.NO_PROXY, first(s, "ftp://wild.com/"));
    }

    @Test
    void rejectsInvalidConfiguration() {
        assertThrows(IllegalArgumentException.class, () -> of(Map.of("http_proxy", "ht tp://bad host")));
        assertThrows(IllegalArgumentException.class, () -> of(Map.of("http_proxy", "http://:80")));
        assertThrows(IllegalArgumentException.class, () -> of(Map.of("http_proxy", "ftp://host")));
        assertThrows(IllegalArgumentException.class, () -> of(Map.of("no_proxy", "[::1")));
        assertThrows(IllegalArgumentException.class, () -> of(Map.of("no_proxy", "[::1]x")));
        assertThrows(IllegalArgumentException.class, () -> of(Map.of("no_proxy", "host:abc")));
        assertThrows(IllegalArgumentException.class, () -> of(Map.of("no_proxy", "host:70000")));
        assertThrows(IllegalArgumentException.class, () -> of(Map.of("no_proxy", ".")));
    }

    @Test
    void connectFailedDelegatesToFallbackWhenPresent() {
        final AtomicInteger calls = new AtomicInteger();
        final ProxySelector fallback = new ProxySelector() {
            @Override
            public List<Proxy> select(final URI uri) {
                return List.of(Proxy.NO_PROXY);
            }

            @Override
            public void connectFailed(final URI uri, final SocketAddress sa, final IOException ioe) {
                calls.incrementAndGet();
            }
        };
        final URI uri = URI.create("http://a.example");
        EnvironmentProxySelector.fromEnvironment(Map.of(), fallback)
                .connectFailed(uri, null, new IOException("x"));
        of(Map.of()).connectFailed(uri, null, new IOException("x"));
        assertEquals(1, calls.get());
    }
}
