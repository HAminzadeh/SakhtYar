package com.sakhtyar.globalization.application;

import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.function.Consumer;
import java.util.zip.ZipInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class GeoNamesImporter {
    private static final Logger log = LoggerFactory.getLogger(GeoNamesImporter.class);
    private static final String BASE = "https://download.geonames.org/export/dump/";
    private static final String MARKER = "MASTER-DATA-IMPORT";

    private final JdbcTemplate jdbc;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(20))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    private final boolean fullCities;

    public GeoNamesImporter(
            JdbcTemplate jdbc,
            @Value("${app.globalization.master-data.full-cities:true}") boolean fullCities
    ) {
        this.jdbc = jdbc;
        this.fullCities = fullCities;
    }

    public Map<String, Long> importAll() {
        return importAll(fullCities ? "full" : "quick", stage -> {});
    }

    public Map<String, Long> importAll(String mode, Consumer<String> stageListener) {
        boolean full = "full".equalsIgnoreCase(mode);
        String cityDataset = full ? "allCountries.zip" : "cities500.zip";

        log.info("{} event=START mode={} cityDataset={}", MARKER, full ? "full" : "quick", cityDataset);

        stageListener.accept("COUNTRIES");
        long countries = importCountries();

        stageListener.accept("DIVISIONS");
        long admin1 = importAdmin1();

        stageListener.accept("CITIES");
        long cities = importCities(cityDataset);

        stageListener.accept("COMPLETED");
        log.info(
                "{} event=COMPLETED mode={} countries={} divisions={} cities={}",
                MARKER,
                full ? "full" : "quick",
                countries,
                admin1,
                cities
        );

        return Map.of(
                "countries", countries,
                "admin1", admin1,
                "cities", cities,
                "currencies", 0L
        );
    }

    public long importCountries() {
        String url = BASE + "countryInfo.txt";
        UUID run = start("geonames-countryInfo", url);
        long count = 0;

        log.info("{} stage=COUNTRIES event=DOWNLOAD_START url={}", MARKER, url);

        try (
                InputStream in = download(url);
                BufferedReader br = new BufferedReader(
                        new InputStreamReader(in, StandardCharsets.UTF_8)
                )
        ) {
            String line;
            while ((line = br.readLine()) != null) {
                if (line.isBlank() || line.startsWith("#")) {
                    continue;
                }

                String[] p = line.split("\\t", -1);
                if (p.length < 17) {
                    continue;
                }

                String iso2 = p[0];
                String iso3 = p[1];
                String num = p[2];
                String name = p[4];
                String continent = p[8];
                String currency = p[10];
                long geonames = parseLong(p[16]);

                UUID id = stable("country:" + iso2);
                UUID currencyId = oneUuid(
                        "select id from global_currency where code=?",
                        currency
                );

                jdbc.update("""
                    insert into global_country(
                      id,iso_alpha2,iso_alpha3,iso_numeric,geonames_id,
                      name_en,name_native,continent_code,default_currency_id,active
                    )
                    values(?,?,?,?,?,?,?,?,?,true)
                    on conflict(iso_alpha2) do update set
                      iso_alpha3=excluded.iso_alpha3,
                      iso_numeric=excluded.iso_numeric,
                      geonames_id=excluded.geonames_id,
                      name_en=excluded.name_en,
                      continent_code=excluded.continent_code,
                      default_currency_id=excluded.default_currency_id,
                      active=true
                    """,
                    id,
                    iso2,
                    blank(iso3),
                    blank(num),
                    geonames,
                    name,
                    name,
                    blank(continent),
                    currencyId
                );

                if (currencyId != null) {
                    jdbc.update("""
                        insert into global_country_currency(
                          country_id,currency_id,primary_currency
                        )
                        values(?,?,true)
                        on conflict do nothing
                        """,
                        id,
                        currencyId
                    );
                }

                for (String raw : p[15].split(",")) {
                    String code = raw.split("-")[0].trim();
                    if (code.isBlank()) {
                        continue;
                    }

                    UUID languageId = oneUuid(
                            "select id from global_language where code=?",
                            code
                    );

                    if (languageId == null) {
                        languageId = stable("language:" + code);
                        jdbc.update("""
                            insert into global_language(
                              id,code,bcp47_tag,name_en,name_native,
                              direction,active,system_default
                            )
                            values(?,?,?,?,?,'LTR',false,false)
                            on conflict(code) do nothing
                            """,
                            languageId,
                            code,
                            code,
                            code,
                            code
                        );
                        languageId = oneUuid(
                                "select id from global_language where code=?",
                                code
                        );
                    }

                    if (languageId != null) {
                        jdbc.update("""
                            insert into global_country_language(
                              country_id,language_id,primary_language
                            )
                            values(?,?,false)
                            on conflict do nothing
                            """,
                            id,
                            languageId
                        );
                    }
                }

                count++;
            }

            finish(run, count, null);
            log.info("{} stage=COUNTRIES event=COMPLETED records={}", MARKER, count);
            return count;
        } catch (Exception ex) {
            finish(run, count, rootMessage(ex));
            log.error(
                    "{} stage=COUNTRIES event=FAILED records={} error={}",
                    MARKER,
                    count,
                    rootMessage(ex),
                    ex
            );
            throw new IllegalStateException("GeoNames country import failed.", ex);
        }
    }

    public long importAdmin1() {
        String url = BASE + "admin1CodesASCII.txt";
        UUID run = start("geonames-admin1", url);
        long count = 0;

        log.info("{} stage=DIVISIONS event=DOWNLOAD_START url={}", MARKER, url);

        try (
                InputStream in = download(url);
                BufferedReader br = new BufferedReader(
                        new InputStreamReader(in, StandardCharsets.UTF_8)
                )
        ) {
            String line;

            while ((line = br.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }

                String[] p = line.split("\\t", -1);
                if (p.length < 4) {
                    continue;
                }

                String[] key = p[0].split("\\.", 2);
                if (key.length != 2) {
                    continue;
                }

                UUID country = oneUuid(
                        "select id from global_country where iso_alpha2=?",
                        key[0]
                );
                if (country == null) {
                    continue;
                }

                UUID id = stable("admin1:" + p[0]);
                jdbc.update("""
                    insert into global_administrative_division(
                      id,country_id,code,division_type,name_en,name_native,
                      level,geonames_id,active
                    )
                    values(?,?,?,'ADMIN1',?,?,1,?,true)
                    on conflict(country_id,code) do update set
                      name_en=excluded.name_en,
                      name_native=excluded.name_native,
                      geonames_id=excluded.geonames_id,
                      active=true
                    """,
                    id,
                    country,
                    key[1],
                    p[2].isBlank() ? p[1] : p[2],
                    p[1],
                    parseLong(p[3])
                );

                count++;
            }

            finish(run, count, null);
            log.info("{} stage=DIVISIONS event=COMPLETED records={}", MARKER, count);
            return count;
        } catch (Exception ex) {
            finish(run, count, rootMessage(ex));
            log.error(
                    "{} stage=DIVISIONS event=FAILED records={} error={}",
                    MARKER,
                    count,
                    rootMessage(ex),
                    ex
            );
            throw new IllegalStateException("GeoNames admin import failed.", ex);
        }
    }

    public long importCities(String dataset) {
        String url = BASE + dataset;
        UUID run = start("geonames-" + dataset, url);
        long count = 0;

        log.info("{} stage=CITIES event=DOWNLOAD_START dataset={} url={}", MARKER, dataset, url);

        try (
                InputStream raw = download(url);
                ZipInputStream zip = new ZipInputStream(raw, StandardCharsets.UTF_8)
        ) {
            if (zip.getNextEntry() == null) {
                throw new IOException("GeoNames ZIP is empty.");
            }

            BufferedReader br = new BufferedReader(
                    new InputStreamReader(zip, StandardCharsets.UTF_8)
            );
            List<Object[]> batch = new ArrayList<>(1000);
            String line;

            while ((line = br.readLine()) != null) {
                String[] p = line.split("\\t", -1);
                if (p.length < 19 || !"P".equals(p[6])) {
                    continue;
                }

                UUID country = oneUuid(
                        "select id from global_country where iso_alpha2=?",
                        p[8]
                );
                if (country == null) {
                    continue;
                }

                UUID admin = p[10].isBlank()
                        ? null
                        : oneUuid(
                                """
                                select id
                                from global_administrative_division
                                where country_id=? and code=?
                                """,
                                country,
                                p[10]
                        );

                long gid = parseLong(p[0]);

                batch.add(new Object[]{
                        stable("city:" + gid),
                        country,
                        admin,
                        gid,
                        p[1],
                        p[2],
                        decimal(p[4]),
                        decimal(p[5]),
                        blank(p[17]),
                        parseLongNullable(p[14]),
                        blank(p[7])
                });

                if (batch.size() >= 1000) {
                    flushCities(batch);
                    count += batch.size();
                    batch.clear();

                    if (count % 25000 == 0) {
                        log.info(
                                "{} stage=CITIES event=PROGRESS dataset={} records={}",
                                MARKER,
                                dataset,
                                count
                        );
                    }
                }
            }

            if (!batch.isEmpty()) {
                flushCities(batch);
                count += batch.size();
            }

            finish(run, count, null);
            log.info(
                    "{} stage=CITIES event=COMPLETED dataset={} records={}",
                    MARKER,
                    dataset,
                    count
            );
            return count;
        } catch (Exception ex) {
            finish(run, count, rootMessage(ex));
            log.error(
                    "{} stage=CITIES event=FAILED dataset={} records={} error={}",
                    MARKER,
                    dataset,
                    count,
                    rootMessage(ex),
                    ex
            );
            throw new IllegalStateException("GeoNames city import failed.", ex);
        }
    }

    private void flushCities(List<Object[]> batch) {
        jdbc.batchUpdate("""
            insert into global_city(
              id,country_id,administrative_division_id,geonames_id,
              name,ascii_name,latitude,longitude,timezone,population,
              feature_code,active
            )
            values(?,?,?,?,?,?,?,?,?,?,?,true)
            on conflict(geonames_id) do update set
              country_id=excluded.country_id,
              administrative_division_id=excluded.administrative_division_id,
              name=excluded.name,
              ascii_name=excluded.ascii_name,
              latitude=excluded.latitude,
              longitude=excluded.longitude,
              timezone=excluded.timezone,
              population=excluded.population,
              feature_code=excluded.feature_code,
              active=true
            """,
            batch
        );
    }

    private InputStream download(String url) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofMinutes(10))
                .header("User-Agent", "SakhtYar/0.2 master-data importer")
                .GET()
                .build();

        HttpResponse<InputStream> response = http.send(
                request,
                HttpResponse.BodyHandlers.ofInputStream()
        );

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException(
                    "HTTP " + response.statusCode() + " for " + url
            );
        }

        return response.body();
    }

    private UUID start(String dataset, String url) {
        UUID id = UUID.randomUUID();

        // PostgreSQL owns the timestamp. This avoids JdbcTemplate/Instant binding ambiguity.
        jdbc.update("""
            insert into global_master_data_import(
              id,dataset,source_url,status,record_count,started_at
            )
            values(?,?,?,'RUNNING',0,now())
            """,
            id,
            dataset,
            url
        );

        return id;
    }

    private void finish(UUID id, long count, String error) {
        jdbc.update("""
            update global_master_data_import
               set status=?,
                   record_count=?,
                   finished_at=now(),
                   error_message=?
             where id=?
            """,
            error == null ? "COMPLETED" : "FAILED",
            count,
            error,
            id
        );
    }

    private UUID oneUuid(String sql, Object... args) {
        List<UUID> rows = jdbc.query(
                sql,
                (rs, n) -> rs.getObject(1, UUID.class),
                args
        );
        return rows.isEmpty() ? null : rows.getFirst();
    }

    private static String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        String message = current.getMessage();
        return message == null || message.isBlank()
                ? current.getClass().getSimpleName()
                : message;
    }

    private static UUID stable(String value) {
        return UUID.nameUUIDFromBytes(
                value.getBytes(StandardCharsets.UTF_8)
        );
    }

    private static String blank(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static long parseLong(String value) {
        try {
            return Long.parseLong(value.trim());
        } catch (Exception ex) {
            return 0L;
        }
    }

    private static Long parseLongNullable(String value) {
        try {
            return value == null || value.isBlank()
                    ? null
                    : Long.valueOf(value.trim());
        } catch (Exception ex) {
            return null;
        }
    }

    private static java.math.BigDecimal decimal(String value) {
        try {
            return new java.math.BigDecimal(value);
        } catch (Exception ex) {
            return null;
        }
    }
}