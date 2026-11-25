package com.agronomia.backend.ai;

import com.agronomia.backend.images.ApiException;
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

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
