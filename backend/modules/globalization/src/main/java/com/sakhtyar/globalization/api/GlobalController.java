package com.sakhtyar.globalization.api;

import static com.sakhtyar.globalization.domain.GlobalDtos.*;
import com.sakhtyar.globalization.application.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/global")
public class GlobalController {
    private final CatalogService catalog;
    private final UserPreferenceService prefs;
    private final JurisdictionService jurisdiction;
    private final GeoNamesImporter importer;
    private final FxService fx;

    public GlobalController(CatalogService catalog,UserPreferenceService prefs,
        JurisdictionService jurisdiction,GeoNamesImporter importer,FxService fx){
        this.catalog=catalog;this.prefs=prefs;this.jurisdiction=jurisdiction;this.importer=importer;this.fx=fx;
    }

    @GetMapping("/catalog/languages") public List<LanguageItem> languages(){return catalog.languages();}
    @GetMapping("/catalog/currencies") public List<CurrencyItem> currencies(){return catalog.currencies();}
    @GetMapping("/catalog/countries") public List<CountryItem> countries(@RequestParam(required=false) String q){return catalog.countries(q);}
    @GetMapping("/catalog/countries/{id}/divisions")
    public List<DivisionItem> divisions(@PathVariable UUID id,@RequestParam(required=false) UUID parentId){return catalog.divisions(id,parentId);}
    @GetMapping("/catalog/countries/{id}/cities")
    public List<CityItem> cities(@PathVariable UUID id,@RequestParam(required=false) UUID divisionId,@RequestParam(required=false) String q){
        return catalog.cities(id,divisionId,q);
    }

    @GetMapping("/preferences/me") public Preference preference(Authentication a){return prefs.get(a);}
    @PutMapping("/preferences/me") public Preference preference(Authentication a,@RequestBody PreferenceUpdate r){return prefs.update(a,r);}

    @GetMapping("/cases/{caseId}/jurisdiction") public JurisdictionContext jurisdiction(@PathVariable UUID caseId){
        return jurisdiction.forCase(caseId);
    }
    @PutMapping("/cases/{caseId}/jurisdiction")
    public JurisdictionContext jurisdiction(@PathVariable UUID caseId,@RequestBody LocationRequest r){
        return jurisdiction.setCaseLocation(caseId,r.countryId(),r.divisionId(),r.cityId());
    }

    @GetMapping("/cases/{caseId}/crawler-sources")
    public List<UUID> crawlerSources(@PathVariable UUID caseId){return jurisdiction.matchingCrawlerSources(caseId);}

    @PostMapping("/master-data/import/geonames")
    public Map<String,Long> importGeoNames(){return importer.importAll();}

    @PostMapping("/fx-rates")
    public Map<String,UUID> addFx(@RequestBody FxRequest r){return Map.of("id",fx.add(r));}

    @GetMapping("/fx/convert")
    public Map<String,Object> convert(@RequestParam UUID baseCurrencyId,@RequestParam UUID quoteCurrencyId,
        @RequestParam BigDecimal amount,@RequestParam(required=false) Instant at){
        return Map.of("amount",amount,"converted",fx.convert(baseCurrencyId,quoteCurrencyId,amount,at));
    }

    public record LocationRequest(UUID countryId,UUID divisionId,UUID cityId){}
}