# Smart Cafeteria System

KCA University STU 3101 project. Spring Boot modular monolith, Thymeleaf + Bootstrap, MySQL, Flyway.
Design baseline: SRS-SC-001 and SDS-SC-001.

## Run locally
1. Create the database (see `docs/setup.sql`).
2. Set `DB_URL`, `DB_USER` and `DB_PASSWORD` (see `.env.example`). Local MySQL runs on port 3307.
3. `mvn spring-boot:run "-Dspring-boot.run.profiles=dev"`
4. Open http://localhost:8080/actuator/health - expect `{"status":"UP"}`.
5. Open http://localhost:8080/login. The dev profile creates four demo accounts (`demo.student`, `demo.staff`,
   `demo.manager`, `demo.admin`). Their shared password comes from the `APP_DEMO_PASSWORD` environment variable; set
   your own before the first run. See `docs/deployment-and-operations.md`.

## Production (HTTPS and limits)
See `docs/https-and-limits.md` for the `prod` and `behind-proxy` profiles, the certificate steps and the rate limits.

## Documentation
| File | For |
|---|---|
| `docs/user-guide.md` | Students, staff, managers and administrators: how to use the system |
| `docs/deployment-and-operations.md` | Installing, configuring, starting, backing up and restoring |
| `docs/https-and-limits.md` | HTTPS profiles, certificates and the abuse limits |
| `docs/security-notes.md` | The security controls, what they protect, and the known limits |
| `docs/integration-tests.md` | Running the database tests |
| `docs/test-report.md` | Test results, manual test records and user trials (to complete) |
| `docs/requirements-traceability.md` | Every SRS requirement, where it is built, how it is tested |

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
