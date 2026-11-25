package com.agronomia.backend.ai;

import com.agronomia.backend.images.ApiException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
class ProviderStore {
    static final int DEFAULT_CONTEXT = 8192;
    static final int DEFAULT_OUTPUT = 768;
    static final Map<String, Set<String>> PREFS = Map.of(
            "mode", Set.of("light","dark","system"), "accent", Set.of("green","blue","plum"),
            "font", Set.of("sans","serif","accessible"), "scale", Set.of("normal","large","extra"),
            "density", Set.of("comfortable","compact"), "garden", Set.of("cards","list"),
            "motion", Set.of("system","off"));
    private final JdbcTemplate db;
    private final ObjectMapper json;
    private final ProviderKeys keys;
    private final ProviderTarget targets;

    ProviderStore(JdbcTemplate db, ObjectMapper json, ProviderKeys keys, ProviderTarget targets) {
        this.db = db;
        this.json = json;
        this.keys = keys;
        this.targets = targets;
    }

    List<Profile> list(long owner) {
        return db.query("SELECT * FROM proveedores_ia WHERE usuario_id=? ORDER BY creado_en,id", this::read, owner);
    }

    Profile require(long owner, String id) {
        return db.query("SELECT * FROM proveedores_ia WHERE id=? AND usuario_id=?", this::read, id, owner)
                .stream().findFirst().orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Proveedor no encontrado."));
    }

    Profile active(long owner) {
        return db.query("""
                SELECT p.* FROM proveedores_ia p JOIN preferencias_usuario u
                    ON u.proveedor_id=p.id AND u.usuario_id=p.usuario_id
                WHERE p.usuario_id=? AND p.estado='activo' AND p.consentimiento IS NOT NULL
                """, this::read, owner).stream().findFirst().orElse(null);
    }

    @Transactional
    public Profile create(long owner, JsonNode input) {
        lock(owner);
        if (db.queryForObject("SELECT COUNT(*) FROM proveedores_ia WHERE usuario_id=?", Integer.class, owner) >= 5) {
            throw invalid("Puedes guardar hasta cinco proveedores.");
        }
        String id = UUID.randomUUID().toString();
        var profile = validate(owner, id, input, null);
        db.update("""
                INSERT INTO proveedores_ia(id,usuario_id,nombre,base_url,clave_cifrada,nonce,kid,sufijo,
                    modelo,context_window,max_output_tokens,max_concurrency,creado_en)
                VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)
                """, id, owner, profile.name(), profile.base(), profile.secret().value(), profile.secret().nonce(),
                profile.secret().kid(), profile.secret().suffix(), profile.model(), profile.context(), profile.output(),
                profile.concurrency(), LocalDateTime.now(ZoneOffset.UTC));
        return require(owner, id);
    }

    @Transactional
    public Profile update(long owner, String id, JsonNode input) {
        lock(owner);
        var before = require(owner, id);
        var p = validate(owner, id, input, before);
        if (!before.base().equals(p.base()) && !input.has("api_key")) {
            throw invalid("Al cambiar la URL vuelve a introducir la clave para ese destino.");
        }
        db.update("""
                UPDATE proveedores_ia SET nombre=?,base_url=?,clave_cifrada=?,nonce=?,kid=?,sufijo=?,
                    modelo=?,context_window=?,max_output_tokens=?,max_concurrency=?,estado='pendiente',
                    supports_vision=FALSE,supports_json_mode=FALSE,ultima_prueba=NULL,latencia_ms=NULL,
                    consentimiento=NULL WHERE id=? AND usuario_id=?
                """, p.name(), p.base(), p.secret().value(), p.secret().nonce(), p.secret().kid(), p.secret().suffix(),
                p.model(), p.context(), p.output(), p.concurrency(), id, owner);
        db.update("UPDATE preferencias_usuario SET proveedor_id=NULL WHERE usuario_id=? AND proveedor_id=?", owner, id);
        return require(owner, id);
    }

