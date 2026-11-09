# Security notes

What the system does to protect people and data, where each control lives, and what it does **not** do. Written for
the supervisor and for anyone reviewing the project. It describes a prototype handling synthetic data.

## 1. Controls in place

| Area | Control | Where |
|---|---|---|
| Passwords | Stored as BCrypt hashes; length and strength rules; maximum 72 characters (BCrypt's limit). | `SecurityConfig`, `PasswordPolicy` |
| New accounts | Created by an administrator with a one-time temporary password; the person must change it at first sign-in. Self-registration is not enabled. | `UserAdminService`, `MustChangePasswordFilter` |
| Guessing passwords | An account locks after 5 wrong passwords; an administrator unlocks it. Separately, 10 failed sign-ins from one address in 15 minutes pause sign-in from that address. A successful sign-in never resets that count. | `AppUser`, `LoginThrottle`, `LoginThrottleFilter` |
| Roles | Four roles. URL rules in `SecurityConfig` and a second check inside the services (`StaffGuard`, `ManagerGuard`), so a mistake in one place does not expose data. | `SecurityConfig`, `StaffGuard`, `ManagerGuard` |
| Other people's data | A student asking for another student's order gets "not found". Student pages take the student from the signed-in session, never from the request. | `OrderService`, student controllers |
| Sessions | HttpOnly and SameSite cookies, Secure in production, 30-minute timeout, session id never in the address. | `application.properties`, `application-prod.properties` |
| CSRF | Enabled for every state-changing request. | `SecurityConfig` |
| Browser protection | Strict Content-Security-Policy (own files only, no framing), `Referrer-Policy: no-referrer`, `X-Content-Type-Options`, `Permissions-Policy`, HSTS over HTTPS. | `SecurityConfig` |
| HTTPS | Profiles `prod` and `behind-proxy` require HTTPS and redirect plain HTTP. | `docs/https-and-limits.md` |
| Prices | Always taken from the menu on the server; orders keep a copy of names and prices at the time. | `CartService`, `OrderService` |
| QR codes | Random, non-sequential tokens; only a hash is stored; shown once; can be rotated; unknown, malformed or inactive tokens give a plain error. | `TableToken`, `TableService` |
| Abuse | At most 5 orders per student in 10 minutes. | `OrderRateLimiter` |
| Money records | Payment and credit records are labelled prototype. A payment can only move along agreed steps. Credit holds, charges, reversals and payments are lines added to a ledger that the application never edits or deletes. | `PaymentStatus`, `CreditEntry` |
| Credit integrity | An order's credit hold, the order and its payment record succeed or fail together. The credit account is locked while a hold is placed, and the check runs at READ COMMITTED so simultaneous orders cannot exceed a limit. Proved by `CreditConcurrencyIT`. | `CreditService`, `OrderService.place` |
| Database | Queries use the ORM and bound parameters; search text is cleaned and cannot act as a wildcard. MySQL itself refuses negative amounts, a second payment per order, and bad ledger lines. | `ReportQueriesIT`, `SchemaAndConstraintsIT` |
| Audit | Account, role, menu, price, table, order, payment and credit actions record who, what, when and the outcome. The log has no edit or delete in the application. | `AuditService` |
| Errors | Error pages show no stack traces, class names or role details. | the 403 and 404 pages |
| Privacy | Only data needed for accounts and orders. Demonstrations use synthetic accounts. Logs do not hold passwords. | policy; review before demo |

## 2. Known limits (be ready to state these)
1. **Rate limits are in memory.** They reset when the application restarts and are counted per running copy. Fine for
   one demo server; a larger deployment would need a shared store.
2. **The ledger is append-only in the application, not in the database.** There are no database triggers, so someone
   with direct database access could change rows. Production use would add triggers or database privileges that
   forbid updates and deletes.
3. **No second factor** and no password-breach checking.
4. **Demo accounts.** In the `dev` profile four demo accounts share one password. A default exists in the code
   (`ChangeMe123!`) for development only. Set `APP_DEMO_PASSWORD` to your own value, never use the `dev` profile on a
   public server, and deactivate demo accounts after use.
5. **First administrator.** The `prod` profile creates no accounts. See `docs/deployment-and-operations.md`.
6. **Many students on one address** (a campus network) share the sign-in limit.
7. **No external payment or banking integration.** By design and by the proposal, nothing real is charged.
8. **Automated scanning.** No dependency vulnerability scan or penetration test has been run. Run
   `mvn org.owasp:dependency-check-maven:check` (or your institution's scanner) and attach the result if required.
9. **Accessibility and browser compatibility** are checked by people, not by the test suite; see the test report.

## 3. Suggested checks for a reviewer
1. As a student, change an order number in the address to another student's: you get "not found".
2. As a student, open `/manager/reports`, `/staff/orders`, `/admin/home`: each is refused.
3. Sign in wrongly ten times from one device: sign-in pauses there with a plain message.
4. Look at a response in the browser's developer tools: the security headers are present.
5. Open the audit log after a payment, a price change and a credit limit change: each shows who did it.
6. Run `mvn clean verify` and read `CreditConcurrencyIT`: simultaneous orders never exceed a credit limit.
