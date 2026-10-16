package com.example.api.controller;

import com.example.api.generated.api.ItemsApi;
import com.example.api.generated.model.Item;
import com.example.api.generated.model.NewItem;
import com.example.api.service.ItemService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * HTTP layer for {@code /api/v1/items}.
 *
 * <p>Implements {@link ItemsApi}, which is generated at build time from
 * src/main/resources/openapi/api.yaml (see target/generated-sources/openapi). The generated
 * interface carries the routes, parameter bindings and validation annotations, so this class
 * only translates between HTTP responses and {@link ItemService} calls. To change a route or
 * a validation rule, edit api.yaml, not this class.
 *
 * <p>Errors (404, 400) are thrown as exceptions and turned into responses by
 * {@link com.example.api.exception.GlobalExceptionHandler}.
 */
@RestController
@RequestMapping("/api/v1") // Must match the "servers.url" in api.yaml.
public class ItemController implements ItemsApi {

    private final ItemService itemService;

    public ItemController(ItemService itemService) {
        this.itemService = itemService;
    }

    /** GET /items[?nameContains=...] → 200 with the (optionally filtered) list. */
    @Override
    public ResponseEntity<List<Item>> listItems(String nameContains) {
        return ResponseEntity.ok(itemService.findAll(nameContains));
    }

    /** GET /items/{id} → 200, or 404 if missing. */
    @Override
    public ResponseEntity<Item> getItem(Long id) {
        return ResponseEntity.ok(itemService.findById(id));
    }

    /** POST /items → 201 with a Location header pointing at the new item. */
    @Override
    public ResponseEntity<Item> createItem(NewItem newItem) {
        Item created = itemService.create(newItem);
        return CreatedResponses.created(created.getId(), created);
    }

    /** PUT /items/{id} → 200 with the replaced item, or 404 if missing. */
    @Override
    public ResponseEntity<Item> updateItem(Long id, NewItem newItem) {
        return ResponseEntity.ok(itemService.update(id, newItem));
    }

    /** DELETE /items/{id} → 204, or 404 if missing. */
    @Override
    public ResponseEntity<Void> deleteItem(Long id) {
        itemService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
