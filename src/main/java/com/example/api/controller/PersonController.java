package com.example.api.controller;

import com.example.api.generated.api.PersonsApi;
import com.example.api.generated.model.NewPerson;
import com.example.api.generated.model.Person;
import com.example.api.service.PersonService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * HTTP layer for {@code /api/v1/persons}.
 *
 * <p>Implements {@link PersonsApi}, which is generated at build time from
 * src/main/resources/openapi/api.yaml (see target/generated-sources/openapi). The generated
 * interface carries the routes, parameter bindings and validation annotations, so this class
 * only translates between HTTP responses and {@link PersonService} calls. To change a route
 * or a validation rule, edit api.yaml, not this class.
 *
 * <p>Errors (404, 400) are thrown as exceptions and turned into responses by
 * {@link com.example.api.exception.GlobalExceptionHandler}.
 */
@RestController
@RequestMapping("/api/v1") // Must match the "servers.url" in api.yaml.
public class PersonController implements PersonsApi {

    private final PersonService personService;

    public PersonController(PersonService personService) {
        this.personService = personService;
    }

    /** GET /persons → 200 with every person. */
    @Override
    public ResponseEntity<List<Person>> listPersons() {
        return ResponseEntity.ok(personService.findAll());
    }

    /** GET /persons/{id} → 200, or 404 if missing. */
    @Override
    public ResponseEntity<Person> getPerson(Long id) {
        return ResponseEntity.ok(personService.findById(id));
    }

    /** POST /persons → 201 with a Location header pointing at the new person. */
    @Override
    public ResponseEntity<Person> createPerson(NewPerson newPerson) {
        Person created = personService.create(newPerson);
        return CreatedResponses.created(created.getId(), created);
    }

    /** PUT /persons/{id} → 200 with the replaced person, or 404 if missing. */
    @Override
    public ResponseEntity<Person> updatePerson(Long id, NewPerson newPerson) {
        return ResponseEntity.ok(personService.update(id, newPerson));
    }

    /** DELETE /persons/{id} → 204, or 404 if missing. */
    @Override
    public ResponseEntity<Void> deletePerson(Long id) {
        personService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
