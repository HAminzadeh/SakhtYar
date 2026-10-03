package com.sakhtyar.agents.input;

import com.sakhtyar.agents.glossary.PersianGlossaryService;
import com.sakhtyar.knowledge.domain.*;
import java.util.*;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InputGatewayKnowledgeResolver {

    private final PersianGlossaryService legacyGlossary;
    private final KnowledgeTermRepository termRepository;
    private final KnowledgeTermAliasRepository aliasRepository;

    public InputGatewayKnowledgeResolver(
            PersianGlossaryService legacyGlossary,
            KnowledgeTermRepository termRepository,
            KnowledgeTermAliasRepository aliasRepository
    ) {
        this.legacyGlossary=legacyGlossary;
        this.termRepository=termRepository;
        this.aliasRepository=aliasRepository;
    }

    @Transactional(readOnly=true)
    public Resolution resolve(String normalizedText) {
        LinkedHashMap<String,Object> recognized=new LinkedHashMap<>();
        LinkedHashSet<String> ambiguous=new LinkedHashSet<>();

        legacyGlossary.recognizedTerms(normalizedText).forEach((surface,meaning)->{
            recognized.put(surface,Map.of(
                    "source","LEGACY_GLOSSARY",
                    "meaning",meaning
            ));
        });

        List<KnowledgeTermEntity> terms=
                termRepository.findByStatusOrderByNameFaAsc(KnowledgeReviewStatus.APPROVED);

        HashMap<UUID,KnowledgeTermEntity> byId=new HashMap<>();
        HashMap<String,UUID> owner=new HashMap<>();

        for (KnowledgeTermEntity term:terms) {
            byId.put(term.getId(),term);
            String surface=PersianInputNormalizer.normalize(term.getNameFa());
            if (!surface.isBlank() && containsPhrase(normalizedText,surface)) {
                put(recognized,ambiguous,owner,term.getNameFa(),surface,"KNOWLEDGE_TERM",null,term);
            }
        }

        for (KnowledgeTermAliasEntity alias:
                aliasRepository.findByStatusOrderByAliasNormalizedAsc(KnowledgeReviewStatus.APPROVED)) {
            if (alias.getLocale()!=null && !alias.getLocale().toLowerCase().startsWith("fa")) continue;
            KnowledgeTermEntity term=byId.get(alias.getTermId());
            if (term==null) continue;

            String surface=PersianInputNormalizer.normalize(alias.getAliasNormalized());
            if (!surface.isBlank() && containsPhrase(normalizedText,surface)) {
                put(recognized,ambiguous,owner,alias.getAlias(),surface,"KNOWLEDGE_ALIAS",alias.getId(),term);
            }
        }

        return new Resolution(Map.copyOf(recognized),List.copyOf(ambiguous));
    }

    private void put(
            Map<String,Object> recognized,
            Set<String> ambiguous,
            Map<String,UUID> owner,
            String displaySurface,
            String normalizedSurface,
            String source,
            UUID aliasId,
            KnowledgeTermEntity term
    ) {
        UUID previous=owner.putIfAbsent(normalizedSurface,term.getId());
        if (previous!=null && !previous.equals(term.getId())) {
            ambiguous.add(displaySurface);
            return;
        }

        LinkedHashMap<String,Object> details=new LinkedHashMap<>();
        details.put("source",source);
        details.put("termId",term.getId().toString());
        details.put("code",term.getCode());
        details.put("nameFa",term.getNameFa());
        if (aliasId!=null) details.put("aliasId",aliasId.toString());
        if (term.getNameEn()!=null) details.put("nameEn",term.getNameEn());
        if (term.getUnitCode()!=null) details.put("unitCode",term.getUnitCode());
        if (term.getConfidence()!=null) details.put("confidence",term.getConfidence());

        recognized.put(displaySurface,Map.copyOf(details));
    }

    private boolean containsPhrase(String text,String phrase) {
        int from=0;
        while (true) {
            int index=text.indexOf(phrase,from);
            if (index<0) return false;
            int before=index-1;
            int after=index+phrase.length();
            boolean left=before<0 || !Character.isLetterOrDigit(text.charAt(before));
            boolean right=after>=text.length() || !Character.isLetterOrDigit(text.charAt(after));
            if (left && right) return true;
            from=index+1;
        }
    }

    public record Resolution(
            Map<String,Object> recognizedTerms,
            List<String> ambiguousSurfaces
    ) {}
}