    @Transactional
    public void delete(long owner, String id) {
        lock(owner);
        require(owner, id);
        db.update("DELETE FROM proveedores_ia WHERE usuario_id=? AND id=?", owner, id);
    }

    @Transactional
    public Profile recordProbe(Profile earlier, boolean available, boolean vision, boolean jsonMode, int context, long ms) {
        lock(earlier.owner());
        var latest = require(earlier.owner(), earlier.id());
        if (!latest.base().equals(earlier.base()) || !latest.model().equals(earlier.model())
                || !java.util.Arrays.equals(latest.secret().nonce(), earlier.secret().nonce())) {
            throw new ApiException(HttpStatus.CONFLICT, "El proveedor cambió durante la prueba. Inténtalo de nuevo.");
        }
        db.update("""
                UPDATE proveedores_ia SET estado=?,supports_vision=?,supports_json_mode=?,context_window=?,
                    max_output_tokens=?,ultima_prueba=?,latencia_ms=? WHERE usuario_id=? AND id=?
                """, available ? "activo" : "fallido", vision, jsonMode, context, Math.min(latest.output(), context / 2),
                LocalDateTime.now(ZoneOffset.UTC), ms, earlier.owner(), earlier.id());
        return require(earlier.owner(), earlier.id());
    }

    @Transactional
    public void select(long owner, String id, boolean consent) {
        lock(owner);
        ensurePreferences(owner);
        if (id != null) {
            var p = require(owner, id);
            if (!consent || !"activo".equals(p.status())) throw invalid("Prueba el proveedor y acepta el envío de datos antes de activarlo.");
            db.update("UPDATE proveedores_ia SET consentimiento=? WHERE usuario_id=? AND id=?",
                    LocalDateTime.now(ZoneOffset.UTC), owner, id);
        }
        db.update("UPDATE preferencias_usuario SET proveedor_id=? WHERE usuario_id=?", id, owner);
    }

    JsonNode preferences(long owner) {
        return db.query("SELECT preferencias FROM preferencias_usuario WHERE usuario_id=?", (rs, i) -> rs.getString(1), owner)
                .stream().filter(java.util.Objects::nonNull).findFirst().map(value -> {
                    try { return json.readTree(value); }
                    catch (Exception e) { throw new IllegalStateException("Preferencias inválidas."); }
                }).orElseGet(json::createObjectNode);
    }

    @Transactional
    public void savePreferences(long owner, JsonNode input) {
        if (!input.isObject() || input.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 4096) {
            throw invalid("Preferencias inválidas.");
        }
        input.fields().forEachRemaining(entry -> {
            if (!entry.getValue().isTextual() || !PREFS.getOrDefault(entry.getKey(), Set.of()).contains(entry.getValue().asText())) {
                throw invalid("Preferencias fuera de la lista permitida.");
            }
        });
        lock(owner);
        ensurePreferences(owner);
        db.update("UPDATE preferencias_usuario SET preferencias=?,version=1 WHERE usuario_id=?", input.toString(), owner);
    }

