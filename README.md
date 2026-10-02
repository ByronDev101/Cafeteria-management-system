# Smart Cafeteria System

KCA University STU 3101 project. Spring Boot modular monolith, Thymeleaf + Bootstrap, MySQL, Flyway.
Design baseline: SRS-SC-001 and SDS-SC-001.

## Run locally
1. Create the database (see `docs/setup.sql`).
2. Set `DB_URL`, `DB_USER` and `DB_PASSWORD` (see `.env.example`). Local MySQL runs on port 3307.
3. `mvn spring-boot:run "-Dspring-boot.run.profiles=dev"`
4. Open http://localhost:8080/actuator/health - expect `{"status":"UP"}`.
5. Open http://localhost:8080/login and sign in with the temporary dev account (dev profile only):
   `dev.student` / `ChangeMe123!` (removed in Sprint 1).

## Branching
`main` = stable, `develop` = integration, `feature/<short-name>` = work, merged to `develop` by pull request.

## Definition of Done
- Meets the acceptance criteria of its FR numbers.
- Has a JUnit or MockMvc test; authorization is enforced server-side.
- Schema changes are a new Flyway migration (never edit an applied one).
- No secrets, tokens or real student data committed.
- Merged to `develop` by pull request.

## Package layout
`config`, `identity`, `menu`, `table`, `ordering`, `finance`, `reporting`, `shared` under `ke.ac.kca.cafeteria`.
