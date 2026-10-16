package com.example.api.service;

import com.example.api.generated.model.NewPerson;
import com.example.api.generated.model.Person;
import com.example.api.store.InMemoryStore;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Business logic for persons. Storage is delegated to an {@link InMemoryStore}; this class
 * only adds person-specific behavior (request → model mapping).
 *
 * <p>Input has already been validated against api.yaml (required fields, email format,
 * phone pattern) by the time it reaches here.
 */
@Service
public class PersonService {

    private final InMemoryStore<Person> store = new InMemoryStore<>("Person");

    /** Returns every person, in no particular order. */
    public List<Person> findAll() {
        return store.findAll();
    }

    /** @throws com.example.api.exception.NotFoundException if the person doesn't exist */
    public Person findById(long id) {
        return store.findById(id);
    }

    /** Stores a new person under a freshly generated id. */
    public Person create(NewPerson newPerson) {
        return store.create(id -> toPerson(id, newPerson));
    }

    /**
     * Replaces every field of an existing person (PUT semantics: omitted optional fields
     * become {@code null}).
     *
     * @throws com.example.api.exception.NotFoundException if the person doesn't exist
     */
    public Person update(long id, NewPerson newPerson) {
        return store.update(id, storedId -> toPerson(storedId, newPerson));
    }

    /** @throws com.example.api.exception.NotFoundException if the person doesn't exist */
    public void delete(long id) {
        store.delete(id);
    }

    /** Copies the request body fields onto a response model with the given id. */
    private static Person toPerson(long id, NewPerson newPerson) {
        return new Person()
                .id(id)
                .name(newPerson.getName())
                .address(newPerson.getAddress())
                .email(newPerson.getEmail())
                .phoneNumber(newPerson.getPhoneNumber());
    }
}
