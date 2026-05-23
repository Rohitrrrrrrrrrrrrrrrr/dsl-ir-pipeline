package com.dslpipeline.vocabulary;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Business vocabulary — aliases and synonyms resolved BEFORE the LLM is called.
 *
 * Per the platform design, the glossary is a governed constraint set, not prompt
 * text: friendly business terms are mapped to canonical schema paths or functions
 * deterministically. This bounds ambiguity and keeps Structured Logic readable.
 *
 * Aliases  : one friendly term  -> one canonical path/function.
 * Synonyms : many surface forms -> one canonical term.
 *
 * @author Nikunj Malik
 */
@Component
public class Vocabulary {

    private final Map<String, String> aliases = new LinkedHashMap<>();
    private final Map<String, String> synonyms = new LinkedHashMap<>();

    public Vocabulary() {
        // canonical path aliases
        aliases.put("policy holder", "customer");
        aliases.put("policyholder", "customer");
        aliases.put("applicant", "customer");
        aliases.put("member", "customer");

        // synonym surface forms -> canonical phrase
        synonyms.put("age at date", "calculateAge");
        synonyms.put("age on", "calculateAge");
        synonyms.put("years old", "age");
        synonyms.put("over", ">");
        synonyms.put("greater than", ">");
        synonyms.put("more than", ">");
        synonyms.put("at least", ">=");
        synonyms.put("under", "<");
        synonyms.put("less than", "<");
        synonyms.put("fewer than", "<");
        synonyms.put("at most", "<=");
        synonyms.put("no more than", "<=");
        synonyms.put("equals", "==");
        synonyms.put("equal to", "==");
        synonyms.put("is not", "!=");
        synonyms.put("not equal to", "!=");
    }

    public Map<String, String> getAliases() { return aliases; }
    public Map<String, String> getSynonyms() { return synonyms; }

    /** Merge a request-supplied vocabulary on top of the defaults (non-destructive copy). */
    public Vocabulary mergedWith(Map<String, String> extraAliases, Map<String, String> extraSynonyms) {
        Vocabulary v = new Vocabulary();
        if (extraAliases != null) v.aliases.putAll(extraAliases);
        if (extraSynonyms != null) v.synonyms.putAll(extraSynonyms);
        return v;
    }

    /** Resolve an alias to its canonical form (case-insensitive); returns input if unknown. */
    public String resolveAlias(String term) {
        if (term == null) return null;
        String hit = aliases.get(term.toLowerCase().trim());
        return hit != null ? hit : term;
    }

    /** Resolve a synonym surface form; returns input if unknown. */
    public String resolveSynonym(String phrase) {
        if (phrase == null) return null;
        String hit = synonyms.get(phrase.toLowerCase().trim());
        return hit != null ? hit : phrase;
    }

    /**
     * Normalise a whole sentence: replace known multi-word synonym phrases
     * (longest-first) so downstream parsing sees canonical operators.
     */
    public String normaliseSentence(String sentence) {
        if (sentence == null) return null;
        String out = sentence;
        // longest phrases first so "no more than" wins over "more than"
        for (String phrase : synonyms.keySet().stream()
                .sorted((x, y) -> y.length() - x.length()).toList()) {
            out = out.replaceAll("(?i)\\b" + java.util.regex.Pattern.quote(phrase) + "\\b",
                    java.util.regex.Matcher.quoteReplacement(synonyms.get(phrase)));
        }
        return out;
    }
}
