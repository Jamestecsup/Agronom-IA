package com.agronomia.backend.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.hc.client5.http.DnsResolver;
import org.apache.hc.client5.http.classic.methods.HttpUriRequestBase;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.util.Timeout;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Component
final class ProviderTransport {
    private static final int RESPONSE_LIMIT = 256 * 1024;
    private final ObjectMapper json;
    private final ProviderTarget targets;
    private final ProviderKeys keys;
    private final ScheduledThreadPoolExecutor timer = new ScheduledThreadPoolExecutor(1, task -> {
        Thread thread = new Thread(task, "proveedor-ia-plazos");
        thread.setDaemon(true);
        return thread;
    });

    ProviderTransport(ObjectMapper json, ProviderTarget targets, ProviderKeys keys) {
        this.json = json;
        this.targets = targets;
        this.keys = keys;
        timer.setRemoveOnCancelPolicy(true);
    }

    @jakarta.annotation.PreDestroy
    void close() { timer.shutdownNow(); }

    Response call(ProviderStore.Profile profile, String path, JsonNode body, AiClient.Deadline deadline) {
        long started = System.nanoTime();
        int status = 0;
        try {
            deadline.requireTime();
            var target = targets.validate(profile.base());
            deadline.requireTime();
            var dns = new DnsResolver() {
                @Override public InetAddress[] resolve(String host) throws UnknownHostException {
                    if (!target.host().equalsIgnoreCase(host)) throw new UnknownHostException();
                    return target.addresses().clone();
                }

                @Override public String resolveCanonicalHostname(String host) throws UnknownHostException {
                    if (!target.host().equalsIgnoreCase(host)) throw new UnknownHostException();
                    return target.host();
                }
            };
            var manager = PoolingHttpClientConnectionManagerBuilder.create().setDnsResolver(dns)
                    .setDefaultConnectionConfig(ConnectionConfig.custom().setConnectTimeout(Timeout.ofSeconds(8))
                            .setSocketTimeout(Timeout.ofSeconds(30)).build()).build();
            var request = new HttpUriRequestBase(body == null ? "GET" : "POST", URI.create(target.base() + path));
            request.setConfig(RequestConfig.custom().setAuthenticationEnabled(false)
                    .setResponseTimeout(Timeout.ofMilliseconds(Math.max(1, deadline.remainingNanos() / 1_000_000)))
                    .build());
            request.setHeader("Authorization", "Bearer " + keys.decrypt(profile.owner(), profile.id(), profile.secret()));
            request.setHeader("Accept", "application/json");
            if (body != null) request.setEntity(new StringEntity(body.toString(), ContentType.APPLICATION_JSON));
            var cancel = timer.schedule(request::cancel, deadline.remainingNanos(), TimeUnit.NANOSECONDS);
            try (var client = HttpClients.custom().setConnectionManager(manager).disableRedirectHandling()
                    .disableAutomaticRetries().disableCookieManagement().disableContentCompression().build()) {
                var result = client.execute(request, response -> {
                    int code = response.getCode();
                    if (code < 200 || code >= 300 || response.getEntity() == null) {
                        request.cancel();
                        return new Response(code, json.missingNode());
                    }
                    byte[] bytes = response.getEntity().getContent().readNBytes(RESPONSE_LIMIT + 1);
                    if (bytes.length > RESPONSE_LIMIT) {
                        request.cancel();
                        return new Response(code, json.missingNode());
                    }
                    try {
                        JsonNode parsed = json.readTree(bytes);
                        return new Response(code, parsed == null ? json.missingNode() : parsed);
                    } catch (IOException e) {
                        return new Response(code, json.missingNode());
                    }
                });
                status = result.status();
                return result;
            } finally {
                cancel.cancel(false);
                request.removeHeaders("Authorization");
            }
        } catch (IOException ignored) {
            return new Response(status, json.missingNode());
        } finally {
            LoggerFactory.getLogger(ProviderTransport.class).info("IA proveedor={} HTTP={} tiempo_ms={}",
                    profile.id(), status, TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started));
        }
    }

    record Response(int status, JsonNode body) {}
}
