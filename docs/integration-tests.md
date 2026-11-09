# Database integration tests

The normal tests (`mvn test`) use no database. The integration tests run the real services, transactions and SQL
against a real MySQL schema. They are the evidence for the SRS checks that need a real database: the migrations build
the schema from nothing, the database refuses bad data by itself, an order that fails leaves no trace, and many
simultaneous orders can never take a student over a credit limit.

## One-time set-up

1. Start MySQL (the one on port 3307) and open MySQL Workbench with an account that may create schemas.
2. Create a separate schema. Its name MUST end in `_test`, because the tests wipe it every run:

   ```sql
   CREATE DATABASE smart_cafeteria_test CHARACTER SET utf8mb4;
   ```

3. Give the tests a database user (the same one you use for the app is fine). In PowerShell, in the window where you
   run Maven:

   ```
   $env:IT_DB_USER     = "<database user>"
   $env:IT_DB_PASSWORD = "<database password>"
   ```

   If you do not set these, the tests fall back to `DB_USER` and `DB_PASSWORD`. If your MySQL is not at
   `localhost:3307`, also set `IT_DB_URL`, for example
   `jdbc:mysql://localhost:3307/smart_cafeteria_test?useUnicode=true&characterEncoding=UTF-8`.

## Running them

| Command | What runs |
|---|---|
| `mvn test` | Unit tests only. No database needed. |
| `mvn verify` | Unit tests, then the integration tests (files ending in `IT.java`). |
| `mvn verify -DskipITs` | Same as `mvn test` plus packaging; skips the integration tests. |
| `mvn verify -Dit.test=CreditConcurrencyIT` | Only one integration test class. |

Reports are in `target/failsafe-reports`. Look for `Tests run:` near the end of the Maven output.

## Safety

- The tests refuse to start if the database name in the URL does not end in `_test`, so they cannot wipe the real
  `smart_cafeteria` schema by mistake.
- Each run drops every table, rebuilds them from the migrations, and empties the data tables before every test.
- The sample student accounts use a placeholder, not a real password.

## What they cover

| Class | Proves |
|---|---|
| `SchemaAndConstraintsIT` | All migrations apply on an empty schema and Hibernate accepts the result. MySQL itself rejects a second payment record for an order, a negative amount, a ledger line of zero or of an unknown type, a second credit account for a student, and a negative limit. |
| `PaymentFlowIT` | A payment is opened with the order, received, refunded after a cancellation, voided when the student cancels, and never received twice. The board, the student's page, the refunds tab and the audit log agree with the database. |
| `ReportQueriesIT` | The manager report totals for the chosen campus days (including the midnight boundary in Nairobi), the search filters, the 100-row limit, and that search text cannot act as a wildcard or SQL. |
| `CreditLedgerIT` | Hold, charge, reversal and payment against the real ledger. An order over the limit, or for a student with no credit, leaves no order, payment, ledger line or history row. Accepting an order with no hold is refused and nothing changes. The credit summary in the manager report. |
| `CreditConcurrencyIT` | Eight simultaneous orders with room for three: exactly three succeed and the ledger never exceeds the limit. Students do not block each other. A double tap places one order and one hold. |

## Recording the MySQL version

The SDS names "MySQL 8.x-compatible". Put the version you tested on in the test report:

```sql
SELECT VERSION();
```

If you can, run `mvn verify` once against MySQL 8.4 as well, by pointing `IT_DB_URL` at an 8.4 server.

## If something fails

- **Access denied:** check `IT_DB_USER` and `IT_DB_PASSWORD`, and that the user may create tables in `smart_cafeteria_test`.
- **Unknown database:** create the schema (step 2 above) or fix `IT_DB_URL`.
- **"Refusing to wipe a database whose name does not end in _test":** the URL points at the wrong schema. This is the
  safety check working.
- **A test fails on a number or a status:** copy the failing test name and the message, and the matching lines from
  `target/failsafe-reports`. That is a real finding about the code or the data, not a nuisance.

## Keeping the login out of PowerShell (optional, more reliable)

If the tests say `Access denied for user 'root'@'localhost' (using password: NO)`, the test run did not receive your
`IT_DB_USER` and `IT_DB_PASSWORD` settings (for example the settings were made in a different PowerShell window).
Instead of environment variables you can use a small file that is never committed:

1. Create `src/test/resources/application-it-local.properties` with two lines:

   ```
   spring.datasource.username=<database user>
   spring.datasource.password=<database password>
   ```

2. Run `mvn clean verify`. Nothing else is needed. The file is listed in `.gitignore`, so it cannot be committed by
   accident. Check with `git status` that it does not appear.
