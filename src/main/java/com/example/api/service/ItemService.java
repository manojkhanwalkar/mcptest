package com.example.api.service;

import com.example.api.generated.model.Item;
import com.example.api.generated.model.NewItem;
import com.example.api.store.InMemoryStore;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;

/**
 * Business logic for items. Storage is delegated to an {@link InMemoryStore}; this class
 * only adds item-specific behavior (name filtering and request → model mapping).
 *
 * <p>Input has already been validated against api.yaml by the time it reaches here.
 */
@Service
public class ItemService {

    private final InMemoryStore<Item> store = new InMemoryStore<>("Item");

    /**
     * Lists items, optionally filtered by name.
     *
     * @param nameContains case-insensitive substring to match against the item name;
     *                     {@code null} or blank returns every item
     */
    public List<Item> findAll(String nameContains) {
        if (nameContains == null || nameContains.isBlank()) {
            return store.findAll();
        }
        // Locale.ROOT keeps lower-casing consistent regardless of the server's locale.
        String needle = nameContains.toLowerCase(Locale.ROOT);
        return store.findAll().stream()
                .filter(item -> item.getName().toLowerCase(Locale.ROOT).contains(needle))
                .toList();
    }

    /** @throws com.example.api.exception.NotFoundException if the item doesn't exist */
    public Item findById(long id) {
        return store.findById(id);
    }

    /** Stores a new item under a freshly generated id. */
    public Item create(NewItem newItem) {
        return store.create(id -> toItem(id, newItem));
    }

    /**
     * Replaces every field of an existing item (PUT semantics: omitted optional fields
     * become {@code null}).
     *
     * @throws com.example.api.exception.NotFoundException if the item doesn't exist
     */
    public Item update(long id, NewItem newItem) {
        return store.update(id, storedId -> toItem(storedId, newItem));
    }

    /** @throws com.example.api.exception.NotFoundException if the item doesn't exist */
    public void delete(long id) {
        store.delete(id);
    }

    /** Copies the request body fields onto a response model with the given id. */
    private static Item toItem(long id, NewItem newItem) {
        return new Item()
                .id(id)
                .name(newItem.getName())
                .description(newItem.getDescription())
                .price(newItem.getPrice());
    }
}
