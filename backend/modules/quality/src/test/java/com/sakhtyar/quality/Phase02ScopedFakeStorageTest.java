package com.sakhtyar.quality;

import static org.junit.jupiter.api.Assertions.*;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class Phase02ScopedFakeStorageTest {
    private static FixtureContext fixture(UUID w, UUID r) {
        return new FixtureContext(w,r,Phase02FixtureKeys.fixtureId(w,r,"E2E-14","root"),"E2E-14");
    }
    @Test void identicalCanaryIsIdempotentAndOwnedByOneRun() {
        var store = new Phase02ScopedFakeStorage();
        UUID w=UUID.randomUUID(), r=UUID.randomUUID();
        var a=fixture(w,r);
        byte[] bytes="synthetic-only".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        String minio=store.put(a,Phase02ScopedFakeStorage.Service.MINIO,"evidence/canary.txt",bytes,Duration.ofMinutes(5));
        assertTrue(minio.contains(w.toString()));
        assertTrue(minio.contains(r.toString()));
        assertEquals(minio,store.put(a,Phase02ScopedFakeStorage.Service.MINIO,"evidence/canary.txt",bytes,Duration.ofMinutes(5)));
        assertArrayEquals(bytes,store.read(a,Phase02ScopedFakeStorage.Service.MINIO,"evidence/canary.txt"));
        assertEquals(1,store.totalCanaryKeys());
    }
    @Test void foreignWorkspaceAndRunNeverSeeOrDeleteOwnedCanary() {
        var store = new Phase02ScopedFakeStorage();
        var owner=fixture(UUID.randomUUID(),UUID.randomUUID());
        var other=fixture(UUID.randomUUID(),UUID.randomUUID());
        store.put(owner,Phase02ScopedFakeStorage.Service.REDIS,"state",new byte[]{1},Duration.ofSeconds(30));
        assertNull(store.read(other,Phase02ScopedFakeStorage.Service.REDIS,"state"));
        assertFalse(store.delete(other,Phase02ScopedFakeStorage.Service.REDIS,"state"));
        assertNotNull(store.read(owner,Phase02ScopedFakeStorage.Service.REDIS,"state"));
        assertTrue(store.delete(owner,Phase02ScopedFakeStorage.Service.REDIS,"state"));
    }
    @Test void traversalMassCleanupAndOverwriteAreDenied() {
        var store=new Phase02ScopedFakeStorage();
        var owner=fixture(UUID.randomUUID(),UUID.randomUUID());
        assertThrows(IllegalArgumentException.class,()->store.put(owner,Phase02ScopedFakeStorage.Service.MINIO,
            "../REAL",new byte[]{1},Duration.ofSeconds(1)));
        assertThrows(IllegalArgumentException.class,()->store.delete(owner,Phase02ScopedFakeStorage.Service.MINIO,"*"));
        store.put(owner,Phase02ScopedFakeStorage.Service.REDIS,"lock",new byte[]{1},Duration.ofSeconds(30));
        assertThrows(SecurityException.class,()->store.put(owner,Phase02ScopedFakeStorage.Service.REDIS,
            "lock",new byte[]{2},Duration.ofSeconds(30)));
        assertThrows(IllegalArgumentException.class,()->store.put(owner,Phase02ScopedFakeStorage.Service.REDIS,
            "extra",new byte[]{1},Duration.ZERO));
    }
}
