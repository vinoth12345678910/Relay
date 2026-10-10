// File: relay-server/src/main/java/com/relay/server/security/SsrfGuard.java
package com.relay.server.security;

import com.relay.server.common.ValidationException;
import com.relay.server.config.RelayProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;

/**
 * MVP SSRF protection for endpoint URLs: http/https only, and the host must not resolve
 * to a loopback, private, link-local, or otherwise internal address.
 *
 * <p>{@link #validate} is the registration-time check. DNS can change afterwards (rebinding),
 * so the delivery worker must call {@link #resolveSafe} on every attempt and connect to the
 * returned address rather than resolving the hostname a second time.
 *
 * <p>{@code relay.ssrf.allow-private-networks} (off by default) relaxes only the private ranges, for
 * setups where receivers are other containers. Loopback, link-local (cloud metadata), unspecified
 * and multicast addresses are blocked either way.
 */
@Slf4j
@Component
public class SsrfGuard {

    private static final String FIELD = "url";

    private final boolean allowPrivateNetworks;

    /** Strict guard: no private networks. */
    public SsrfGuard() {
        this.allowPrivateNetworks = false;
    }

    @Autowired
    public SsrfGuard(RelayProperties properties) {
        this.allowPrivateNetworks = properties.getSsrf().isAllowPrivateNetworks();
        if (allowPrivateNetworks) {
            log.warn("relay.ssrf.allow-private-networks is ON: endpoints may target private networks. Do not use this in production.");
        }
    }

    /** Throws {@link ValidationException} (field {@code url}) if the URL is unsafe. */
    public void validate(String url) {
        resolveSafe(url);
    }

    /**
     * Validates the URL and returns a vetted address for it. Every resolved address must be
     * public; connect to the returned one (keeping the original Host header / SNI name) so
     * a later DNS change cannot redirect the request.
     */
    public InetAddress resolveSafe(String url) {
        return resolveSafe(parse(url));
    }

    /** Same checks for an already-parsed URI. */
    public void validate(URI uri) {
        resolveSafe(uri);
    }

    /** Same as {@link #resolveSafe(String)} for an already-parsed URI. */
    public InetAddress resolveSafe(URI uri) {
        String scheme = uri.getScheme();
        if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
            throw new ValidationException(FIELD, "url must use http or https");
        }
        if (uri.getHost() == null || uri.getUserInfo() != null) {
            throw new ValidationException(FIELD, "url must have a host and no credentials");
        }
        InetAddress[] addresses = resolve(uri.getHost());
        for (InetAddress address : addresses) {
            if (isInternal(address)) {
                throw new ValidationException(FIELD, "url must not target a private or loopback address");
            }
        }
        return addresses[0];
    }

    private URI parse(String url) {
        try {
            return new URI(url.trim());
        } catch (URISyntaxException | NullPointerException e) {
            throw new ValidationException(FIELD, "url is not a valid URL");
        }
    }

    private InetAddress[] resolve(String host) {
        try {
            return InetAddress.getAllByName(host);
        } catch (UnknownHostException e) {
            throw new ValidationException(FIELD, "url host could not be resolved");
        }
    }

    private boolean isInternal(InetAddress a) {
        if (a.isAnyLocalAddress() || a.isLoopbackAddress() || a.isLinkLocalAddress() || a.isMulticastAddress()) {
            return true;   // never allowed, flag or not (link-local includes 169.254.169.254)
        }
        return !allowPrivateNetworks && isPrivateRange(a);
    }

    private boolean isPrivateRange(InetAddress a) {
        return a.isSiteLocalAddress() || isCarrierGradeNat(a) || isUniqueLocalIpv6(a);
    }

    /** 100.64.0.0/10 */
    private boolean isCarrierGradeNat(InetAddress a) {
        byte[] b = a.getAddress();
        return b.length == 4 && (b[0] & 0xFF) == 100 && (b[1] & 0xC0) == 64;
    }

    /** fc00::/7 */
    private boolean isUniqueLocalIpv6(InetAddress a) {
        byte[] b = a.getAddress();
        return b.length == 16 && (b[0] & 0xFE) == 0xFC;
    }
}
