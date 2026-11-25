package com.agronomia.backend.ai;

import com.agronomia.backend.images.ApiException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProviderSecurityTest {
    private final ObjectMapper json = new ObjectMapper();
    private static final String SYNTHETIC = "synthetic-test-key";

    @Test
    void rejectsIpRangesAndUnsafeUrlShapes() throws Exception {
        for (String address : new String[]{"0.0.0.0","127.1","10.0.0.1","100.100.100.200","169.254.169.254",
                "168.63.129.16","172.31.10.9","192.168.1.1","192.0.2.1","198.18.0.1","224.0.0.1","::1","fc00::1","fe80::1",
                "::ffff:127.0.0.1","::ffff:169.254.169.254","2002:7f00:1::1","2001:db8::1"}) {
            assertThat(ProviderTarget.publicAddress(InetAddress.getByName(address))).as(address).isFalse();
        }
        assertThat(ProviderTarget.publicAddress(InetAddress.getByName("8.8.8.8"))).isTrue();
        var strict = new ProviderTarget(false);
        var local = new ProviderTarget(true);
        for (String url : new String[]{"http://8.8.8.8/v1","https://127.0.0.1/v1","https://user@8.8.8.8/v1",
                "https://8.8.8.8/v1?token=x","https://8.8.8.8/v1#x","https://8.8.8.8/v1/%2e%2e"}) {
            assertThatThrownBy(() -> strict.validate(url)).as(url).isInstanceOf(ApiException.class);
        }
        assertThatThrownBy(() -> local.validate("http://169.254.169.254/v1")).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> local.validate("http://[fd00:ec2::254]/v1")).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> local.validate("https://168.63.129.16/v1")).isInstanceOf(ApiException.class);
        assertThat(local.validate("http://127.0.0.1:9090/v1").host()).isEqualTo("127.0.0.1");
    }

    @Test
    void credentialsAreBoundToOwnerAndProfileAndCanRotate() {
        byte[] first = new byte[32], second = new byte[32];
        second[0] = 1;
        var original = new ProviderKeys(json, b64(first), "v1", "");
        var a = original.encrypt(7, "a", SYNTHETIC);
        var b = original.encrypt(7, "a", SYNTHETIC);
        assertThat(a.nonce()).isNotEqualTo(b.nonce());
        assertThat(a.suffix()).isEqualTo("-key");
        var rotated = new ProviderKeys(json, b64(second), "v2", "{\"v1\":\"" + b64(first) + "\"}");
        assertThat(rotated.decrypt(7,"a",a)).isEqualTo(SYNTHETIC);
        assertThat(rotated.encrypt(7,"a",SYNTHETIC).kid()).isEqualTo("v2");
        assertThatThrownBy(() -> rotated.decrypt(8,"a",a)).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> rotated.decrypt(7,"b",a)).isInstanceOf(ApiException.class);
    }

    @Test
    void customTransportKeepsCredentialsOnPinnedHostAndBoundsResponses() throws Exception {
        var secret = new ProviderKeys(json, b64(new byte[32]), "v1", "");
        var transport = new ProviderTransport(json, new ProviderTarget(true), secret);
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        var redirected = new AtomicInteger();
        server.createContext("/v1/redirect", exchange -> {
            exchange.getResponseHeaders().add("Location", "/target");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });
        server.createContext("/target", exchange -> {
            redirected.incrementAndGet();
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
        });
        server.createContext("/v1/models", exchange -> {
            var status = ("Bearer " + SYNTHETIC).equals(exchange.getRequestHeaders().getFirst("Authorization")) ? 200 : 401;
            byte[] body = "{}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, body.length);
            try (var output = exchange.getResponseBody()) { output.write(body); }
        });
        server.createContext("/v1/large", exchange -> {
            byte[] body = new byte[300_000];
            exchange.sendResponseHeaders(200, body.length);
            try (var output = exchange.getResponseBody()) { output.write(body); }
        });
        server.start();
        try {
            String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/v1";
            var profile = new ProviderStore.Profile(7,"p","fixture",url,"demo",2048,256,1,false,false,
                    "activo",null,null,null,secret.encrypt(7,"p",SYNTHETIC));
            var deadline = AiClient.Deadline.after(Duration.ofSeconds(20));
            assertThat(transport.call(profile,"/models",null,deadline).body().isObject()).isTrue();
            assertThat(transport.call(profile,"/redirect",null,deadline).status()).isEqualTo(302);
            assertThat(redirected).hasValue(0);
            assertThat(transport.call(profile,"/large",null,deadline).body().isMissingNode()).isTrue();
        } finally {
            transport.close();
            server.stop(0);
        }
    }

    private static String b64(byte[] value) { return Base64.getEncoder().encodeToString(value); }
}
