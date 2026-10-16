package com.example.api.store;

import com.example.api.exception.NotFoundException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plain unit tests for {@link InMemoryStore} (no Spring context). Entities are simple
 * strings of the form "id:value" so the assertions can see which id was assigned.
 */
class InMemoryStoreTest {

    private final InMemoryStore<String> store = new InMemoryStore<>("Thing");

    @Test
    void assignsSequentialIdsStartingAtOne() {
        assertEquals("1:a", store.create(id -> id + ":a"));
        assertEquals("2:b", store.create(id -> id + ":b"));
        assertEquals(2, store.findAll().size());
    }

    @Test
    void updateReplacesExistingEntity() {
        store.create(id -> id + ":old");
        assertEquals("1:new", store.update(1, id -> id + ":new"));
        assertEquals("1:new", store.findById(1));
    }

    @Test
    void doesNotReuseIdsAfterDelete() {
        store.create(id -> id + ":a");
        store.delete(1);
        assertEquals("2:b", store.create(id -> id + ":b"));
    }

    @Test
    void missingIdsThrowNotFoundWithEntityName() {
        NotFoundException ex = assertThrows(NotFoundException.class, () -> store.findById(9));
        assertEquals("Thing 9 not found", ex.getMessage());
        assertThrows(NotFoundException.class, () -> store.update(9, id -> id + ":x"));
        assertThrows(NotFoundException.class, () -> store.delete(9));
        // A failed update must not create the entity as a side effect.
        assertTrue(store.findAll().isEmpty());
    }
}
