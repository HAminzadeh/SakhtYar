package com.sakhtyar.quality;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Explanations are not a PASS assertion. A check clears only when its real source reports proof. */
public final class Phase09SafetyBlockerCatalog {
    private Phase09SafetyBlockerCatalog() {}
    public record Requirement(String code, String group, String faTitle, String remediation, String evidenceType) {
        public Map<String,Object> describe(boolean blocked) {
            return Map.of("code",code,"group",group,"titleFa",faTitle,"remediationFa",remediation,
                "evidenceType",evidenceType,"state",blocked?"BLOCKED":"NOT_BLOCKING_IN_THIS_SNAPSHOT");
        }
    }
    private static final List<Requirement> REQUIREMENTS=List.of(
        new Requirement("CORE_FIXTURE_GATE_NOT_VERIFIED","DB","مجوز Fixture محدود","چهار جدول اصلی و نقش ایمن TEST را بدون تغییر خودکار DB بررسی کنید.","CORE_FIXTURE_READINESS"),
        new Requirement("PHYSICAL_RESTRICTED_TEST_LOGIN_NOT_VERIFIED","DB","اتصال فیزیکی TEST","اتصال مستقل و از پیش تأمین‌شده با current_user = session_user و نقش workspace مشخص لازم است.","DEDICATED_JDBC_READ_ONLY"),
        new Requirement("RESTRICTED_DB_PRIVILEGES_NOT_VERIFIED","DB","مجوزهای نقش محدود","NOSUPERUSER، NOBYPASSRLS، NOINHERIT و نبود مالکیت یا عضویت خطرناک در PostgreSQL اثبات شود.","PG_CATALOG_READ_ONLY"),
        new Requirement("CROSS_WORKSPACE_REAL_INVISIBILITY_NOT_PROVEN","DB","نامرئی‌بودن REAL و Workspace دیگر","پروب مثبت و منفی با مبنای مستقل دارای داده در Workspace دیگر و نقش محدود لازم است.","INDEPENDENT_POSITIVE_CONTROL"),
        new Requirement("FULL_APPLICATION_SCOPE_NOT_VERIFIED","SCOPE","پوشش همه مسیرهای برنامه","مسیرهای Repository، FK، RLS، Object Storage و داده‌های نامحدود به scope ممیزی شوند.","APPLICATION_SCOPE_AUDIT"),
        new Requirement("EXTERNAL_EFFECTS_NOT_GLOBALLY_GUARDED","EGRESS","جلوگیری از اثرات خارجی","Fake Adapterها در تمام providerهای ایمیل، پیامک، AI، Crawler، Redis، MinIO و Outbox تزریق شوند.","END_TO_END_EGRESS_PROOF"),
        new Requirement("FULL_BUSINESS_EXECUTOR_CATALOG_NOT_READY","EXECUTOR","Executorهای تجاری","Executor مستقل هر سناریو به HTTP و شواهد مرورگر و دیتابیس متصل شود؛ تشخیص فاز ۰۷ کافی نیست.","24_BUSINESS_EXECUTORS"),
        new Requirement("RUN_SCOPED_CLEANUP_NOT_VERIFIED","CLEANUP","پاک‌سازی محدود به Run","فقط منابع ساخته‌شده با workspaceId/runId و تأیید مالکیت پاک شوند.","RUN_RESOURCE_OWNERSHIP"),
        new Requirement("EVIDENCE_STORE_NOT_VERIFIED","EVIDENCE","مخزن شواهد","فایل‌های شواهد باید روی دیسک، دارای SHA256 و قابل راستی‌آزمایی باشند.","ARTIFACT_SHA256"),
        new Requirement("DEVELOPMENT_SEED_MUST_BE_DISABLED","STARTUP","Seed ناخواسته","در development و production، DevSeedRunner اجرا نشود؛ مقادیر متناقض باعث خطای راه‌اندازی شوند.","STARTUP_PROFILE_GUARD"),
        new Requirement("EMBEDDED_SOURCE_COMMIT_UNVERIFIED","BUILD","هویت کد اجراشده","Build تمیز پس از Commit باید SHA چهل‌رقمی واقعی را در build-info قرار دهد.","REPRODUCIBLE_BUILD_INFO")
    );
    public static List<Requirement> all() { return REQUIREMENTS; }
    public static List<Map<String,Object>> report(List<String> blockers) {
        Set<String> active=new LinkedHashSet<>(blockers==null?List.of():blockers);
        List<Map<String,Object>> result=new ArrayList<>();
        for(Requirement item:REQUIREMENTS) result.add(item.describe(active.contains(item.code())));
        for(String unknown:active) if(REQUIREMENTS.stream().noneMatch(item->item.code().equals(unknown))) {
            Map<String,Object> row=new LinkedHashMap<>();
            row.put("code",unknown);row.put("group","UNKNOWN");row.put("state","BLOCKED");
            row.put("titleFa","مانع ناشناخته");row.put("remediationFa","در سورس بررسی شود");row.put("evidenceType","UNCLASSIFIED");result.add(row);
        }
        return List.copyOf(result);
    }
}
