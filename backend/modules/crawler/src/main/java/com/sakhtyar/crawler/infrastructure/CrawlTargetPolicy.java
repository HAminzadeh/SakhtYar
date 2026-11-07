package com.sakhtyar.crawler.infrastructure;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Locale;

/**
 * Fail-closed policy for the REAL crawler egress adapter. This is not a firewall:
 * enforce destination CIDR restrictions at the network boundary as well to
 * protect against DNS rebinding between validation and HttpClient connection.
 */
public final class CrawlTargetPolicy {
    private CrawlTargetPolicy() {}

    public static void requireSyntax(URI uri) {
        if (uri == null || uri.isOpaque() || uri.getHost() == null || uri.getHost().isBlank()) {
            throw new SecurityException("Crawler target must have a host");
        }
        String scheme = uri.getScheme();
        if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
            throw new SecurityException("Crawler allows only HTTP(S)");
        }
        if (uri.getRawUserInfo() != null || uri.getRawFragment() != null ||
                uri.getPort() > 65535 || uri.getPort() == 0 || uri.getPort() < -1) {
            throw new SecurityException("Crawler target contains forbidden URL components");
        }
        String host = uri.getHost().toLowerCase(Locale.ROOT);
        if (host.equals("localhost") || host.endsWith(".localhost") ||
                host.endsWith(".local") || host.endsWith(".internal") ||
                host.endsWith(".test") || host.endsWith(".invalid")) {
            throw new SecurityException("Crawler internal/test hostname denied");
        }
    }

    public static void requireAllowedHost(URI uri, String allowedHost) {
        requireSyntax(uri);
        if (allowedHost == null || allowedHost.isBlank() ||
                !uri.getHost().equalsIgnoreCase(allowedHost.trim())) {
            throw new SecurityException("Crawler URL is outside its explicitly allowed host");
        }
    }

    public static void requireSameHost(URI origin, URI candidate) {
        requireSyntax(origin);
        requireSyntax(candidate);
        if (!origin.getHost().equalsIgnoreCase(candidate.getHost())) {
            throw new SecurityException("Cross-host crawler redirect denied");
        }
        if ("https".equalsIgnoreCase(origin.getScheme()) &&
                !"https".equalsIgnoreCase(candidate.getScheme())) {
            throw new SecurityException("HTTPS downgrade denied");
        }
    }

    public static void requirePublicDestination(URI uri) {
        requireSyntax(uri);
        final InetAddress[] resolved;
        try {
            resolved = InetAddress.getAllByName(uri.getHost());
        } catch (UnknownHostException e) {
            throw new SecurityException("Crawler DNS resolution failed (fail closed)", e);
        }
        if (resolved.length == 0) throw new SecurityException("Crawler target has no DNS addresses");
        for (InetAddress ip : resolved) {
            if (!isPublicAddress(ip)) {
                throw new SecurityException("Crawler target resolves to a non-public address");
            }
        }
    }

    static boolean isPublicAddress(InetAddress address) {
        if (address.isAnyLocalAddress() || address.isLoopbackAddress() ||
                address.isLinkLocalAddress() || address.isSiteLocalAddress() ||
                address.isMulticastAddress()) return false;
        byte[] b = address.getAddress();
        if (b.length == 4) {
            int a=b[0]&255,c=b[1]&255,d=b[2]&255;
            // Additional special-use IPv4 ranges (RFC 6890 and CGNAT).
            if (a==0 || a>=224 || a==100 && c>=64 && c<=127 ||
                a==192 && c==0 && d==0 || a==192 && c==0 && d==2 ||
                a==198 && (c==18 || c==19) || a==198 && c==51 && d==100 ||
                a==203 && c==0 && d==113) return false;
        } else if (b.length == 16) {
            int a=b[0]&255;
            if ((a&0xfe)==0xfc || (a==0x20 && (b[1]&255)==0x01 && (b[2]&255)==0x0d && (b[3]&255)==0xb8)) return false;
        }
        return true;
    }
}
