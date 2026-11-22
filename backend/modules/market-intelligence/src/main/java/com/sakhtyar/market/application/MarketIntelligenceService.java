package com.sakhtyar.market.application;

import static com.sakhtyar.market.api.MarketDtos.*;

import com.sakhtyar.audit.application.AuditService;
import tools.jackson.databind.ObjectMapper;
import java.math.*;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MarketIntelligenceService {
    private final JdbcTemplate jdbc;
    private final AuditService audit;
    private final ObjectMapper mapper;

    public MarketIntelligenceService(JdbcTemplate jdbc, AuditService audit, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.audit = audit;
        this.mapper = mapper;
    }

    @Transactional
    public ObservationResponse ingest(ObservationRequest r, Authentication auth) {
        BigDecimal pricePerM2 = r.pricePerM2();
        if (pricePerM2 == null && r.totalPrice() != null && r.areaM2() != null && r.areaM2().signum() > 0) {
            pricePerM2 = r.totalPrice().divide(r.areaM2(), 2, RoundingMode.HALF_UP);
        }
        if (pricePerM2 == null || pricePerM2.signum() < 0) {
            throw new IllegalArgumentException("pricePerM2 or totalPrice is required.");
        }

        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        String currency = r.currencyCode().trim().toUpperCase(Locale.ROOT);
        String country = clean(r.countryCode()) == null ? "IR" : r.countryCode().trim().toUpperCase(Locale.ROOT);

        jdbc.update("""
            insert into market_observation(
              id,source_code,external_id,observed_at,country_code,province,city,district,neighborhood,
              property_type,construction_year,area_m2,total_price,price_per_m2,currency_code,listing_type,
              latitude,longitude,source_url,confidence,raw_payload,created_at
            ) values (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?::jsonb,?)
            on conflict (source_code,external_id,observed_at) where external_id is not null
            do update set
              total_price=excluded.total_price,price_per_m2=excluded.price_per_m2,
              confidence=excluded.confidence,raw_payload=excluded.raw_payload
            """,
            id, r.sourceCode().trim(), clean(r.externalId()), java.sql.Timestamp.from(r.observedAt()), country, clean(r.province()),
            r.city().trim(), clean(r.district()), clean(r.neighborhood()), clean(r.propertyType()),
            r.constructionYear(), r.areaM2(), r.totalPrice(), pricePerM2, currency, clean(r.listingType()),
            r.latitude(), r.longitude(), clean(r.sourceUrl()), r.confidence(),
            json(r.rawPayload()), java.sql.Timestamp.from(now)
        );

        ObservationResponse response = findObservation(
                r.sourceCode().trim(), clean(r.externalId()), r.observedAt()
        ).orElse(new ObservationResponse(
                id,r.sourceCode().trim(),clean(r.externalId()),r.observedAt(),country,clean(r.province()),
                r.city().trim(),clean(r.district()),clean(r.neighborhood()),clean(r.propertyType()),
                r.constructionYear(),r.areaM2(),r.totalPrice(),pricePerM2,currency,clean(r.listingType()),
                r.latitude(),r.longitude(),clean(r.sourceUrl()),r.confidence(),now
        ));

        audit.record("MARKET_OBSERVATION", response.id(), "MARKET_OBSERVATION_INGESTED",
                Map.of("sourceCode", response.sourceCode(), "city", response.city(),
                        "actor", auth == null ? "system" : auth.getName()));
        return response;
    }

    @Transactional(readOnly = true)
    public MarketSummary summary(String city,String district,String neighborhood,String propertyType,int days) {
        if (days < 1 || days > 3650) throw new IllegalArgumentException("periodDays must be between 1 and 3650.");
        Instant end = Instant.now();
        Instant start = end.minus(Duration.ofDays(days));
        Instant previousStart = start.minus(Duration.ofDays(days));

        Aggregate current = aggregate(city,district,neighborhood,propertyType,start,end);
        Aggregate previous = aggregate(city,district,neighborhood,propertyType,previousStart,start);

        BigDecimal change = null;
        if (current.median != null && previous.median != null && previous.median.signum() != 0) {
            change = current.median.subtract(previous.median)
                    .divide(previous.median, 6, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100))
                    .setScale(2, RoundingMode.HALF_UP);
        }

        String freshness = "NO_DATA";
        if (current.latest != null) {
            long ageHours = Duration.between(current.latest, Instant.now()).toHours();
            freshness = ageHours <= 24 ? "FRESH" : ageHours <= 168 ? "STALE" : "OLD";
        }

        return new MarketSummary(
                city,clean(district),clean(neighborhood),clean(propertyType),days,current.count,
                current.average,current.median,current.min,current.max,change,current.latest,
                current.currency,freshness
        );
    }

    @Transactional(readOnly = true)
    public List<TrendPoint> trend(String city,String district,String neighborhood,String propertyType,int days) {
        if (days < 1 || days > 3650) throw new IllegalArgumentException("periodDays must be between 1 and 3650.");
        StringBuilder sql = new StringBuilder("""
            select observed_at::date as day,count(*) as sample_count,
                   avg(price_per_m2)::numeric as avg_price,
                   percentile_cont(0.5) within group(order by price_per_m2)::numeric as median_price
              from market_observation
             where observed_at>=? and city=?
            """);
        List<Object> args = new ArrayList<>();
        args.add(java.sql.Timestamp.from(Instant.now().minus(Duration.ofDays(days))));
        args.add(city);
        appendFilters(sql,args,district,neighborhood,propertyType);
        sql.append(" group by observed_at::date order by day");

        return jdbc.query(sql.toString(), (rs,row) -> new TrendPoint(
                rs.getObject("day", LocalDate.class),
                rs.getLong("sample_count"),
                decimal(rs.getObject("avg_price")),
                decimal(rs.getObject("median_price"))
        ), args.toArray());
    }

    @Transactional(readOnly = true)
    public List<ComparableResponse> comparables(
            String city,String district,String neighborhood,String propertyType,BigDecimal areaM2,int limit
    ) {
        int safeLimit = Math.max(1, Math.min(limit, 100));
        StringBuilder sql = new StringBuilder("""
            select id,source_code,external_id,observed_at,city,district,neighborhood,property_type,
                   construction_year,area_m2,total_price,price_per_m2,currency_code,source_url,confidence
              from market_observation where city=?
            """);
        List<Object> args = new ArrayList<>();
        args.add(city);
        appendFilters(sql,args,district,neighborhood,propertyType);
        if (areaM2 != null && areaM2.signum() > 0) {
            sql.append(" and area_m2 between ? and ? ");
            args.add(areaM2.multiply(new BigDecimal("0.70")));
            args.add(areaM2.multiply(new BigDecimal("1.30")));
            sql.append(" order by abs(area_m2-?) asc, observed_at desc ");
            args.add(areaM2);
        } else {
            sql.append(" order by observed_at desc ");
        }
        sql.append(" limit ").append(safeLimit);

        return jdbc.query(sql.toString(), (rs,row) -> comparable(rs), args.toArray());
    }

    @Transactional
    public SnapshotResponse publishSnapshot(
            String city,String district,String neighborhood,String propertyType,int days,Authentication auth
    ) {
        MarketSummary s = summary(city,district,neighborhood,propertyType,days);
        if (s.sampleCount() < 1 || s.medianPricePerM2() == null) {
            throw new IllegalStateException("No market data available for snapshot.");
        }
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        jdbc.update("""
            insert into market_snapshot(
              id,city,district,neighborhood,property_type,period_days,sample_count,median_price_per_m2,
              average_price_per_m2,change_percent,currency_code,source_observed_at,generated_at,generated_by
            ) values (?,?,?,?,?,?,?,?,?,?,?,?,?,?)
            """,
            id,s.city(),s.district(),s.neighborhood(),s.propertyType(),s.periodDays(),s.sampleCount(),
            s.medianPricePerM2(),s.averagePricePerM2(),s.changePercent(),s.currencyCode(),
            (s.latestObservedAt()==null?null:java.sql.Timestamp.from(s.latestObservedAt())),java.sql.Timestamp.from(now),auth == null ? "system" : auth.getName()
        );
        audit.record("MARKET_SNAPSHOT", id, "MARKET_SNAPSHOT_PUBLISHED",
                Map.of("city",city,"periodDays",days,"sampleCount",s.sampleCount()));
        return new SnapshotResponse(
                id,s.city(),s.district(),s.neighborhood(),s.propertyType(),s.periodDays(),s.sampleCount(),
                s.medianPricePerM2(),s.averagePricePerM2(),s.changePercent(),s.currencyCode(),
                s.latestObservedAt(),now
        );
    }

    @Transactional(readOnly = true)
    public Optional<SnapshotResponse> latestSnapshot(
            String city,String district,String neighborhood,String propertyType,int days
    ) {
        StringBuilder sql = new StringBuilder("""
            select id,city,district,neighborhood,property_type,period_days,sample_count,median_price_per_m2,
                   average_price_per_m2,change_percent,currency_code,source_observed_at,generated_at
              from market_snapshot where city=? and period_days=?
            """);
        List<Object> args = new ArrayList<>();
        args.add(city); args.add(days);
        nullableEquals(sql,args,"district",district);
        nullableEquals(sql,args,"neighborhood",neighborhood);
        nullableEquals(sql,args,"property_type",propertyType);
        sql.append(" order by generated_at desc limit 1");
        List<SnapshotResponse> rows = jdbc.query(sql.toString(), (rs,row)->new SnapshotResponse(
                rs.getObject("id",UUID.class),rs.getString("city"),rs.getString("district"),
                rs.getString("neighborhood"),rs.getString("property_type"),rs.getInt("period_days"),
                rs.getLong("sample_count"),rs.getBigDecimal("median_price_per_m2"),
                rs.getBigDecimal("average_price_per_m2"),rs.getBigDecimal("change_percent"),
                rs.getString("currency_code"),instant(rs,"source_observed_at"),instant(rs,"generated_at")
        ),args.toArray());
        return rows.stream().findFirst();
    }

    private Aggregate aggregate(
            String city,String district,String neighborhood,String propertyType,Instant start,Instant end
    ) {
        StringBuilder sql = new StringBuilder("""
            select count(*) sample_count,avg(price_per_m2)::numeric avg_price,
                   percentile_cont(0.5) within group(order by price_per_m2)::numeric median_price,
                   min(price_per_m2) min_price,max(price_per_m2) max_price,max(observed_at) latest_observed,
                   min(currency_code) currency_code
              from market_observation where observed_at>=? and observed_at<? and city=?
            """);
        List<Object> args = new ArrayList<>();
        args.add(java.sql.Timestamp.from(start)); args.add(java.sql.Timestamp.from(end)); args.add(city);
        appendFilters(sql,args,district,neighborhood,propertyType);

        return jdbc.queryForObject(sql.toString(), (rs,row)->new Aggregate(
                rs.getLong("sample_count"),decimal(rs.getObject("avg_price")),
                decimal(rs.getObject("median_price")),rs.getBigDecimal("min_price"),
                rs.getBigDecimal("max_price"),instant(rs,"latest_observed"),
                rs.getString("currency_code")
        ),args.toArray());
    }

    private void appendFilters(StringBuilder sql,List<Object> args,String district,String neighborhood,String propertyType) {
        if (clean(district) != null) { sql.append(" and district=?"); args.add(district.trim()); }
        if (clean(neighborhood) != null) { sql.append(" and neighborhood=?"); args.add(neighborhood.trim()); }
        if (clean(propertyType) != null) { sql.append(" and property_type=?"); args.add(propertyType.trim()); }
    }

    private void nullableEquals(StringBuilder sql,List<Object> args,String column,String value) {
        if (clean(value) == null) sql.append(" and ").append(column).append(" is null");
        else { sql.append(" and ").append(column).append("=?"); args.add(value.trim()); }
    }

    private Optional<ObservationResponse> findObservation(String sourceCode,String externalId,Instant observedAt) {
        if (externalId == null) return Optional.empty();
        List<ObservationResponse> rows = jdbc.query("""
            select id,source_code,external_id,observed_at,country_code,province,city,district,neighborhood,
                   property_type,construction_year,area_m2,total_price,price_per_m2,currency_code,listing_type,
                   latitude,longitude,source_url,confidence,created_at
              from market_observation where source_code=? and external_id=? and observed_at=? limit 1
            """,(rs,row)->observation(rs),sourceCode,externalId,java.sql.Timestamp.from(observedAt));
        return rows.stream().findFirst();
    }

    private ObservationResponse observation(ResultSet rs) throws SQLException {
        return new ObservationResponse(
                rs.getObject("id",UUID.class),rs.getString("source_code"),rs.getString("external_id"),
                instant(rs,"observed_at"),rs.getString("country_code"),rs.getString("province"),
                rs.getString("city"),rs.getString("district"),rs.getString("neighborhood"),
                rs.getString("property_type"),(Integer)rs.getObject("construction_year"),
                rs.getBigDecimal("area_m2"),rs.getBigDecimal("total_price"),rs.getBigDecimal("price_per_m2"),
                rs.getString("currency_code"),rs.getString("listing_type"),rs.getBigDecimal("latitude"),
                rs.getBigDecimal("longitude"),rs.getString("source_url"),rs.getBigDecimal("confidence"),
                instant(rs,"created_at")
        );
    }

    private ComparableResponse comparable(ResultSet rs) throws SQLException {
        return new ComparableResponse(
                rs.getObject("id",UUID.class),rs.getString("source_code"),rs.getString("external_id"),
                instant(rs,"observed_at"),rs.getString("city"),rs.getString("district"),
                rs.getString("neighborhood"),rs.getString("property_type"),
                (Integer)rs.getObject("construction_year"),rs.getBigDecimal("area_m2"),
                rs.getBigDecimal("total_price"),rs.getBigDecimal("price_per_m2"),
                rs.getString("currency_code"),rs.getString("source_url"),rs.getBigDecimal("confidence")
        );
    }

    private Instant instant(ResultSet rs,String column) throws SQLException {
        var ts=rs.getTimestamp(column);
        return ts==null?null:ts.toInstant();
    }

    private BigDecimal decimal(Object value) {
        if (value == null) return null;
        if (value instanceof BigDecimal b) return b;
        if (value instanceof Number n) return new BigDecimal(n.toString());
        return new BigDecimal(value.toString());
    }

    private String clean(String value) {
        if (value == null) return null;
        String v=value.trim();
        return v.isEmpty()?null:v;
    }

    private String json(Map<String,Object> payload) {
        try { return mapper.writeValueAsString(payload == null ? Map.of() : payload); }
        catch (Exception ex) { throw new IllegalArgumentException("rawPayload must be JSON serializable.", ex); }
    }

    private record Aggregate(
            long count,BigDecimal average,BigDecimal median,BigDecimal min,BigDecimal max,
            Instant latest,String currency
    ) {}
}
