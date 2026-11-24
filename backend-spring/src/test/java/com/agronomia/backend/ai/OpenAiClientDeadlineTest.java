package com.agronomia.backend.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OpenAiClientDeadlineTest {
    @Test
    void deadlineAlsoCoversBodyAfterHeadersHaveArrived() throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        var headersSent = new CountDownLatch(1);
        var canFinish = new CountDownLatch(1);
        server.createContext("/chat/completions", exchange -> {
            exchange.getRequestBody().readAllBytes();
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, 10);
            headersSent.countDown();
            try {
                canFinish.await(3, TimeUnit.SECONDS);
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
            } finally {
                exchange.close();
            }
        });
        server.start();
        try {
            var client = new OpenAiClient(new ObjectMapper(), "http://127.0.0.1:" + server.getAddress().getPort(),
                    "fixture", "texto", "", 1, Duration.ofSeconds(1));
            assertThatThrownBy(() -> client.reply("deadline", List.of(), false))
                    .isInstanceOf(AiClient.Failure.class).hasMessageContaining("no respondió a tiempo");
            assertThat(headersSent.getCount()).isZero();
        } finally {
            canFinish.countDown();
            server.stop(0);
        }
    }
}
