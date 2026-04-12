package dev.openui.langcore.store;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link DefaultStore}.
 * Ref: Design §14
 */
class StoreTest {

    private DefaultStore store() { return new DefaultStore(); }

    // -------------------------------------------------------------------------
    // set triggers listener
    // -------------------------------------------------------------------------

    @Test
    void set_triggersListener() {
        DefaultStore s = store();
        AtomicInteger calls = new AtomicInteger();
        s.subscribe(calls::incrementAndGet);

        s.set("$x", 1.0);

        assertEquals(1, calls.get());
    }

    @Test
    void set_storesValue() {
        DefaultStore s = store();
        s.set("$x", 42.0);
        assertEquals(42.0, s.get("$x"));
    }

    // -------------------------------------------------------------------------
    // identity-equal value skips notify
    // -------------------------------------------------------------------------

    @Test
    void set_identityEqual_skipsNotify() {
        DefaultStore s = store();
        String val = "same";
        s.set("$x", val);

        AtomicInteger calls = new AtomicInteger();
        s.subscribe(calls::incrementAndGet);

        s.set("$x", val); // same reference
        assertEquals(0, calls.get(), "Identity-equal value must not trigger listener");
    }

    // -------------------------------------------------------------------------
    // shallow-equal Map skips notify
    // -------------------------------------------------------------------------

    @Test
    void set_shallowEqualMap_skipsNotify() {
        DefaultStore s = store();
        Object sharedVal = new Object();
        Map<String, Object> map = Map.of("k", sharedVal);
        s.set("$obj", map);

        AtomicInteger calls = new AtomicInteger();
        s.subscribe(calls::incrementAndGet);

        // Different Map instance, same keys and same value identity
        Map<String, Object> sameShallow = new LinkedHashMap<>();
        sameShallow.put("k", sharedVal);
        s.set("$obj", sameShallow);

        assertEquals(0, calls.get(), "Shallow-equal map must not trigger listener");
    }

    @Test
    void set_differentMapValue_triggersNotify() {
        DefaultStore s = store();
        s.set("$obj", Map.of("k", "v1"));

        AtomicInteger calls = new AtomicInteger();
        s.subscribe(calls::incrementAndGet);

        s.set("$obj", Map.of("k", "v2")); // different value object
        assertEquals(1, calls.get());
    }

    // -------------------------------------------------------------------------
    // initialize — persisted overrides defaults; defaults only for new keys
    // -------------------------------------------------------------------------

    @Test
    void initialize_persistedOverridesDefault() {
        DefaultStore s = store();
        s.initialize(
                Map.of("$x", "default"),
                Map.of("$x", "persisted")
        );
        assertEquals("persisted", s.get("$x"));
    }

    @Test
    void initialize_defaultOnlyForNewKeys() {
        DefaultStore s = store();
        s.set("$x", "existing");
        s.initialize(Map.of("$x", "default"), Map.of());
        assertEquals("existing", s.get("$x"), "Default must not overwrite existing key");
    }

    @Test
    void initialize_triggersListener() {
        DefaultStore s = store();
        AtomicInteger calls = new AtomicInteger();
        s.subscribe(calls::incrementAndGet);

        s.initialize(Map.of("$a", 1.0), Map.of());
        assertEquals(1, calls.get());
    }

    // -------------------------------------------------------------------------
    // getSnapshot — immutable view
    // -------------------------------------------------------------------------

    @Test
    void getSnapshot_reflectsCurrentState() {
        DefaultStore s = store();
        s.set("$x", 10.0);
        Map<String, Object> snap = s.getSnapshot();
        assertEquals(10.0, snap.get("$x"));
    }

    @Test
    void getSnapshot_isImmutable() {
        DefaultStore s = store();
        s.set("$x", 1.0);
        Map<String, Object> snap = s.getSnapshot();
        assertThrows(UnsupportedOperationException.class, () -> snap.put("$y", 2.0));
    }

    // -------------------------------------------------------------------------
    // unsubscribe works
    // -------------------------------------------------------------------------

    @Test
    void unsubscribe_stopsNotifications() {
        DefaultStore s = store();
        AtomicInteger calls = new AtomicInteger();
        Runnable unsub = s.subscribe(calls::incrementAndGet);

        s.set("$x", 1.0);
        unsub.run();
        s.set("$x", 2.0);

        assertEquals(1, calls.get(), "Listener must not be called after unsubscribe");
    }

    // -------------------------------------------------------------------------
    // dispose clears listeners
    // -------------------------------------------------------------------------

    @Test
    void dispose_clearsListeners() {
        DefaultStore s = store();
        AtomicInteger calls = new AtomicInteger();
        s.subscribe(calls::incrementAndGet);

        s.dispose();
        s.set("$x", 1.0); // should be ignored after dispose

        assertEquals(0, calls.get(), "No listeners should fire after dispose");
    }

    // -------------------------------------------------------------------------
    // shallowEquals unit tests
    // -------------------------------------------------------------------------

    @Test void shallowEquals_sameRef()      { assertTrue(DefaultStore.shallowEquals("a", "a")); }
    @Test void shallowEquals_bothNull()     { assertTrue(DefaultStore.shallowEquals(null, null)); }
    @Test void shallowEquals_oneNull()      { assertFalse(DefaultStore.shallowEquals("a", null)); }
    @Test void shallowEquals_differentPrim(){ assertFalse(DefaultStore.shallowEquals(1.0, 2.0)); }
}
