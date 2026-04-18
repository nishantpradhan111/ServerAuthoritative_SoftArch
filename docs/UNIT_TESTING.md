# Unit and JUnit Testing Guide

## 1. Purpose

This guide explains the unit/JUnit test setup in CodeReboot, what is covered, and how to run the tests reliably.

## 2. Testing Stack

- JUnit 5 (`spring-boot-starter-test`)
- Mockito (via Spring Boot test starter)
- Spring Test support
- ArchUnit (architecture fitness tests)
- Maven Surefire (test execution)

## 3. Test Scope and Coverage

The test suite is primarily unit and slice-style verification.
It is organized by layer under `src/test/java/com/codereboot/gameboot/`:

- `api/` - controller behavior and response contracts
- `application/` - service orchestration and side effects
- `config/` - configuration behavior (for example, websocket origins)
- `domain/` - gameplay rules and invariants
- `security/` - request-id and auth-related behavior
- `transport/` - websocket parsing/dispatching/broadcast semantics
- `architecture/` - ArchUnit fitness rules for layering and dependency boundaries

Current cataloged total: 68 tests.
For the full list, see `UNIT_TEST_CATALOG.txt` in this same `docs/` directory.

## 4. Prerequisites

- Java 21
- Maven 3.9+

Most unit tests do not require launching the app manually.
Run tests from the repository root.

## 5. How to Run Tests

### 5.1 Run all tests

```bash
mvn test
```

### 5.2 Run a single test class

```bash
mvn -Dtest=RoomTest test
```

### 5.3 Run a single test method

```bash
mvn -Dtest=RoomTest#movementStaysInsideBoard test
```

### 5.4 Run multiple classes

```bash
mvn -Dtest=RoomTest,RoomServiceTest test
```

## 6. Windows Examples

If Maven is available on `PATH`:

```powershell
mvn test
```

If using your explicit Maven path:

```powershell
& "C:\Users\Nishant Pradhan\.maven\maven-3.9.14\bin\mvn.cmd" test
```

## 7. Typical Success Signal

A healthy run ends with output similar to:

- `Tests run: 68, Failures: 0, Errors: 0`
- `BUILD SUCCESS`

## 8. Troubleshooting Quick Notes

- If tests fail after code changes, run the exact failing class first, then the full suite.
- If a test count differs from this guide, trust the current output from `mvn test` and update `docs/UNIT_TEST_CATALOG.txt`.
- If Maven is not found, install Maven or use the full `mvn.cmd` path shown above.
