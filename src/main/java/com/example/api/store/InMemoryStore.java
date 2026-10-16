package com.example.api.store;

import com.example.api.exception.NotFoundException;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongFunction;

/**
 * Thread-safe, in-memory CRUD storage for entities keyed by a generated {@code long} id.
 *
 * <p>Each service owns one instance per entity type, so the services only contain
 * entity-specific logic (mapping, filtering). Data is lost on restart; to move to a real
 * database, replace this class with a Spring Data repository and keep the same
 * method contracts in the services.
 *
 * <p>Ids start at 1 and are never reused, even after a delete.
 *
 * @param <T> the stored entity type (a generated OpenAPI model such as {@code Item})
 */
public class InMemoryStore<T> {

    /** Entity id → entity. ConcurrentHashMap lets requests on different threads share it safely. */
    private final Map<Long, T> entries = new ConcurrentHashMap<>();

    /** Source of new ids; atomic so concurrent creates never receive the same id. */
    private final AtomicLong nextId = new AtomicLong(1);

    /** Human-readable entity name used in error messages, e.g. "Item". */
    private final String entityName;

    /**
     * @param entityName name used in "not found" messages, e.g. {@code "Item"} gives
     *                   {@code "Item 42 not found"}
     */
    public InMemoryStore(String entityName) {
        this.entityName = entityName;
    }

    /** Returns an immutable snapshot of every stored entity, in no particular order. */
    public List<T> findAll() {
        return List.copyOf(entries.values());
    }

    /**
     * Returns the entity with the given id.
     *
     * @throws NotFoundException if no entity has that id (mapped to HTTP 404)
     */
    public T findById(long id) {
        T entity = entries.get(id);
        if (entity == null) {
            throw notFound(id);
        }
        return entity;
    }

    /**
     * Allocates a new id, builds the entity with it and stores it.
     *
     * @param factory builds the entity from the newly assigned id
     * @return the stored entity
     */
    public T create(LongFunction<T> factory) {
        long id = nextId.getAndIncrement();
        T created = factory.apply(id);
        entries.put(id, created);
        return created;
    }

    /**
     * Replaces the entity with the given id. The existence check and the write are a
     * single atomic operation, so a concurrent delete can't be silently undone.
     *
     * @param factory builds the replacement entity from the (unchanged) id
     * @return the stored replacement
     * @throws NotFoundException if no entity has that id (mapped to HTTP 404)
     */
    public T update(long id, LongFunction<T> factory) {
        T updated = factory.apply(id);
        if (entries.replace(id, updated) == null) {
            throw notFound(id);
        }
        return updated;
    }

    /**
     * Removes the entity with the given id.
     *
     * @throws NotFoundException if no entity has that id (mapped to HTTP 404)
     */
    public void delete(long id) {
        if (entries.remove(id) == null) {
            throw notFound(id);
        }
    }

    private NotFoundException notFound(long id) {
        return new NotFoundException(entityName + " " + id + " not found");
    }
}
