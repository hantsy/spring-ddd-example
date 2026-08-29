# Spring DDD Example

[![build](https://github.com/hantsy/spring-ddd-example/actions/workflows/build.yml/badge.svg)](https://github.com/hantsy/spring-ddd-example/actions/workflows/build.yml)

A **Library** example demonstrating how to implement a modular DDD application with **Spring Boot 4.2** and **Spring Modulith 2.2**. It reimplements the [Jakarta EE DDD example](https://github.com/hantsy/jakartaee-ddd-example) using the Spring stack.

The idea comes from the [Spring I/O 2024 talk: Implementing Domain-Driven Design with Spring](https://www.youtube.com/watch?v=VGhg6Tfxb60) by [Maciej Walkowiak](https://github.com/maciejwalkowiak).

## Architecture

Two bounded contexts, modelled as Spring Modulith application modules, plus a shared kernel:

- **`catalog`** — `Book`, `Copy`, ISBN search (backed by an Open Library adapter)
- **`lending`** — `Loan`, overdue fees, and the `LoanCreated` / `LoanClosed` domain events
- **`common`** — the shared kernel (`DomainException`, the `@UseCase` stereotype, `Clock`, and the logging aspect)

Notable DDD / Modulith features:

- Domain model with embedded-ID value objects (`BookId`, `CopyId`, `Isbn`, `BarCode`, `LoanId`, `UserId`), repositories as domain contracts (Spring Data JPA).
- Application use cases via the composed `@UseCase` annotation (`@Service` + `@Transactional` + logging aspect).
- Cross-module communication through domain events and `@ApplicationModuleListener` (asynchronous, after commit) — the catalog observes `LoanCreated`/`LoanClosed` to keep copy availability in sync.
- Architecture enforcement with Spring Modulith's `ApplicationModules` verifier and ArchUnit.

## Prerequisites

- Java 25+
- Maven 3.9+ (or use the included Maven wrapper)

## Build

```bash
./mvnw clean package
```

Run the network-dependent integration test against the real Open Library API (excluded from the default build):

```bash
./mvnw test -Dgroups=integration
```

## Contribute

If you have any ideas about the implementation, please create an issue to discuss them.
