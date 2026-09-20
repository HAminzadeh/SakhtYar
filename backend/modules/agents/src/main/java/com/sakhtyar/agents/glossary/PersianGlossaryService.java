package com.sakhtyar.agents.glossary;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class PersianGlossaryService {

    private final Map<String, String> terms;

    public PersianGlossaryService() {
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
        values.put("پیشروی", "میزان مجاز توسعه سطح بنا در عمق یا بخش مشخصی از زمین");
        values.put("مشاعات", "فضاهای مشترک ساختمان مانند راه‌پله، آسانسور و راهروها");
        this.terms = Collections.unmodifiableMap(values);
    }

    public Map<String, String> recognizedTerms(String text) {
        LinkedHashMap<String, String> found = new LinkedHashMap<>();
        if (text == null || text.isBlank()) {
            return found;
        }
        terms.forEach((term, meaning) -> {
            if (text.contains(term)) {
                found.put(term, meaning);
            }
        });
        return found;
    }

    public Map<String, String> all() {
        return terms;
    }
}