    @Transactional
    public void useSystem(long owner, int maximum) {
        lock(owner);
        var today = LocalDate.now(ZoneOffset.UTC);
        var current = db.query("SELECT llamadas FROM uso_ia_diario WHERE usuario_id=? AND fecha=?",
                (rs, i) -> rs.getInt(1), owner, today);
        if (!current.isEmpty() && current.get(0) >= maximum) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "Agotaste la cuota diaria de IA del sistema. Se renueva a medianoche UTC.");
        }
        if (current.isEmpty()) db.update("INSERT INTO uso_ia_diario(usuario_id,fecha,llamadas) VALUES(?,?,1)", owner, today);
        else db.update("UPDATE uso_ia_diario SET llamadas=llamadas+1 WHERE usuario_id=? AND fecha=?", owner, today);
    }

    private void ensurePreferences(long owner) {
        if (db.queryForObject("SELECT COUNT(*) FROM preferencias_usuario WHERE usuario_id=?", Integer.class, owner) == 0) {
            db.update("INSERT INTO preferencias_usuario(usuario_id,version) VALUES(?,1)", owner);
        }
    }

    private void lock(long owner) {
        db.queryForObject("SELECT id FROM auth_user WHERE id=? FOR UPDATE", Long.class, owner);
    }

    private Profile validate(long owner, String id, JsonNode input, Profile existing) {
        if (input == null || !input.isObject()) throw invalid("Configuración inválida.");
        String name = field(input, "name", 80, existing == null ? "" : existing.name());
        String base = field(input, "base_url", 500, existing == null ? "" : existing.base());
        String model = field(input, "model", 160, existing == null ? "" : existing.model());
        targets.validate(base);
        int context = integer(input, "context_window", 1024, 262144, existing == null ? DEFAULT_CONTEXT : existing.context());
        int output = integer(input, "max_output_tokens", 128, Math.min(8192, context / 2),
                existing == null ? Math.min(DEFAULT_OUTPUT, context / 2) : Math.min(existing.output(), context / 2));
        int concurrency = integer(input, "max_concurrency", 1, 4, existing == null ? 2 : existing.concurrency());
        var secret = existing != null && !input.has("api_key") ? existing.secret()
                : keys.encrypt(owner, id, credential(input));
        return new Profile(owner,id,name,base,model,context,output,concurrency,false,false,"pendiente",null,null,null,secret);
    }

    private static String credential(JsonNode input) {
        String key = field(input, "api_key", 1024, "");
        if (!key.matches("[!-~]{8,1024}")) throw invalid("La clave del proveedor no es válida.");
        return key;
    }

    private static String field(JsonNode input, String key, int max, String fallback) {
        var node = input.path(key);
        String value = node.isMissingNode() ? fallback : node.isTextual() ? node.asText().strip() : "";
        if (value.isEmpty() || value.length() > max || value.chars().anyMatch(Character::isISOControl)) {
            throw invalid("Campo inválido: " + key);
        }
        return value;
    }

    private static int integer(JsonNode input, String name, int min, int max, int fallback) {
        var value = input.path(name);
        if (value.isMissingNode()) return fallback;
        if (!value.isIntegralNumber() || !value.canConvertToInt() || value.intValue() < min || value.intValue() > max) {
            throw invalid("Valor fuera del rango: " + name);
        }
        return value.intValue();
    }

    private Profile read(ResultSet rs, int row) throws SQLException {
        return new Profile(rs.getLong("usuario_id"),rs.getString("id"),rs.getString("nombre"),rs.getString("base_url"),
                rs.getString("modelo"),rs.getInt("context_window"),rs.getInt("max_output_tokens"),rs.getInt("max_concurrency"),
                rs.getBoolean("supports_vision"),rs.getBoolean("supports_json_mode"),rs.getString("estado"),
                rs.getObject("ultima_prueba",LocalDateTime.class),rs.getObject("latencia_ms",Long.class),
                rs.getObject("consentimiento",LocalDateTime.class),
                new ProviderKeys.Secret(rs.getBytes("nonce"),rs.getBytes("clave_cifrada"),rs.getString("kid"),rs.getString("sufijo")));
    }

    private static ApiException invalid(String detail) { return new ApiException(HttpStatus.BAD_REQUEST, detail); }

    record Profile(long owner,String id,String name,String base,String model,int context,int output,int concurrency,
                   boolean vision,boolean jsonMode,String status,LocalDateTime tested,Long latency,LocalDateTime consent,
                   ProviderKeys.Secret secret) {
        ObjectNode view(ObjectMapper mapper) {
            return mapper.createObjectNode().put("id",id).put("name",name).put("base_url",base).put("model",model)
                    .put("context_window",context).put("max_output_tokens",output).put("max_concurrency",concurrency)
                    .put("supports_vision",vision).put("supports_json_mode",jsonMode).put("status",status)
                    .put("last_test_at",tested == null ? null : tested.toString()+"Z")
                    .put("latency_ms",latency).put("consent_at",consent == null ? null : consent.toString()+"Z")
                    .put("key_suffix",secret.suffix());
        }
    }
}
