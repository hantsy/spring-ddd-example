# Applying DDD with Spring Boot

Two bounded contexts can live in separate Java packages and still become
coupled through accidental dependencies. This version uses Spring Modulith to
make the Cataloging–Lending boundary explicit and verifiable, while JPA and
Spring Data handle aggregate persistence. This post focuses on the
Spring-specific implementation decisions.

## Strategic design

Cataloging owns book records and physical copies; Lending owns loans and
overdue fees. The contexts share a copy identifier and loan events, not each
other's entities. If you want the full story behind these boundaries, start
with the [Jakarta EE DDD example](https://github.com/hantsy/jakartaee-ddd-example/blob/master/docs/2026-10-08-applying-ddd-with-jakarta-ee-and-jakarta-data.md).
Here, we'll focus on how Spring Modulith turns the boundary into module
metadata and checks.

## Part II — Tactical design with Spring Boot

The framework choices are not interchangeable details: JPA maps the domain
entities directly, while Spring Modulith protects the dependency boundary
between the contexts and coordinates event reactions.

### 1. Entities, value objects, and aggregate roots

#### Which concepts are entities and which are values

The model tracks three entities by identity: `Book` represents a catalog
record, `Copy` a physical item, and `Loan` a lending record. Each is mapped as
a JPA entity and used as an aggregate root for its own state.

The model represents identities and business values with `BookId`, `CopyId`,
`LoanId`, `UserId`, `Isbn`, `BarCode`, and `OverdueFee`. `Isbn` and `BarCode`
are value objects; `OverdueFee` is the lending fee schedule. `Copy` stores a
`BookId`, while `Loan` stores a Lending-owned `CopyId` and a `UserId`.

#### Aggregate boundaries

`Copy` refers to its book by `BookId`, and `Loan` refers to the copy and
borrower by identifiers. Neither aggregate embeds another aggregate. This
keeps catalog changes separate from loan changes.

> Aggregates refer to each other by identity, not by containment.

#### Invariants and their guards

`Copy` changes availability through `makeAvailable()` and
`makeUnavailable()`. `Loan.returned()` records the return and calculates an
overdue fee. `CopyAvailabilityValidator` checks whether another open loan
exists before the use case creates a loan.

`RegisterBookCopyUseCase` accepts a `BookId` but does not check that the book
exists before saving the copy. The sample therefore does not enforce the
book-to-copy association invariant.

`Copy` and `Loan` have JPA `@Version` fields, but the integration tests do not
exercise simultaneous rentals or define a concurrency policy. A repository
check followed by a save does not, by itself, prove that only one concurrent
request can rent a copy.

#### Map the model with JPA

JPA mapping is part of the domain classes in this variant.
`@EmbeddedId` maps entity identity, and `@Embedded` with
`@AttributeOverride` maps values to the intended columns. For example, the
book entity embeds both its identifier and ISBN:

```java
@Entity
public class Book {
    @EmbeddedId
    private BookId id;

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "isbn"))
    private Isbn isbn;
}
```

`Copy` and `Loan` use the same mapping approach for their identifiers and
values. This model does not use jMolecules aggregate annotations or
interfaces; the aggregate boundaries come from the domain design and are
checked through package and module rules.

### 2. Repositories with Spring Data JPA

The repository interfaces live beside their aggregate types in the domain
packages and extend Spring Data's `CrudRepository`. Spring Data JPA supplies
the implementation:

```java
public interface BookRepository extends CrudRepository<Book, BookId> {
    boolean existsByIsbn(Isbn isbn);
}
```

Spring Data derives `existsByIsbn` from the method name. `LoanRepository`
defines a derived query for an open loan and a default `isAvailable` method.
The use cases depend on these contracts, not on entity managers or generated
implementation classes.

This mapping style differs from the Helidon variant: the Spring repository
does not declare SQL or use row mappers, and JPA annotations map the domain
entities directly.

### 3. Domain events with Spring Modulith

#### Events in this example

Lending publishes `LoanCreated` and `LoanClosed` as immutable records. Each
event carries the lending context's `CopyId` rather than a Cataloging `Copy`.
The use cases publish them through Spring's `ApplicationEventPublisher`.
The loan records overdue fees, but the sample does not publish an
`OverdueFeeCalculated` event.

#### Publish and observe Spring Modulith events

Cataloging handles the events in `DomainEventListener`, whose methods use
`@ApplicationModuleListener`. Spring Modulith processes these listeners
asynchronously after the Lending transaction commits. The listener translates
the event identifier to Cataloging's `CopyId`, loads its own `Copy`, and
updates availability. The integration test waits for this asynchronous state
change.

Spring Modulith's event-publication registry tracks listener publications
using this project's JPA configuration. That tracking does not turn the
in-process publisher into a message broker or make the contexts independent
services.

### 4. Domain and application services with Spring

Application services coordinate use cases; domain services enforce rules that
depend on more than one aggregate. Spring supplies dependency injection and
transaction management.

#### 4.1 Application services and transactions

`AddBookToCatalogUseCase`, `RegisterBookCopyUseCase`, `RentBookUseCase`, and
`ReturnBookUseCase` coordinate their application operations. The shared
`@UseCase` annotation composes Spring's `@Service`, `@Transactional`, and the
sample's `@Logged` aspect. This places a transaction around each use case.

`RentBookUseCase` checks availability, obtains time from an injected `Clock`,
saves a `Loan`, and publishes `LoanCreated`. `ReturnBookUseCase` loads the
loan, calls its return behavior, saves the changed aggregate, and publishes
`LoanClosed`.

#### 4.2 Domain services and cross-aggregate rules

`CopyAvailabilityValidator` uses `LoanRepository` to check for an open loan
for a copy. It raises `CopyNotAvailableException` when the repository reports
that a copy is already loaned. Because this rule depends on other loans, it
does not belong inside one `Loan` entity.

The validator is in the domain package, but Spring injects it as a component.
`ArchitectureTest` explicitly excludes this class from its rule that keeps
domain code free of Spring application-framework APIs.

#### 4.3 Ports and adapters with Spring

Cataloging declares `BookSearchService` as a port for resolving an ISBN.
`OpenLibraryBookSearchService` implements the port in the infrastructure
package and translates the provider response and failures into domain types
and exceptions. `AddBookToCatalogUseCase` depends on the port rather than the
HTTP client or provider response model.

### 5. Package and module boundary verification

The source packages follow bounded contexts: `catalog` and `lending` each
contain `application` and `domain` packages, while Cataloging also has an
`infrastructure` package for the external search adapter. `shared` contains
cross-cutting types.

#### Check layer and context rules with ArchUnit

`ArchitectureTest` uses ArchUnit to check that:

- domain packages do not depend on application or infrastructure packages;
- domain packages avoid the listed Spring application APIs, except for
  `CopyAvailabilityValidator`;
- application packages depend on domain but not on infrastructure;
- `shared` does not depend on either bounded context;
- Lending does not depend on Cataloging;
- only the catalog `DomainEventListener` may depend on Lending; and
- repository contracts reside in domain packages.

The framework-independence rule allows JPA mappings and Spring Data repository
contracts. It excludes `CopyAvailabilityValidator` because Spring injects that
domain service.

#### Verify module dependencies with Spring Modulith

ArchUnit guards the intended layer and context dependencies. Spring Modulith
adds a module-level contract that makes accidental access to another module's
internal packages—and cycles—fail `ApplicationModules.verify()`. Lending
allows `shared`; Cataloging allows only Lending's named `domain` interface.
The Cataloging module declares its side of that contract in
`catalog/package-info.java`:

```java
@ApplicationModule(allowedDependencies = "lending :: domain")
package com.example.library.catalog;
```

The `lending.domain` package is marked with `@NamedInterface("domain")`.
Cataloging can therefore observe public loan events without gaining access to
every Lending package. `ModularityTest` runs this verification alongside the
ArchUnit rules, so both module access and layer dependencies are checked by
the build rather than left as package-naming conventions.

## Part III — Verify scenarios with integration tests

`LibraryIntegrationTest` exercises sequential rent and return behavior, copy
availability, a second-rental rejection, and an overdue fee. The rejection
assertion accepts `Exception`, so it does not confirm the specific domain
exception. `CatalogModuleTest` uses Spring Modulith's `Scenario` API to verify
the asynchronous catalog reaction.

The tests do not exercise simultaneous rentals, so they do not demonstrate
that the availability check is race-free.

## Run the sample

Build the project and run the default tests with:

```shell
./mvnw clean package
```

To run the network-dependent Open Library integration test, use:

```shell
./mvnw test -Dgroups=integration
```

## Summary

Spring Boot maps the aggregate roots directly as JPA entities, uses Spring
Data JPA repository contracts, and delegates cross-context event handling and
module verification to Spring Modulith. ArchUnit complements those module
checks with explicit layer and dependency rules.

### Further reading

- [Spring Modulith reference](https://docs.spring.io/spring-modulith/reference/)
- [Spring Modulith module verification](https://docs.spring.io/spring-modulith/reference/verification.html)
- [Spring Modulith events](https://docs.spring.io/spring-modulith/reference/events.html)
- [Spring I/O talk: Implementing Domain-Driven Design with Spring](https://www.youtube.com/watch?v=VGhg6Tfxb60)
- [Helidon DDD example](https://github.com/hantsy/helidon-ddd-example)
- [Spring Boot Kotlin DDD example](https://github.com/hantsy/spring-ddd-kotlin-example)
- [Quarkus DDD example](https://github.com/hantsy/quarkus-ddd-example)
