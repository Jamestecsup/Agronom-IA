package com.agronomia.backend.ai;

import com.agronomia.backend.images.ApiException;
import com.agronomia.backend.images.ApiExceptionHandler;
import com.agronomia.backend.user.UserAccounts;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ProviderStoreTest {
    private EmbeddedDatabase database;
    private ProviderStore store;
    private final ObjectMapper json = new ObjectMapper();

    @BeforeEach
    void createSchema() throws Exception {
        database = new EmbeddedDatabaseBuilder().generateUniqueName(true).setType(EmbeddedDatabaseType.H2).build();
        var db = new JdbcTemplate(database);
        db.execute("SET MODE MySQL");
        db.execute("CREATE TABLE auth_user(id INT PRIMARY KEY)");
        db.update("INSERT INTO auth_user VALUES (1),(2)");
        try (var connection = database.getConnection()) {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/migration/V2__proveedores_ia.sql"));
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/migration/V3__uso_ia_diario.sql"));
        }
        store = new ProviderStore(db, json, new ProviderKeys(json, Base64.getEncoder().encodeToString(new byte[32]), "v1", ""),
                new ProviderTarget(true));
    }

    @AfterEach
    void stop() { if (database != null) database.shutdown(); }

    @Test
    void protectsSettingsOverHttp() throws Exception {
        var profile = store.create(1, json.createObjectNode().put("name","Prueba").put("model","small")
                .put("base_url","http://127.0.0.1:13301/v1").put("api_key","synthetic-credential")
                .put("context_window",1024).put("max_output_tokens",384));
        store.recordProbe(profile,true,false,true,1024,20);
        var accounts = mock(UserAccounts.class);
        when(accounts.requireUser(null)).thenThrow(new ApiException(HttpStatus.UNAUTHORIZED, "Sesión requerida."));
        when(accounts.requireUser("Bearer A")).thenReturn(1L);
        when(accounts.requireUser("Bearer B")).thenReturn(2L);
        var transport = mock(ProviderTransport.class);
        var resolver = new AiProviderResolver(mock(OpenAiClient.class), store, transport, json, 25);
        var http = MockMvcBuilders.standaloneSetup(new AiSettingsController(accounts, store, resolver, json))
                .setControllerAdvice(new ApiExceptionHandler()).build();
        String api = "/agronomia-api/api";
        String target = api + "/ai/providers/" + profile.id();
        String selection = "{\"provider_id\":\"" + profile.id() + "\",\"consent\":true}";

        http.perform(get(api + "/ai/providers")).andExpect(status().isUnauthorized()).andExpect(header().string("Cache-Control","no-store"));
        http.perform(get(api + "/ai/providers").header("Authorization","Bearer B"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.providers").isEmpty()).andExpect(header().string("Cache-Control","no-store"));
        http.perform(patch(target).header("Authorization","Bearer B").contentType("application/json").content("{}"))
                .andExpect(status().isNotFound());
        http.perform(post(target + "/test").header("Authorization","Bearer B")).andExpect(status().isNotFound());
        http.perform(delete(target).header("Authorization","Bearer B")).andExpect(status().isNotFound());
        http.perform(put(api + "/ai/selection").header("Authorization","Bearer B").contentType("application/json").content(selection))
                .andExpect(status().isNotFound());
        http.perform(put(api + "/ai/selection").header("Authorization","Bearer A").contentType("application/json")
                        .content(selection.replace("true","false"))).andExpect(status().isBadRequest());
        http.perform(put(api + "/preferences").header("Authorization","Bearer A").contentType("application/json")
                        .content("{\"version\":1,\"preferences\":{\"mode\":\"invalid\"}}")).andExpect(status().isBadRequest());
        http.perform(put(api + "/ai/selection").header("Authorization","Bearer A").contentType("application/json").content(selection))
                .andExpect(status().isNoContent()).andExpect(header().string("Cache-Control","no-store"));
        http.perform(get(api + "/ai/capabilities").header("Authorization","Bearer A")).andExpect(status().isOk())
                .andExpect(jsonPath("$.features.chat.available").value(true))
                .andExpect(jsonPath("$.features.card.available").value(false))
                .andExpect(jsonPath("$.features.chat_photo.available").value(false));
        http.perform(get(api + "/ai/capabilities").header("Authorization","Bearer B"))
                .andExpect(jsonPath("$.features.chat_photo.available").value(true));
        verifyNoInteractions(transport);
    }

    @Test
    void checksVisionByColorAndEnforcesCustomClientLimits() {
        var profile = store.create(1, json.createObjectNode().put("name","Prueba").put("model","small")
                .put("base_url","http://127.0.0.1:13301/v1").put("api_key","synthetic-credential"));
        var transport = mock(ProviderTransport.class);
        var resolver = new AiProviderResolver(mock(OpenAiClient.class), store, transport, json, 25);
        var color = new java.util.concurrent.atomic.AtomicReference<>("OK");
        when(transport.call(any(),eq("/models"),isNull(),any())).thenReturn(new ProviderTransport.Response(404,json.missingNode()));
        when(transport.call(any(),eq("/chat/completions"),any(),any())).thenAnswer(call -> {
            var request = call.<com.fasterxml.jackson.databind.JsonNode>getArgument(2);
            var content = request.path("messages").path(0).path("content");
            String reply = content.isArray() ? color.get() : request.has("response_format") ? "{\"ok\":true}" : "OK";
            var result = json.createObjectNode();
            result.putArray("choices").addObject().putObject("message").put("content",reply);
            return new ProviderTransport.Response(200,result);
        });
        assertThat(resolver.probe(1,profile.id()).vision()).isFalse();
        store.select(1,profile.id(),true);
        var client = resolver.forUser(1);
        var prompt = java.util.List.of(AiClient.message("user","Hola"));
        assertThatThrownBy(() -> client.reply("chat",prompt,true)).isInstanceOfSatisfying(AiClient.Failure.class,
                error -> assertThat(error.visionUnsupported()).isTrue());
        assertThatThrownBy(() -> client.reply("chat",java.util.List.of(AiClient.message("user","a".repeat(1_000_000))),false))
                .isInstanceOfSatisfying(ApiException.class,error -> assertThat(error.status()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY));
        for (int i = 0; i < 60; i++) assertThat(client.reply("chat",prompt,false)).isEqualTo("OK");
        assertThatThrownBy(() -> client.reply("chat",prompt,false)).isInstanceOfSatisfying(ApiException.class,
                error -> assertThat(error.status()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS));
        color.set("Rojo.");
        assertThat(resolver.probe(1,profile.id()).vision()).isTrue();
        store.select(1,null,false);
        assertThatThrownBy(() -> client.reply("chat",prompt,false)).isInstanceOfSatisfying(ApiException.class,
                error -> assertThat(error.status()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void enforcesOwnershipConsentQuotaAndPreferenceWhitelist() {
        var input = json.createObjectNode().put("name", "Local").put("base_url","http://127.0.0.1:13301/v1")
                .put("model","test").put("api_key","synthetic-credential");
        var profile = store.create(1,input);
        assertThat(profile.view(json).toString()).doesNotContain("synthetic-credential").contains("tial");
        assertThat(store.list(2)).isEmpty();
        assertThatThrownBy(() -> store.require(2,profile.id())).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> store.select(2,profile.id(),true)).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> store.select(1,profile.id(),true)).isInstanceOf(ApiException.class);
        store.recordProbe(profile,true,false,true,4096,100);
        assertThatThrownBy(() -> store.select(1,profile.id(),false)).isInstanceOf(ApiException.class);
        store.select(1,profile.id(),true);
        assertThat(store.active(1).id()).isEqualTo(profile.id());
        assertThat(store.active(2)).isNull();
        assertThatThrownBy(() -> store.update(1,profile.id(),
                json.createObjectNode().put("base_url","http://127.0.0.1:13302/v1"))).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> store.savePreferences(1,json.createObjectNode().put("css","url(...)")))
                .isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> store.savePreferences(1,json.createObjectNode().put("mode","unsafe")))
                .isInstanceOf(ApiException.class);
        store.savePreferences(1,json.createObjectNode().put("mode","system"));
        store.useSystem(1,1);
        assertThatThrownBy(() -> store.useSystem(1,1)).isInstanceOf(ApiException.class);
        store.useSystem(2,1);
        store.delete(1,profile.id());
        assertThat(store.active(1)).isNull();
    }
}
