package com.sakhtyar.quality;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;

class T10DatasourceIdentityProbeTest {
    @Test void readsIdentityFromPhysicalDatasource() throws Exception {
        DataSource ds = mock(DataSource.class);
        Connection c = mock(Connection.class);
        PreparedStatement ps = mock(PreparedStatement.class);
        ResultSet rs = mock(ResultSet.class);
        when(ds.getConnection()).thenReturn(c);
        when(c.prepareStatement(anyString())).thenReturn(ps);
        when(ps.executeQuery()).thenReturn(rs);
        when(rs.next()).thenReturn(true,false);
        when(rs.getString(1)).thenReturn("sakhtyar_runtime");
        when(rs.getString(2)).thenReturn("sakhtyar_runtime");
        when(rs.getBoolean(7)).thenReturn(true);
        var result = T10DatasourceIdentityProbe.inspect(ds);
        assertTrue(result.restricted());
        assertEquals("sakhtyar_runtime", result.currentUser());
        verify(c).close();
        verify(ps).close();
        verify(rs).close();
    }
    @Test void superuserIsNotRestricted() {
        var role = new T10DatasourceIdentityProbe.Identity("a","a",true,false,false,false,true,0);
        assertFalse(role.restricted());
    }
    @Test void createRoleIsNotRestricted() {
        var role = new T10DatasourceIdentityProbe.Identity("a","a",false,false,true,false,true,0);
        assertFalse(role.restricted());
    }
    @Test void equalDatabasePrincipalsAreNotSeparate() {
        var a = new T10DatasourceIdentityProbe.Identity("same","same",false,false,false,false,true,0);
        assertFalse(T10DatasourceIdentityProbe.distinctPrincipal(a,a));
    }
    @Test void differentDatabasePrincipalsAreSeparate() {
        var a = new T10DatasourceIdentityProbe.Identity("runtime","runtime",false,false,false,false,true,0);
        var b = new T10DatasourceIdentityProbe.Identity("admin","admin",true,true,true,true,true,0);
        assertTrue(T10DatasourceIdentityProbe.distinctPrincipal(a,b));
    }
}
