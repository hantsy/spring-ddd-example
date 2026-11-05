# Applying DDD with Spring Boot

This post is for Java developers who want to see how tactical Domain-Driven
Design (DDD) fits into a Spring Boot application. It follows the library sample
from domain objects through persistence, use cases, events, and tests. The
library's requirements and story are covered in the
[Jakarta EE DDD article](https://github.com/hantsy/jakartaee-ddd-example/blob/master/docs/2026-10-08-applying-ddd-with-jakarta-ee-and-jakarta-data.md).

## Strategic design

The design separates Cataloging, which owns books and copies, from Lending,
which owns loans and overdue fees. Lending refers to a copy by its identifier
and publishes loan events so Cataloging can update copy availability without
sharing its entity model. This post focuses on Spring Modulith's
asynchronous, after-commit event listener as the implementation of that
boundary; see the linked article for the domain story, context map, and
subdomain rationale.

## Model identity and state with JPA entities

The catalog context owns JPA entities `Book` and `Copy`; lending owns `Loan`.
Each uses an embedded identifier value object. The types keep identifiers
distinct even though their values are UUIDs. `Isbn` validates ISBN values when
constructed, and `BarCode` wraps a physical copy's barcode.

`Loan` owns the expected return date, return time, and overdue fee. Its
constructor sets a 30-day term, and `returned()` records the return and
calculates the matching flat fee tier. `Copy` owns its availability flag and
provides `makeUnavailable()` and `makeAvailable()` operations.

The JPA entities also declare `@Version` fields on `Copy` and `Loan`. The code
doesn't add an explicit lock or unique constraint for one open loan per copy,
so those version fields alone don't establish a demonstrated concurrency
policy in the integration tests.

## Keep repository contracts beside the model

The repositories extend Spring Data's `CrudRepository`. A derived finder
expresses the catalog's duplicate-ISBN check without a handwritten
implementation:

```java
public interface BookRepository extends CrudRepository<Book, BookId> {
    boolean existsByIsbn(Isbn isbn);
}
```

`LoanRepository` similarly defines an availability query based on the absence
of an open loan. Spring Data JPA provides the persistence implementation; the
application use cases depend on the repository interfaces. The test
configuration uses H2 and creates the schema through Hibernate.

## Apply a use-case boundary

`@UseCase` composes Spring's service and transaction annotations with the
application's logging aspect:

```java
@Service
@Transactional
@Logged
public @interface UseCase {
}
```

`AddBookToCatalogUseCase` checks for a duplicate ISBN, asks the
`BookSearchService` port to resolve the title, creates a `Book`, and saves it.
`RentBookUseCase` checks availability, obtains time through an injected
`Clock`, saves a `Loan`, and publishes `LoanCreated`. `ReturnBookUseCase`
updates the loan and publishes `LoanClosed`.

The transaction boundary belongs to the use-case class, so the use case
coordinates repository work as one application operation. `CopyAvailabilityValidator`
encapsulates the lending availability check, although it is a Spring-injected
service in the domain package. The architecture test explicitly exempts this
class from its framework-independence rule.

## Use Modulith events across contexts

Lending publishes events with Spring's `ApplicationEventPublisher`. The
catalog's `DomainEventListener` handles them through `@ApplicationModuleListener`.
Spring Modulith configures that listener as asynchronous and after commit; the
integration test uses Awaitility to wait for the copy state change. The event
publication registry is backed by the JPA stack in this project.

This boundary keeps lending from importing the catalog entity. The event
contains the lending context's `CopyId`; catalog translates it to its own
`CopyId`, loads the `Copy`, and changes its availability. The projects'
architecture rules document this as the one permitted catalog-to-lending
dependency.

The event publication registry helps track listener processing, but this
sample remains a modular monolith with an in-process event publisher. It
doesn't turn the contexts into independent services.

## Test behavior and module structure

`LibraryIntegrationTest` checks sequential rent and return behavior, copy
availability, rejection of a second rent attempt, and an overdue fee. Its
rejection assertion uses `Exception.class`, so it doesn't confirm the precise
domain exception type. `CatalogModuleTest` uses Spring Modulith's `Scenario`
API to verify the asynchronous catalog reactions.

`ModularityTest` calls `ApplicationModules.verify()` to check module
dependencies. `ArchitectureTest` adds rules for layer direction, context
isolation, and repository placement.

The [Spring Modulith verification guide](https://docs.spring.io/spring-modulith/reference/verification.html)
describes the module checks, and the
[event publication reference](https://docs.spring.io/spring-modulith/reference/events.html)
covers event listener and registry behavior.

## Run the sample

The default Maven build excludes the live Open Library integration test:

```shell
./mvnw clean package
```

To run that network-dependent test, use:

```shell
./mvnw test -Dgroups=integration
```

The project targets Java 25 and uses Spring Boot 4.2.0-M2 and Spring Modulith
2.2.0-M2, as declared in `pom.xml`.

## Further reading

- [Spring Modulith reference](https://docs.spring.io/spring-modulith/reference/)
- [Jakarta EE DDD article](https://github.com/hantsy/jakartaee-ddd-example/blob/master/docs/2026-10-08-applying-ddd-with-jakarta-ee-and-jakarta-data.md)
- [Spring I/O talk: Implementing Domain-Driven Design with Spring](https://www.youtube.com/watch?v=VGhg6Tfxb60)

The [Helidon](https://github.com/hantsy/helidon-ddd-example),
[Quarkus](https://github.com/hantsy/quarkus-ddd-example), and
[Spring Kotlin](https://github.com/hantsy/spring-ddd-kotlin-example)
repositories implement the same example with different persistence and event
stacks.
