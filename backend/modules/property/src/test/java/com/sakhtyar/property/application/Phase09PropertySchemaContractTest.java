package com.sakhtyar.property.application;

import static org.junit.jupiter.api.Assertions.*;
import com.sakhtyar.property.domain.PropertyEntity;
import com.sakhtyar.property.domain.PropertyRepository;
import jakarta.persistence.Column;
import jakarta.persistence.Table;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Structural guards; real FK and RLS enforcement still require PostgreSQL integration. */
class Phase09PropertySchemaContractTest {
    @Test void onePropertyPerCaseRemainsMappedAndRepositoryExposesCaseLookup() throws Exception {
        assertEquals("property", PropertyEntity.class.getAnnotation(Table.class).name());
        Column column = PropertyEntity.class.getDeclaredField("caseId").getAnnotation(Column.class);
        assertTrue(column.unique());
        assertFalse(column.nullable());
        assertNotNull(PropertyRepository.class.getMethod("findByCaseId", UUID.class));
        assertNotNull(PropertyRepository.class.getMethod("existsByCaseId", UUID.class));
    }
    @Test void realTestScopeIsNotClientWritableThroughJpa() throws Exception {
        Column scope = PropertyEntity.class.getDeclaredField("dataScope").getAnnotation(Column.class);
        assertFalse(scope.insertable());
        assertFalse(scope.updatable());
    }
}
