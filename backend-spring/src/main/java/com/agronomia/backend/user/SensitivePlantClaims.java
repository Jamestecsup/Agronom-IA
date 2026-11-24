package com.agronomia.backend.user;

import java.text.BreakIterator;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

final class SensitivePlantClaims {
    static final String NOTICE = "Consulta una fuente confiable antes de consumir esta planta o usarla con fines medicinales.";
    private static final String DEFAULT_TERMS = """
            medicinal,medicinales,medicamento,terapéutico,terapéutica,terapéuticos,terapéuticas,
            curativo,curativa,curativos,curativas,cura,curan,curar,remedio,remedios,analgésico,analgésica,
            antiinflamatorio,antiinflamatoria,antibacteriano,antibacteriana,alivia el dolor,trata la fiebre,
            tóxico,tóxica,tóxicos,tóxicas,toxicidad,no tóxico,no tóxica,veneno,venenoso,venenosa,
            intoxicación,intoxicaciones,inocuo,inocua,inofensivo,inofensiva,comestible,comestibles,
            consumo humano,consumo seguro,sin riesgo de intoxicación,
            seguro para comer,segura para comer,seguro para consumir,segura para consumir,
            se puede consumir,se pueden consumir,puede consumirse,pueden consumirse,consumir sin riesgo,
            se puede comer,se pueden comer,puede comerse,pueden comerse,apto para el consumo,apta para el consumo,
            seguro ingerir,seguro para ingerir,segura para ingerir,puede ingerirse,pueden ingerirse,
            se consume,se consumen,se come,se comen,para alimentación humana
            """;
    private static final Locale SPANISH = Locale.forLanguageTag("es");
    private final Pattern terms;

    SensitivePlantClaims(String additionalTerms) {
        String alternatives = Stream.concat(Arrays.stream(DEFAULT_TERMS.split(",")),
                        Arrays.stream(additionalTerms.split(",")))
                .map(String::strip)
                .filter(term -> !term.isEmpty())
                .map(SensitivePlantClaims::normalize)
                .distinct()
                .map(Pattern::quote)
                .collect(Collectors.joining("|"));
        terms = Pattern.compile("(?<![\\p{L}\\p{N}])(?:" + alternatives + ")(?![\\p{L}\\p{N}])");
    }

    boolean containsSensitiveTerm(String text) {
        return terms.matcher(normalize(text)).find();
    }

    Result filter(String text) {
        var kept = new ArrayList<String>();
        int removed = 0;
        var sentences = BreakIterator.getSentenceInstance(SPANISH);
        for (String line : text.split("\\R")) {
            sentences.setText(line);
            int start = sentences.first();
            for (int end = sentences.next(); end != BreakIterator.DONE; start = end, end = sentences.next()) {
                String sentence = line.substring(start, end).strip();
                if (containsSensitiveTerm(sentence)) {
                    removed++;
                } else if (!sentence.isBlank()) {
                    kept.add(sentence);
                }
            }
        }
        return new Result(removed == 0 ? text : String.join(" ", kept), removed);
    }

    private static String normalize(String text) {
        return Normalizer.normalize(text.toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "").replaceAll("[\\s\\p{Pd}]+", " ");
    }

    record Result(String text, int removed) {}
}
