package com.sakhtyar.material.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.util.*;

/** Deterministic catalog and numeric boundary rules; no market or FX data is invented. */
public final class MaterialFoundation {
    private MaterialFoundation() {}
    public record Unit(String code, String dimension, BigDecimal factor) {}
    private static final Map<String, Unit> UNITS;
    static {
        Map<String, Unit> units = new LinkedHashMap<>();
        add(units,"MASS","KG","1","G","0.001","T","1000","TON","1000");
        add(units,"LENGTH","M","1","CM","0.01","MM","0.001","KM","1000");
        add(units,"AREA","M2","1","CM2","0.0001","MM2","0.000001");
        add(units,"VOLUME","M3","1","L","0.001","ML","0.000001","CM3","0.000001");
        add(units,"COUNT","EA","1","PCS","1","PC","1","PIECE","1","UNIT","1","NO","1");
        for (String code : List.of("BAG","PACK","BOX","ROLL","SET","PAIR","SHEET","BUNDLE","LOT","TRIP","DAY","HOUR","MONTH"))
            add(units,code,code,"1"); // no assumed pack weight or time conversion
        UNITS = Collections.unmodifiableMap(units);
    }
    private static void add(Map<String,Unit> map,String dimension,String... pairs) {
        for(int i=0;i<pairs.length;i+=2) map.put(pairs[i],new Unit(pairs[i],dimension,new BigDecimal(pairs[i+1])));
    }
    public static List<Unit> units(){ return List.copyOf(UNITS.values()); }
    public static String unitCode(String value) {
        String s=required(value,40).toUpperCase(Locale.ROOT);
        if(!UNITS.containsKey(s)) throw new IllegalArgumentException("Unsupported material unit.");
        return s;
    }
    /** Catalog unit is immutable: changing the code can reinterpret existing prices or BOMs. */
    public static String unchangedUnit(String existing,String requested) {
        if(existing==null || requested==null || !existing.equalsIgnoreCase(requested.trim()))
            throw new IllegalArgumentException("Material base unit is immutable; create a new catalog item.");
        return existing;
    }
    public static BigDecimal convert(BigDecimal quantity,String from,String to) {
        if(quantity==null || quantity.signum()<=0 || quantity.precision()>34 || Math.abs((long)quantity.scale())>34)
            throw new IllegalArgumentException("Invalid conversion quantity.");
        Unit a=UNITS.get(unitCode(from)), b=UNITS.get(unitCode(to));
        if(!a.dimension().equals(b.dimension())) throw new IllegalArgumentException("Incompatible material units.");
        return quantity.multiply(a.factor()).divide(b.factor()); // exact decimal factors; no premature rounding
    }
    public static BigDecimal amount(BigDecimal value){return decimal(value,18,2,true);}
    public static BigDecimal quantity(BigDecimal value){return decimal(value,14,6,true);}
    public static BigDecimal confidence(BigDecimal value){
        if(value==null)return null;
        if(value.signum()<0 || value.compareTo(BigDecimal.ONE)>0)throw new IllegalArgumentException("Invalid confidence.");
        return decimal(value,1,4,false);
    }
    private static BigDecimal decimal(BigDecimal value,int integerDigits,int scale,boolean positive){
        if(value==null || (positive && value.signum()<=0))throw new IllegalArgumentException("Invalid material decimal.");
        BigDecimal stripped=value.stripTrailingZeros();
        if((long)stripped.precision()-stripped.scale()>integerDigits || stripped.scale()>scale)
            throw new IllegalArgumentException("Material decimal exceeds storage precision.");
        return stripped.setScale(scale,RoundingMode.UNNECESSARY);
    }
    /** The active global_currency table and existing V26 trigger remain authoritative. */
    public static String currencyCode(String value){
        String s=required(value,8).toUpperCase(Locale.ROOT);
        if(!s.matches("[A-Z][A-Z0-9]{2,7}"))throw new IllegalArgumentException("Invalid currency code.");
        return s;
    }
    public static String code(String value){
        String s=required(value,120).toUpperCase(Locale.ROOT);
        if(s.length()>120 || !s.matches("[A-Z0-9][A-Z0-9._-]*"))throw new IllegalArgumentException("Invalid material code.");
        return s;
    }
    public static String required(String value,int max){
        if(value==null || value.codePoints().anyMatch(Character::isISOControl))throw new IllegalArgumentException("Invalid material text.");
        String s=Normalizer.normalize(value.strip(),Normalizer.Form.NFC).replace('\u064a','\u06cc').replace('\u0643','\u06a9');
        s=s.replaceAll("[\\p{Z} ]+"," ").strip();
        if(s.isBlank() || s.length()>max)throw new IllegalArgumentException("Invalid material text length.");
        return s;
    }
    public static String optional(String value,int max){return value==null || value.isBlank()?null:required(value,max);}
    public static String country(String value){
        String s=optional(value,2);
        if(s==null)return null;
        s=s.toUpperCase(Locale.ROOT);
        if(!Arrays.asList(Locale.getISOCountries()).contains(s))throw new IllegalArgumentException("Invalid country code.");
        return s;
    }
    public static String search(String value){
        if(value==null || value.isBlank())return "";
        return Normalizer.normalize(value,Normalizer.Form.NFKC).replace('\u064a','\u06cc').replace('\u0643','\u06a9')
            .replaceAll("[\\p{Z}\\s\\u200c]+", "").toLowerCase(Locale.ROOT);
    }
}
