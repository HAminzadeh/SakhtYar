package com.sakhtyar.globalization;

import com.sakhtyar.globalization.application.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class Phase16MasterDataSafetyTest {
    @AfterEach void clear(){SecurityContextHolder.clearContext();}
    private void login(String authority){
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
            "actor",null,List.of(new SimpleGrantedAuthority(authority))));
    }
    @Test void startupNeverCallsRemoteImporterEvenWhenLegacyAutoImportTrue(){
        GeoNamesImporter importer=mock(GeoNamesImporter.class);
        new MasterDataStartup(importer,true).ready();
        verifyNoInteractions(importer);
    }
    @Test void offlineModeRefusesBeforeJdbcOrNetwork(){
        login("USER_MANAGE");
        JdbcTemplate jdbc=mock(JdbcTemplate.class);
        GeoNamesImporter importer=new GeoNamesImporter(jdbc,true,false);
        assertThrows(SecurityException.class,importer::importAll);
        assertThrows(SecurityException.class,()->importer.importCities("allCountries.zip"));
        verifyNoInteractions(jdbc);
    }
    @Test void testerCannotStartNetworkEvenWhenExplicitlyEnabled(){
        login("ROLE_TESTER");
        JdbcTemplate jdbc=mock(JdbcTemplate.class);
        GeoNamesImporter importer=new GeoNamesImporter(jdbc,false,true);
        assertThrows(RuntimeException.class,importer::importAll);
        verifyNoInteractions(jdbc);
    }
}
