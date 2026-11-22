package com.sakhtyar.quality;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Pure mocked JDBC contract; never connects to the live shared database. */
class Phase02RestrictedIsolationProbeContractTest {
    @Test void rowSecurityOffRejectsEvenCorrectLookingPhysicalPrincipal() throws Exception {
        UUID own=UUID.randomUUID(),other=UUID.randomUUID();
        String role="sakhtyar_tws_"+own.toString().replace("-","");
        Connection c=mock(Connection.class);
        when(c.getAutoCommit()).thenReturn(true);
        when(c.isReadOnly()).thenReturn(false);
        when(c.prepareStatement(anyString())).thenAnswer(inv -> {
            String sql=inv.getArgument(0,String.class);
            PreparedStatement p=mock(PreparedStatement.class);
            ResultSet r=mock(ResultSet.class);
            when(p.executeQuery()).thenReturn(r);
            when(r.next()).thenReturn(true,false);
            if(sql.equals("select current_user") || sql.equals("select session_user"))
                when(r.getString(1)).thenReturn(role);
            else if(sql.equals("show row_security"))
                when(r.getString(1)).thenReturn("off");
            return p;
        });
        assertThrows(SecurityException.class,()->Phase02RestrictedIsolationProbe.inspect(c,own,other,true));
        verify(c).setReadOnly(true);
        verify(c).rollback();
        verify(c).setAutoCommit(true);
        verify(c).setReadOnly(false);
    }
}
