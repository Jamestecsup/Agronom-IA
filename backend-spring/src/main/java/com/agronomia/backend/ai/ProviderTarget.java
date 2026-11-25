package com.agronomia.backend.ai;

import com.agronomia.backend.images.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Locale;

@Component
final class ProviderTarget {
    private final boolean localDevelopment;

    ProviderTarget(@Value("${AI_ALLOW_PRIVATE_HOSTS:false}") boolean localDevelopment) {
        this.localDevelopment = localDevelopment;
    }

    Target validate(String base) {
        try {
            URI uri = URI.create(base);
            String host = uri.getHost();
            String path = uri.getRawPath();
            if (host == null || uri.getRawUserInfo() != null || uri.getRawQuery() != null || uri.getRawFragment() != null
                    || uri.getPort() == 0 || uri.getPort() > 65535
                    || !path.matches("(?:/[a-zA-Z0-9_-]+)*/?")) throw invalid();
            host = host.replace("[", "").replace("]", "").toLowerCase(Locale.ROOT);
            if (host.contains("%") || host.endsWith(".") || host.endsWith(".local") || host.endsWith(".internal")) throw invalid();
            boolean http = "http".equalsIgnoreCase(uri.getScheme());
            if (!"https".equalsIgnoreCase(uri.getScheme()) && !(http && localDevelopment)) throw invalid();
            InetAddress[] resolved = InetAddress.getAllByName(host);
            if (resolved.length == 0 || Arrays.stream(resolved).anyMatch(ip ->
                    !publicAddress(ip) && !(localDevelopment && localAddress(ip)))) throw invalid();
            if (http && Arrays.stream(resolved).anyMatch(ip -> !localAddress(ip))) throw invalid();
            return new Target(base.replaceFirst("/+$", ""), host, resolved);
        } catch (IllegalArgumentException | UnknownHostException e) {
            throw invalid();
        }
    }

    static boolean publicAddress(InetAddress address) {
        byte[] ip = address.getAddress();
        if (ip.length == 4) {
            int a = ip[0] & 255, b = ip[1] & 255, c = ip[2] & 255;
            return a > 0 && a < 224 && a != 10 && a != 127
                    && !(a == 100 && b >= 64 && b <= 127) && !(a == 169 && b == 254)
                    && !(a == 172 && b >= 16 && b <= 31)
                    && !(a == 192 && (b == 168 || (b == 0 && (c == 0 || c == 2)) || (b == 88 && c == 99)))
                    && !(a == 198 && (b == 18 || b == 19 || (b == 51 && c == 100)))
                    && !(a == 203 && b == 0 && c == 113);
        }
        int a = ip[0] & 255, b = ip[1] & 255, c = ip[2] & 255, d = ip[3] & 255;
        return (a & 0xe0) == 0x20 && !(a == 0x20 && b == 2)
                && !(a == 0x20 && b == 1 && (c < 2 || (c == 0x0d && d == 0xb8)))
                && !(a == 0x3f && b == 0xff);
    }

    private static boolean localAddress(InetAddress address) {
        return address.isLoopbackAddress() || address.isSiteLocalAddress()
                || (address.getAddress().length == 16 && (address.getAddress()[0] & 0xfe) == 0xfc);
    }

    private static ApiException invalid() {
        return new ApiException(HttpStatus.BAD_REQUEST, "La URL o la dirección del proveedor no están permitidas.");
    }

    record Target(String base, String host, InetAddress[] addresses) {}
}
