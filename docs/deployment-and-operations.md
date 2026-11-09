# Deployment and operations

For a person installing, running and looking after the system for a demonstration or a trial. Nothing here contains a
real password: wherever a password or secret is needed, choose your own and keep it out of Git and out of chat.

## 1. What you need
- Java JDK (the version the project is built with; `mvn -v` shows it) and Maven 3.9.
- MySQL (the SDS names "MySQL 8.x-compatible"; the development server runs on port 3307). Record `SELECT VERSION();`.
- A certificate if you serve HTTPS yourself (see `docs/https-and-limits.md`).

## 2. Database
1. In MySQL Workbench, as an administrative account:
   ```sql
   CREATE DATABASE IF NOT EXISTS smart_cafeteria CHARACTER SET utf8mb4;
   CREATE USER IF NOT EXISTS 'cafe_app'@'localhost' IDENTIFIED BY '<choose a strong password>';
   GRANT ALL ON smart_cafeteria.* TO 'cafe_app'@'localhost';
   FLUSH PRIVILEGES;
   ```
   (`docs/setup.sql` is the same with a placeholder password. Never keep the placeholder.)
2. The tables are created and updated automatically at start-up by Flyway (migrations V1 to V9). Never edit a
   migration that has been applied; add a new one.

## 3. Configuration (environment variables)
Set these in the window or service that starts the application.

| Variable | Meaning |
|---|---|
| `DB_URL` | For example `jdbc:mysql://localhost:3307/smart_cafeteria?useUnicode=true&characterEncoding=UTF-8` |
| `DB_USER`, `DB_PASSWORD` | The database login |
| `PUBLIC_BASE_URL` | The address phones will open from the QR codes, for example `https://cafeteria.example.ac.ke:8443` |
| `SSL_KEYSTORE`, `SSL_KEYSTORE_PASSWORD`, `SSL_KEY_ALIAS` | Certificate (profile `prod` only) |
| `APP_DEMO_PASSWORD` | Shared password for the four demo accounts the `dev` profile creates |

Other settings (session timeout, rate limits, HTTPS requirement) are documented in `docs/https-and-limits.md`.

## 4. Building and starting
```
mvn clean package -DskipITs
java -jar target\smart-cafeteria-<version>.jar --spring.profiles.active=prod
```
- `dev`: HTTP on port 8080, demo accounts created, for development.
- `prod`: HTTPS on port 8443, plain HTTP refused, secure cookies.
- `behind-proxy`: for a reverse proxy that handles HTTPS.
Check `http(s)://<server>/actuator/health` shows `{"status":"UP"}`.

### First administrator (open item)
The `prod` profile creates no accounts. Until a one-time first-administrator setting is added, create the first
accounts by running the application once with the `dev` profile against the same database, with a strong
`APP_DEMO_PASSWORD` set. This creates `demo.student`, `demo.staff`, `demo.manager` and `demo.admin`. Then sign in as
`demo.admin`, create the real accounts, **deactivate or change the password of every demo account you do not need**,
and from then on start with the `prod` profile.

### Restart procedure (NFR-12)
1. Stop the application (Ctrl+C in its window, or stop the service).
2. Start it again with the same command and variables. Flyway checks the schema and does nothing if it is current.
3. Check `/actuator/health`, sign in as staff, and open the order board.
Orders already placed are in the database and appear on the board again.

## 5. Backup and restore (NFR-06)
Targets: lose no more than 24 hours of data; restore to a working demonstration within 4 hours.

### Taking a backup (before every demonstration or release, and daily while in use)
In PowerShell. `--password` with no value makes the tool ask you, so the password is never written in the command.
```
mysqldump --host=localhost --port=3307 --user=cafe_app --password --single-transaction --routines smart_cafeteria > D:\backups\smart_cafeteria_YYYYMMDD_HHMM.sql
```
- Keep the last 7 daily copies and one copy on a different drive or machine.
- Check the file is not empty (`Get-Item` shows its size, a few kilobytes or more) and that it ends with a
  "Dump completed" line.

### Restoring, and proving it works (do this once before your demonstration and keep the evidence)
Restore into a **different** schema first, so nothing live is overwritten.
1. In Workbench: `CREATE DATABASE smart_cafeteria_restore CHARACTER SET utf8mb4;` and
   `GRANT ALL ON smart_cafeteria_restore.* TO 'cafe_app'@'localhost';`
2. Restore (PowerShell; it asks for the password):
   ```
   cmd /c "mysql --host=localhost --port=3307 --user=cafe_app --password smart_cafeteria_restore < D:\backups\smart_cafeteria_YYYYMMDD_HHMM.sql"
   ```
3. Compare the two schemas in Workbench:
   ```sql
   SELECT (SELECT COUNT(*) FROM smart_cafeteria.orders)          AS orders_live,
          (SELECT COUNT(*) FROM smart_cafeteria_restore.orders)  AS orders_restored,
          (SELECT COUNT(*) FROM smart_cafeteria.credit_entries)         AS ledger_live,
          (SELECT COUNT(*) FROM smart_cafeteria_restore.credit_entries) AS ledger_restored;
   ```
   The pairs must match.
4. To prove the application runs on the restored copy, start it with `DB_URL` pointing at `smart_cafeteria_restore`,
   sign in and open an old order. Record the date, the file used, the time it took, and the result in
   `docs/test-report.md`.
5. To restore for real after a loss: stop the application, restore into the live schema (drop and recreate it empty
   first), start the application, and check as above.

## 6. Printing and replacing QR codes
Print each table's QR code from **Tables and QR codes** at the moment it is created or rotated (it is shown once).
Laminate the print and fix it to the table. Test every printed code with a phone before the demonstration. If a code is
lost or damaged, **Rotate** it and print the new one.

## 7. If the system is unavailable (staff-assisted fallback)
Students order at the counter as before. Staff write the table number and items on a paper slip, take payment as
usual, and enter the orders into the system when it returns, or keep the paper records for the demonstration notes.
Do not ask students for passwords or credit details on paper.

## 8. Routine checks before a demonstration
1. Back up the database (section 5).
2. `mvn clean test` passes, and `mvn clean verify` passes if you have the test schema.
3. Start the application, check `/actuator/health`, sign in as each role.
4. Place a cash order, a credit order and cancel one; confirm the board, the student page and the reports agree.
5. Use only synthetic accounts and data unless permission for real data has been given.
