---
name: add-endpoint
description: Add a new CRUD REST endpoint for an entity to this Spring Boot API server, given the entity name and its attributes. Use when the user asks to add/create a new API endpoint, resource, or entity (e.g. "/add-endpoint Order customerName:string! total:number").
argument-hint: <EntityName> <attr>[:type][!] ...
---

# Add a CRUD endpoint for a new entity

The user supplies an entity name and attributes in `$ARGUMENTS`. Generate a full CRUD API
for it, following the spec-first pattern this project already uses for Items and Persons.

## 1. Parse the arguments

Format: `<EntityName> <attr>[:type][!] ...`

- `EntityName`: singular, PascalCase (`Order`, `BlogPost`). Normalize if the user wrote
  `order` or `blog_post`.
- Each attribute is `name[:type][!]`:
  - `name`: camelCase it (`phone_number` → `phoneNumber`).
  - `type` (default `string`) is one of the types in the table below.
  - A trailing `!` marks the attribute as required.
- The user may also describe attributes in plain prose ("a Book with title, author and
  ISBN, title required"). Interpret that the same way.
- With no `!` markers, make the first attribute required and the rest optional.
- If no entity name or no attributes were given, ask for them and stop.

| Arg type   | OpenAPI schema                                    | Java type        |
|------------|---------------------------------------------------|------------------|
| `string`   | `type: string`, `maxLength: 255` (`minLength: 1` if required) | `String` |
| `text`     | `type: string`, `maxLength: 2000`                 | `String`         |
| `email`    | `type: string`, `format: email`, `maxLength: 254` | `String`         |
| `phone`    | `type: string`, `pattern: '^\+?[0-9 ().-]{7,20}$'` | `String`        |
| `integer`  | `type: integer`, `format: int32`                  | `Integer`        |
| `long`     | `type: integer`, `format: int64`                  | `Long`           |
| `number`   | `type: number`, `format: double`                  | `Double`         |
| `boolean`  | `type: boolean`                                   | `Boolean`        |
| `date`     | `type: string`, `format: date`                    | `LocalDate`      |
| `datetime` | `type: string`, `format: date-time`               | `OffsetDateTime` |
| `uuid`     | `type: string`, `format: uuid`                    | `UUID`           |
| `enum(A\|B\|C)` | `type: string`, `enum: [A, B, C]`            | generated enum   |

If the user gives a constraint ("price ≥ 0", "max 50 chars"), add it to the schema
(`minimum`, `maxLength`, `pattern`, etc.).

Names used below, for entity `BlogPost`:
- `{Entity}` = `BlogPost`, `{entity}` = `blogPost`
- `{entities}` = plural, lowercase, kebab-case for the path and the tag: `blog-posts`
  (use correct English plurals: `Category` → `categories`, `Person` → `persons`, to match
  the existing API)
- `{Entities}` = plural PascalCase, used in the generated interface name: `BlogPosts`
  (the generator names the interface after the tag: tag `blog-posts` → `BlogPostsApi`)

Before writing anything, check that `/{entities}` is not already in
`src/main/resources/openapi/api.yaml`. If it is, tell the user and stop.

## 2. Edit the spec: `src/main/resources/openapi/api.yaml`

This file is the source of truth. Use `/persons` in it as the template.

1. Add `- name: {entities}` under the top-level `tags:`.
2. Add the `/{entities}` (list, create) and `/{entities}/{id}` (get, put, delete) paths
   just before `components:`. Use the same `operationId` naming as the existing paths
   (`list{Entities}`, `create{Entity}`, `get{Entity}`, `update{Entity}`, `delete{Entity}`),
   the same response codes, and the shared `BadRequest`/`NotFound` responses.
3. Add a `{Entity}Id` path parameter under `components.parameters` (int64, `minimum: 1`).
4. Add two schemas under `components.schemas`, before `Problem`:
   - `New{Entity}`: the request body. It has the attributes, their validation
     constraints, and a `required` list.
   - `{Entity}`: the response. It has `id` (int64) plus the same attributes, and `required`
     is `[id, <required attrs>]`. Keep the `format`, drop the length/pattern constraints.

## 3. Service: `src/main/java/com/example/api/service/{Entity}Service.java`

Copy `PersonService.java`. Storage is delegated to a field
`private final InMemoryStore<{Entity}> store = new InMemoryStore<>("{Entity}");`
(`com.example.api.store`), so don't write map/id/not-found logic yourself. The service
exposes `findAll`, `findById`, `create`, `update` and `delete`, each delegating to the
store, plus a `to{Entity}(long id, New{Entity})` mapper that copies every attribute from
`New{Entity}` to `{Entity}`. Keep the Javadoc comments in the same style.

## 4. Controller: `src/main/java/com/example/api/controller/{Entity}Controller.java`

Copy `PersonController.java`. It is a `@RestController` with `@RequestMapping("/api/v1")`
that implements the generated `{Entities}Api` interface and delegates to the service. The
create method returns `CreatedResponses.created(created.getId(), created)` (201 with a
`Location` header). Don't add hand-written validation: the constraints come from the spec.
Keep the class and per-method Javadoc comments in the same style.

## 5. Test: `src/test/java/com/example/api/{Entity}ControllerTest.java`

Copy `PersonControllerTest.java`:
- One round-trip test: POST with every attribute set (check 201, `Location`, each field
  in the response), then GET, PUT, DELETE, and a final GET that returns 404.
- One test per constraint that sends a bad value and expects 400: missing required field,
  bad email, pattern mismatch, value below minimum, and so on.

## 6. Build and verify

Run `./mvnw -q test`, then check
`grep -h "Tests run" target/surefire-reports/*.txt`. If generation or compilation fails,
look at the generated sources in `target/generated-sources/openapi` for the real
interface, method, and model names, fix your code, and run again. Don't report success
until every test passes.

## 7. Report back

Give the user:
- a table of the new endpoints
- the files you added or changed
- every default you picked (which fields are required, length limits, inferred types),
  so they can adjust them
