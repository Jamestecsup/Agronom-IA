package com.agronomia.backend.user;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class IdentificationPipelineService {
    private static final List<String> CATEGORIES = List.of("description","light","watering","soil","climate","flowering","uses","care");
    private static final Map<String,String> TITLES = Map.of(
            "description","Descripción","light","Luz","watering","Riego","soil","Suelo",
            "climate","Clima","flowering","Floración","uses","Usos","care","Cuidados");
    private static final List<String> MATERIAL_CATEGORIES = List.of("light","watering","soil","climate","care");
    private static final Set<String> UNKNOWN = Set.of("unknown","desconocido","desconocida","no disponible","n/a","no se conoce","sin informacion","sin información");
    private static final String INSUFFICIENT = "Identificamos la especie, pero no encontramos información suficiente sobre sus cuidados. Prueba con otra foto o pregunta al asistente.";
    public static final double MIN_CONFIDENCE = 0.20;
    public static final double CONFIDENT_CONFIDENCE = 0.50;

    private final PlantIdentificationClient plantNet;
    private final PlantAiClient ai;
    private final String project;
    private final String lang;

    public IdentificationPipelineService(PlantIdentificationClient plantNet, PlantAiClient ai,
                                        @Value("${plantnet.project:${PLANTNET_PROJECT:all}}") String project,
                                        @Value("${plantnet.lang:${PLANTNET_LANG:es}}") String lang) {
        this.plantNet = plantNet; this.ai = ai; this.project = project; this.lang = lang;
    }

    public Map<String, Object> identify(List<byte[]> images, String organ) {
        Map<String, Object> data;
        try { data = plantNet.identify(images, organ, project, lang); }
        catch (UserApiException e) { return Map.of("status","failed","message",e.getMessage()); }

        List<Map<String, Object>> results = asListOfMaps(data.get("results"));
        if (results.isEmpty()) return Map.of("status","unidentified","message","No se encontraron coincidencias.","raw",data);

        results.sort((a,b) -> Double.compare(toDouble(b.get("score")), toDouble(a.get("score"))));
        Map<String, Object> best = results.get(0);
        double score = toDouble(best.get("score"));
        List<Map<String, Object>> candidates = new ArrayList<>();
        for (Map<String,Object> r : results) {
            Map<String,Object> sp = asMap(r.get("species"));
            Map<String,Object> c = new LinkedHashMap<>();
            c.put("scientific_name", first(sp.get("scientificNameWithoutAuthor"), sp.get("scientificName")));
            c.put("common_names", sp.getOrDefault("commonNames", List.of()));
            c.put("family", str(asMap(sp.get("family")).get("scientificNameWithoutAuthor")));
            c.put("confidence", toDouble(r.get("score")));
            candidates.add(c);
        }

        if (score < MIN_CONFIDENCE) return Map.of("status","unidentified","message","No pudimos reconocer la planta con suficiente claridad.","candidates",candidates,"raw",data);
        if (score < CONFIDENT_CONFIDENCE) {
            enrichCandidates(candidates);
            return Map.of("status","ambiguous","candidates",candidates,"raw",data);
        }
        try {
            Map<String, Object> plant = generatePlantInfo(best, candidates);
            return new LinkedHashMap<>(Map.of("status","identified","plant",plant,"candidates",candidates,"raw",data));
        } catch (Exception e) {
            String name = str(asMap(best.get("species")).get("scientificNameWithoutAuthor"));
            if (name.isBlank()) name = str(asMap(best.get("species")).get("scientificName"));
            return Map.of("status","insufficient","message",INSUFFICIENT,"plant_name",name,"candidates",candidates,"raw",data);
        }
    }

    public Map<String, Object> generatePlantInfo(Map<String, Object> plantnetResult, List<Map<String, Object>> candidates) {
        Map<String, Object> header = plantHeader(plantnetResult);
        String ficha = ficha(plantnetResult, candidates);
        String refined = "";
        try { refined = str(ai.jsonCall(ai.loadSkill("refine-prompt"), ficha, 0.0).get("prompt")); } catch (Exception ignored) {}

        Map<String,String> catSkills = new LinkedHashMap<>();
        for (String c : CATEGORIES) catSkills.put(c, ai.loadCategorySkill(c));
        Map<String, Object> rawTexts = ai.jsonCall(categoriesSystem(catSkills), categoriesUser(ficha, refined), 0.0);
        Map<String,String> texts = new LinkedHashMap<>();
        for (String c : CATEGORIES) texts.put(c, cleanText(rawTexts.get(c)));

        Map<String, List<Map<String,Object>>> glossary = glossaryBatch(texts);
        Map<String, List<Map<String,Object>>> materials = materialsBatch(header.get("scientific_name").toString(), texts);

        List<Map<String,Object>> sections = new ArrayList<>();
        boolean any = false;
        for (String key : CATEGORIES) {
            String body = texts.getOrDefault(key, "");
            if (body.isBlank()) body = "Aún no tenemos información suficiente sobre este tema.";
            else any = true;
            Map<String,Object> sec = new LinkedHashMap<>();
            sec.put("key", key); sec.put("title", TITLES.get(key)); sec.put("body", body);
            sec.put("terms", glossary.getOrDefault(key, List.of()));
            sec.put("materials", materials.getOrDefault(key, List.of()));
            sections.add(sec);
        }
        if (!any) throw new RuntimeException("insufficient");
        header.put("sections", sections);
        return header;
    }

    private void enrichCandidates(List<Map<String, Object>> candidates) {
        if (candidates.isEmpty()) return;
        try {
            Map<String, Object> data = ai.jsonCall(ai.loadSkill("candidate-hints"), "Especies candidatas:\n" + toJson(candidatesPayload(candidates)), 0.2);
            Map<String, Map<String,Object>> norm = new HashMap<>();
            for (var e : data.entrySet()) if (e.getValue() instanceof Map<?,?> m) norm.put(e.getKey().toLowerCase().trim(), asMap(m));
            for (Map<String,Object> c : candidates) {
                Map<String,Object> extra = norm.getOrDefault(str(c.get("scientific_name")).toLowerCase(), Map.of());
                c.put("hint", cleanHint(extra.get("hint")));
                c.put("terms", cleanTerms(extra.get("terms")));
            }
        } catch (Exception e) {
            for (Map<String,Object> c : candidates) { c.put("hint",""); c.put("terms", List.of()); }
        }
    }

    private List<Map<String,Object>> candidatesPayload(List<Map<String,Object>> candidates) {
        List<Map<String,Object>> out = new ArrayList<>();
        for (Map<String,Object> c : candidates) {
            Map<String,Object> m = new LinkedHashMap<>();
            m.put("scientific_name", c.get("scientific_name")); m.put("common_names", c.get("common_names"));
            m.put("family", c.get("family")); m.put("confidence", Math.round(toDouble(c.get("confidence"))*100));
            out.add(m);
        }
        return out;
    }

    private String categoriesSystem(Map<String,String> catSkills) {
        StringBuilder sb = new StringBuilder("Eres botánico experto. Genera la ficha completa de la planta en español.\nPara cada categoría debes seguir EXACTAMENTE la skill correspondiente.\n\n");
        for (var e : catSkills.entrySet()) sb.append("\n### SKILL DE LA CATEGORÍA `").append(e.getKey()).append("`\n").append(e.getValue()).append("\n");
        sb.append("\nResponde ÚNICAMENTE con un objeto JSON válido, sin texto adicional ni bloques de código Markdown, con exactamente estas claves: ");
        for (int i=0;i<CATEGORIES.size();i++) { if (i>0) sb.append(", "); sb.append("\"").append(CATEGORIES.get(i)).append("\""); }
        sb.append(". Cada valor es el texto extenso de esa categoría (4 a 6 frases). Si no conoces la planta, usa \"unknown\" en cada valor.");
        return sb.toString();
    }
    private String categoriesUser(String ficha, String refined) { return "Ficha de Pl@ntNet:\n" + ficha + (refined==null||refined.isBlank()?"":"\n\nPrompt de investigación:\n"+refined); }

    private Map<String, List<Map<String,Object>>> glossaryBatch(Map<String,String> texts) {
        List<String> keys = new ArrayList<>(); for (String k : CATEGORIES) if (!cleanText(texts.get(k)).isBlank()) keys.add(k);
        Map<String, List<Map<String,Object>>> result = new LinkedHashMap<>(); for (String k : CATEGORIES) result.put(k, List.of());
        if (keys.isEmpty()) return result;
        String sys = ai.loadSkill("glossary") + "\n\nVas a recibir VARIAS categorías a la vez. Devuelve un único JSON con una clave por categoría, y en cada una la lista de términos: {\"<categoría>\": [{\"word\":\"...\",\"meaning\":\"...\"}]}";
        StringBuilder user = new StringBuilder(); for (String k : keys) user.append("### ").append(k).append("\n").append(texts.get(k)).append("\n\n");
        try {
            Map<String,Object> data = ai.jsonCall(sys, user.toString(), 0.0);
            for (String k : keys) { Object v = data.get(k); if (v instanceof List<?> l) result.put(k, castTermList(l)); }
        } catch (Exception ignored) {}
        return result;
    }

    private Map<String, List<Map<String,Object>>> materialsBatch(String plantName, Map<String,String> texts) {
        List<String> keys = new ArrayList<>(); for (String k : MATERIAL_CATEGORIES) if (!cleanText(texts.get(k)).isBlank()) keys.add(k);
        Map<String, List<Map<String,Object>>> result = new LinkedHashMap<>(); for (String k : CATEGORIES) result.put(k, List.of());
        if (keys.isEmpty()) return result;
        String sys = ai.loadSkill("materials") + "\n\nVas a recibir VARIAS categorías a la vez. Devuelve un único JSON con una clave por categoría: {\"<categoría>\": [{\"name\":\"...\",\"detail\":\"...\"}]}";
        StringBuilder user = new StringBuilder("Planta: ").append(plantName).append("\n\n"); for (String k : keys) user.append("### ").append(k).append("\n").append(texts.get(k)).append("\n\n");
        try {
            Map<String,Object> data = ai.jsonCall(sys, user.toString(), 0.0);
            for (String k : keys) { Object v = data.get(k); if (v instanceof List<?> l) result.put(k, castTermList(l)); }
        } catch (Exception ignored) {}
        return result;
    }

    private List<Map<String,Object>> castTermList(List<?> l) {
        List<Map<String,Object>> out = new ArrayList<>();
        for (Object o : l) if (o instanceof Map<?,?> m) out.add(asMap(m));
        return out;
    }

    private Map<String, Object> plantHeader(Map<String, Object> result) {
        Map<String, Object> species = asMap(result.get("species"));
        List<?> commons = species.get("commonNames") instanceof List<?> l ? l : List.of();
        Map<String, Object> h = new LinkedHashMap<>();
        h.put("common_name", commons.isEmpty() ? "" : str(commons.get(0)));
        h.put("scientific_name", first(species.get("scientificNameWithoutAuthor"), species.get("scientificName")));
        h.put("authorship", str(species.get("scientificNameAuthorship")));
        h.put("genus", str(asMap(species.get("genus")).get("scientificNameWithoutAuthor")));
        h.put("family", str(asMap(species.get("family")).get("scientificNameWithoutAuthor")));
        h.put("confidence", toDouble(result.get("score")));
        h.put("gbif_id", str(result.get("gbif")));
        h.put("powo_id", str(asMap(result.get("powo")).get("id")));
        h.put("iucn_category", str(asMap(result.get("iucn")).get("category")));
        return h;
    }

    private String ficha(Map<String, Object> result, List<Map<String, Object>> candidates) {
        Map<String, Object> species = asMap(result.get("species"));
        List<?> commons = species.get("commonNames") instanceof List<?> l ? l : List.of();
        StringBuilder sb = new StringBuilder();
        sb.append("- Nombre científico: ").append(first(species.get("scientificNameWithoutAuthor"), species.get("scientificName"))).append("\n");
        if (!str(species.get("scientificNameAuthorship")).isBlank()) sb.append("- Autoría: ").append(species.get("scientificNameAuthorship")).append("\n");
        if (!commons.isEmpty()) sb.append("- Nombres comunes: ").append(String.join(", ", commons.stream().map(String::valueOf).toList())).append("\n");
        sb.append("- Género: ").append(str(asMap(species.get("genus")).get("scientificNameWithoutAuthor"))).append("\n");
        sb.append("- Familia: ").append(str(asMap(species.get("family")).get("scientificNameWithoutAuthor"))).append("\n");
        sb.append("- Confianza Pl@ntNet: ").append(String.format(Locale.US, "%.2f", toDouble(result.get("score")))).append("\n");
        if (candidates != null && !candidates.isEmpty()) {
            sb.append("- Candidatas: ");
            for (int i=0;i<candidates.size();i++) { if (i>0) sb.append("; "); var c=candidates.get(i); sb.append(c.get("scientific_name")).append(" (").append(String.format(Locale.US,"%.0f%%", toDouble(c.get("confidence"))*100)).append(")"); }
            sb.append("\n");
        }
        return sb.toString();
    }

    private String cleanText(Object v) {
        String t = str(v);
        return UNKNOWN.contains(t.toLowerCase().replaceAll("[.]$", "")) ? "" : t;
    }
    private String cleanHint(Object v) { String t = cleanText(v); return t.length()>800 ? t.substring(0,800) : t; }
    private List<Map<String,Object>> cleanTerms(Object v) {
        if (!(v instanceof List<?> l)) return List.of();
        List<Map<String,Object>> out = new ArrayList<>();
        for (Object o : l) {
            if (!(o instanceof Map<?,?> m)) continue;
            String w = str(m.get("word")); String me = str(m.get("meaning"));
            if (!w.isBlank() && !me.isBlank()) out.add(Map.of("word", w.length()>80?w.substring(0,80):w, "meaning", me.length()>300?me.substring(0,300):me));
            if (out.size()>=6) break;
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private Map<String,Object> asMap(Object o) { return o instanceof Map<?,?> m ? (Map<String,Object>) m : new LinkedHashMap<>(); }
    @SuppressWarnings("unchecked")
    private List<Map<String,Object>> asListOfMaps(Object o) {
        if (!(o instanceof List<?> l)) return new ArrayList<>();
        List<Map<String,Object>> out = new ArrayList<>();
        for (Object i : l) if (i instanceof Map<?,?> m) out.add((Map<String,Object>) m);
        return out;
    }
    private String str(Object o) { return o == null ? "" : String.valueOf(o).trim(); }
    private double toDouble(Object o) { if (o instanceof Number n) return n.doubleValue(); try { return Double.parseDouble(String.valueOf(o)); } catch (Exception e) { return 0; } }
    private String first(Object... vals) { for (Object v : vals) { String s=str(v); if (!s.isBlank()) return s; } return ""; }
    private String toJson(Object o) { try { return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(o); } catch (Exception e) { return "[]"; } }
}
