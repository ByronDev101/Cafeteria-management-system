# Test report

Project: Smart Cafeteria Table Ordering, Payment and Student Credit Management System
Student: Byron Mathews Muosi (25/02782) | Supervisor: Beatrice Kagucia | Course: STU 3101

Complete the **bracketed** fields. Everything else is already known. Keep screenshots and saved Maven output as the
evidence and refer to them here.

## 1. Test environment
| Item | Value |
|---|---|
| Date of final run | [date] |
| Operating system | [Windows version] |
| Java | [`java -version`] |
| Maven | [`mvn -v`] |
| Spring Boot | 3.5.6 |
| MySQL | [`SELECT VERSION();` from the development server and from the test schema] |
| Browsers | [Chrome version], [Firefox version], [phone model and browser] |

## 2. Automated tests
| Suite | Command | Result | Evidence |
|---|---|---|---|
| Unit and web-layer tests | `mvn clean test` | [Tests run: ___, Failures: 0, Errors: 0] | [saved output] |
| Database integration tests | `mvn clean verify` | [29 tests, Failures: 0, Errors: 0] | [saved output, MySQL version] |

What the integration tests prove: the nine migrations build the schema from nothing; MySQL refuses bad data by itself;
a failed order leaves no trace; payment and refund flows agree with the database; manager report figures are right at
campus-day boundaries; simultaneous credit orders and payments cannot exceed a limit or an amount owed. See
`docs/integration-tests.md`.

## 3. Defects found and fixed
Record the real defects found by testing. These are worth presenting: they show the testing found problems that
ordinary tests could not.

| ID | Found by | What was wrong | Cause | Fix | Evidence |
|---|---|---|---|---|---|
| D-01 | `CreditConcurrencyIT` (first database run) | With 8 simultaneous credit orders and room for 3, all 8 were accepted: the credit limit could be exceeded. Sequential orders were handled correctly. | MySQL's default isolation level (REPEATABLE READ) gives a transaction a snapshot taken at its first read. After waiting for the credit-account lock, the ledger sum still read the old snapshot and missed lines committed meanwhile. | The credit check now reads the latest committed data (see D-02 for the final fix). | [saved output of the failing run] |
| D-02 | `CreditConcurrencyIT` (second run) | A first fix that locked the ledger rows caused "Deadlock found" between different students' simultaneous orders. | The locking read took gap locks on empty index ranges; two students' inserts then waited for each other. | Order placement and recording a payment run at READ COMMITTED, with the account row still locked. No gap locks, no deadlock, correct totals. | [saved output of the deadlock and of the passing run] |
| D-03 | `mvn test` (Sprint 5 credit) | A test written before credit existed expected the order service to refuse `CREDIT` outright; credit is now accepted and refused by the credit service when there is no credit. | Outdated test, not a code defect. | Test updated. | [saved output] |

Add any further defects you find during manual testing and the user trial.

## 4. Manual test record
Mark each check Pass or Fail with the date. Sprint check lists were given with each delivery; the key ones are below.

| ID | Requirement | Steps | Expected | Result | Date |
|---|---|---|---|---|---|
| M-01 | FR-004 | Sign in, press logout; sign in and leave idle past the timeout (or lower it in config) | Logged out; protected pages need sign-in | [ ] | |
| M-02 | FR-005 | As a student, change an order id in the address to another student's | "Not found", no details | [ ] | |
| M-03 | FR-010 | Scan a printed QR code with a phone | Opens the right table | [ ] | |
| M-04 | FR-022 | Place an order; as staff press Accept; watch the student's phone | Status updates without manual work | [ ] | |
| M-05 | FR-023 to FR-024 | Cash order: mark payment received; cancel; mark refund paid | Statuses and audit entries as expected | [ ] | |
| M-06 | FR-025 to FR-031 | Set a limit; credit order; accept; cancel; record a payment | Held, charged, reversed, paid figures correct | [ ] | |
| M-07 | FR-034 to FR-036 | Open Reports; change dates; search by status, number, table | Figures match what you did | [ ] | |
| M-08 | NFR-03 | Over HTTPS (profile `prod`) inspect the session cookie and the response headers | Secure, HttpOnly, SameSite; security headers present | [ ] | |
| M-09 | NFR-04 | Read the application log after a day of testing | No passwords or session ids | [ ] | |

## 5. Backup and restore record (NFR-06, NFR-12)
Follow `docs/deployment-and-operations.md` section 5.
| Item | Value |
|---|---|
| Date and time of the backup | [ ] |
| Backup file name and size | [ ] |
| Order count live / restored | [ ] / [ ] |
| Ledger line count live / restored | [ ] / [ ] |
| Application started on the restored copy, old order opened | [Yes/No] |
| Time taken from "start" to "working" | [ ] minutes (target: under 4 hours) |

## 6. Usability trial (NFR-07)
Run with at least 5 first-time student volunteers using **synthetic accounts**, after a brief orientation (2 minutes).
Do not help them. Record each.

| Tester | Scanned the QR | Signed in | Added items | Placed the order | Time to finish | Problems seen |
|---|---|---|---|---|---|---|
| T1 | [ ] | [ ] | [ ] | [ ] | [ ] min | |
| T2 | | | | | | |
| T3 | | | | | | |
| T4 | | | | | | |
| T5 | | | | | | |

Summary: [how many finished unaided; the three most common problems; what you changed because of them].
Get verbal consent and tell testers no personal data is collected beyond what they say.

## 7. Accessibility checks (NFR-08)
| Check | Page(s) | Result |
|---|---|---|
| Automated scan (for example browser Lighthouse or axe) | Login, menu, cart, order, order board, reports | [score / issues, saved report] |
| Keyboard only: reach and use every control, visible focus | Same pages | [ ] |
| Every form field has a label | Same pages | [ ] |
| Text contrast readable (check status pills and buttons) | Same pages | [ ] |
| Zoom to 200 percent keeps the page usable | Same pages | [ ] |
Record the issues found and fixed.

## 8. Browser and device matrix (NFR-09)
Core workflow: scan or open the table page, sign in, add items, place an order, watch its status; and as staff, accept it.
| Browser / device | Student workflow | Staff workflow | Notes |
|---|---|---|---|
| Chrome (desktop) | [ ] | [ ] | |
| Firefox (desktop) | [ ] | [ ] | |
| [Android or iOS browser] | [ ] | [ ] | |

## 9. Performance (NFR-01) - optional but listed in the SRS
Record how you measured it (tool, number of virtual users, pages and order submission), the 95th percentile times, and
the machine used. If not done, say so plainly in the report and list it as a limitation.

## 10. Sign-off
| Role | Name | Date |
|---|---|---|
| Student | Byron Mathews Muosi | [ ] |
| Supervisor | Beatrice Kagucia | [ ] |
