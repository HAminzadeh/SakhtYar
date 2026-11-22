package com.sakhtyar.quality;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import java.lang.reflect.Modifier;
import org.junit.jupiter.api.Test;
import org.springframework.aop.MethodBeforeAdvice;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.aop.support.AopUtils;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** Prevents the production startup regression: a final @Transactional controller cannot be CGLIB proxied. */
class Phase02SharedDatabaseAuditCglibTest {

    @Test
    void transactionalControllerCanBeSubclassProxied() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        Phase02SharedDatabaseAuditController target = new Phase02SharedDatabaseAuditController(jdbc);
        ProxyFactory factory = new ProxyFactory(target);
        factory.setProxyTargetClass(true);
        factory.addAdvice((MethodBeforeAdvice) (method, args, bean) -> { });

        Object proxy = factory.getProxy();
        assertTrue(AopUtils.isCglibProxy(proxy));
        Phase02SharedDatabaseAuditController controller =
                assertInstanceOf(Phase02SharedDatabaseAuditController.class, proxy);
        ResponseStatusException denied = assertThrows(ResponseStatusException.class,
                () -> controller.inspect(null));
        assertEquals(HttpStatus.FORBIDDEN, denied.getStatusCode());
        verifyNoInteractions(jdbc);
    }

    @Test
    void readOnlyContractAndEndpointArePreserved() throws NoSuchMethodException {
        Class<Phase02SharedDatabaseAuditController> type = Phase02SharedDatabaseAuditController.class;
        assertFalse(Modifier.isFinal(type.getModifiers()));
        assertTrue(type.isAnnotationPresent(RestController.class));
        var inspect = type.getMethod("inspect", org.springframework.security.core.Authentication.class);
        Transactional transaction = inspect.getAnnotation(Transactional.class);
        assertTrue(transaction != null && transaction.readOnly());
        GetMapping route = inspect.getAnnotation(GetMapping.class);
        assertTrue(route != null);
        assertEquals("/api/v1/admin/quality/database-isolation", route.value()[0]);
    }
}
