from sakhtyar_knowledge.text import normalize, extraction_artifact_count

def test_pdf_artifacts():
    raw = "کمیسژیون مژاده پنج شژهر تهران، توسط شهرداری الزم االجرا خواهد بود."
    clean, changed = normalize(raw)
    assert changed
    assert "کمیسیون" in clean
    assert "ماده" in clean
    assert "شهر" in clean
    assert "لازم" in clean
    assert "الاجرا" in clean

def test_real_zhe_words_survive():
    clean, _ = normalize("این پروژه ویژه دارای انرژی مناسب است.")
    assert "پروژه" in clean
    assert "ویژه" in clean
    assert "انرژی" in clean

def test_observed_samples():
    raw = "صژدور پروانژه منو ط بژه تأمین پارکینگ و تصژویب کمیسژیون است"
    clean, _ = normalize(raw)
    assert "صدور" in clean
    assert "منوط" in clean
    assert "به" in clean
    assert "تصویب" in clean
    assert "کمیسیون" in clean
