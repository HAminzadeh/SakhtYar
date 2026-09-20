package com.sakhtyar.agents.glossary;

import com.sakhtyar.audit.application.AuditService;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PersianGlossaryService {

    private static final Map<String, String> BUILT_INS = builtIns();

    private final GlossaryEntryRepository repository;
    private final AuditService auditService;

    public PersianGlossaryService(
            GlossaryEntryRepository repository,
            AuditService auditService
    ) {
        this.repository = repository;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public Map<String, String> recognizedTerms(String text) {
        LinkedHashMap<String, String> found = new LinkedHashMap<>();
        if (text == null || text.isBlank()) {
            return found;
        }

        String normalizedText = normalize(text);
        allApprovedTerms().forEach((term, meaning) -> {
            if (normalizedText.contains(normalize(term))) {
                found.put(term, meaning);
            }
        });
        return found;
    }

    @Transactional(readOnly = true)
    public boolean isKnown(String term) {
        if (term == null || term.isBlank()) {
            return true;
        }
        String normalized = normalize(term);
        if (BUILT_INS.keySet().stream().anyMatch(key -> normalize(key).equals(normalized))) {
            return true;
        }
        return repository.findByNormalizedTerm(normalized)
                .filter(entity -> entity.getStatus() == GlossaryStatus.APPROVED)
                .isPresent();
    }

    @Transactional(readOnly = true)
    public Map<String, String> all() {
        return Collections.unmodifiableMap(allApprovedTerms());
    }

    @Transactional(readOnly = true)
    public List<GlossaryEntryView> entries() {
        return repository.findAllByOrderByUpdatedAtDesc().stream()
                .map(this::view)
                .toList();
    }

    @Transactional
    public GlossaryEntryView createDraft(
            String term,
            String meaning,
            List<String> aliases
    ) {
        String cleanTerm = cleanRequired(term, "term");
        String cleanMeaning = cleanRequired(meaning, "meaning");
        String normalized = normalize(cleanTerm);
        String aliasesText = normalizeAliases(aliases);

        GlossaryEntryEntity entity = repository.findByNormalizedTerm(normalized)
                .orElse(null);
        if (entity != null && entity.getStatus() == GlossaryStatus.APPROVED) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "این اصطلاح قبلاً در واژه‌نامه تأیید شده است."
            );
        }

        if (entity == null) {
            Instant now = Instant.now();
            entity = new GlossaryEntryEntity(
                    UUID.randomUUID(),
                    cleanTerm,
                    normalized,
                    cleanMeaning,
                    aliasesText,
                    GlossaryStatus.DRAFT,
                    currentActor(),
                    null,
                    now,
                    now
            );
        } else {
            entity.updateDraft(cleanTerm, cleanMeaning, aliasesText);
        }
        GlossaryEntryEntity saved = repository.save(entity);
        auditService.record(
                "PERSIAN_GLOSSARY",
                saved.getId(),
                "GLOSSARY_DRAFT_SAVED",
                Map.of("term", saved.getTerm(), "status", saved.getStatus().name())
        );
        return view(saved);
    }

    @Transactional
    public GlossaryEntryView approve(UUID id) {
        GlossaryEntryEntity entity = require(id);
        entity.approve(currentActor());
        GlossaryEntryEntity saved = repository.save(entity);
        auditService.record(
                "PERSIAN_GLOSSARY",
                saved.getId(),
                "GLOSSARY_APPROVED",
                Map.of("term", saved.getTerm(), "status", saved.getStatus().name())
        );
        return view(saved);
    }

    @Transactional
    public GlossaryEntryView reject(UUID id) {
        GlossaryEntryEntity entity = require(id);
        entity.reject();
        GlossaryEntryEntity saved = repository.save(entity);
        auditService.record(
                "PERSIAN_GLOSSARY",
                saved.getId(),
                "GLOSSARY_REJECTED",
                Map.of("term", saved.getTerm(), "status", saved.getStatus().name())
        );
        return view(saved);
    }

    private LinkedHashMap<String, String> allApprovedTerms() {
        LinkedHashMap<String, String> values = new LinkedHashMap<>(BUILT_INS);
        for (GlossaryEntryEntity entity : repository
                .findAllByStatusOrderByTermAsc(GlossaryStatus.APPROVED)) {
            values.put(entity.getTerm(), entity.getMeaning());
            for (String alias : aliases(entity.getAliasesText())) {
                values.put(alias, entity.getMeaning());
            }
        }
        return values;
    }

    private GlossaryEntryEntity require(UUID id) {
        return repository.findById(id).orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Glossary entry not found."
        ));
    }

    private GlossaryEntryView view(GlossaryEntryEntity entity) {
        return new GlossaryEntryView(
                entity.getId(),
                entity.getTerm(),
                entity.getMeaning(),
                aliases(entity.getAliasesText()),
                entity.getStatus(),
                entity.getCreatedBy(),
                entity.getApprovedBy(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private String normalizeAliases(List<String> aliases) {
        if (aliases == null || aliases.isEmpty()) {
            return null;
        }
        Set<String> cleaned = new LinkedHashSet<>();
        for (String alias : aliases) {
            if (alias != null && !alias.isBlank()) {
                cleaned.add(alias.trim());
            }
        }
        return cleaned.isEmpty() ? null : String.join("\n", cleaned);
    }

    private List<String> aliases(String aliasesText) {
        if (aliasesText == null || aliasesText.isBlank()) {
            return List.of();
        }
        return Arrays.stream(aliasesText.split("\\R"))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .toList();
    }

    private String cleanRequired(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    field + " is required."
            );
        }
        return value.trim();
    }

    public String normalize(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder result = new StringBuilder();
        for (char c : value.trim().toCharArray()) {
            result.append(switch (c) {
                case '\u0643' -> '\u06A9';
                case '\u064A' -> '\u06CC';
                case '\u06F0', '\u0660' -> '0';
                case '\u06F1', '\u0661' -> '1';
                case '\u06F2', '\u0662' -> '2';
                case '\u06F3', '\u0663' -> '3';
                case '\u06F4', '\u0664' -> '4';
                case '\u06F5', '\u0665' -> '5';
                case '\u06F6', '\u0666' -> '6';
                case '\u06F7', '\u0667' -> '7';
                case '\u06F8', '\u0668' -> '8';
                case '\u06F9', '\u0669' -> '9';
                default -> c;
            });
        }
        return result.toString()
                .replaceAll("\\s+", " ")
                .trim()
                .toLowerCase(Locale.ROOT);
    }

    private String currentActor() {
        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return "system";
        }
        return authentication.getName();
    }

    private static Map<String, String> builtIns() {
        LinkedHashMap<String, String> values = new LinkedHashMap<>();
        values.put("کلنگی", "ساختمان قدیمی که معمولاً برای تخریب و نوسازی بررسی می‌شود");
        values.put("بر ملک", "عرض ضلع ملک که به گذر یا خیابان متصل است");
        values.put("برش", "در گفتار بازار املاک معمولاً به معنی عرض برِ ملک است");
        values.put("دو نبش", "ملکی که به دو گذر یا معبر متصل است");
        values.put("قدرالسهم", "سهم هر مالک از عرصه یا زمین مشترک");
        values.put("بلاعوض", "مبلغی که در برخی قراردادهای مشارکت سازنده به مالک پرداخت می‌کند");
        values.put("پهنه", "طبقه‌بندی شهرسازی تعیین‌کننده نوع استفاده و ضوابط کلی ساخت");
        values.put("تراکم", "میزان مجاز زیربنای قابل ساخت نسبت به مساحت زمین");
        values.put("سطح اشغال", "درصدی از مساحت زمین که می‌تواند در یک طبقه اشغال شود");
        values.put("بر اصلاحی", "بخشی از ملک که طبق طرح شهری ممکن است در مسیر اصلاح گذر قرار گیرد");
        values.put("اصلاحی", "بخشی از ملک که مطابق طرح یا ضابطه شهری ممکن است از سطح قابل استفاده کسر شود");
        values.put("عقب‌نشینی", "الزام فاصله گرفتن بنا یا مرز مؤثر ساخت از حد مشخص‌شده");
        values.put("پخ", "بریدگی گوشه ملک در تقاطع گذرها مطابق ضوابط شهری");
        values.put("تجمیع", "ترکیب دو یا چند پلاک یا ملک برای تشکیل قطعه بزرگ‌تر");
        values.put("عرصه", "زمین یا بستر اصلی ملک");
        values.put("اعیان", "بنای احداث‌شده روی عرصه");
        values.put("پیشروی", "میزان مجاز توسعه سطح بنا در عمق یا بخش مشخصی از زمین");
        values.put("مشاعات", "فضاهای مشترک ساختمان مانند راه‌پله، آسانسور و راهروها");
        return Collections.unmodifiableMap(values);
    }

}
