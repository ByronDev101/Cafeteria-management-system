# Requirements traceability

Every requirement in the SRS (FR-001 to FR-037 and NFR-01 to NFR-12), where it is built, which automated tests check it,
and what evidence still has to come from a person. "Unit" means `mvn test`; "IT" means the database tests in
`mvn verify` (see `docs/integration-tests.md`). Test names are classes in `src/test/java`.

> Test classes for the earlier sprints (accounts, menu, tables, cart, ordering) were matched to requirements from the
> class and feature names. Before submitting, open any row you will rely on and confirm the named test really checks
> that requirement; correct the row if not.

**Status key:** Built = implemented and covered by the tests named. Manual = also needs a recorded manual check in
`docs/test-report.md`. Gap = not yet evidenced.

## Functional requirements

| ID | Requirement (short) | Where built | Automated tests | Status |
|---|---|---|---|---|
| FR-001 | Sign in with account and password | `SecurityConfig`, `AppUserDetailsService`, login page | `SecurityBaselineTest`, `AccessRulesTest`, `AppUserTest`, `MustChangePasswordFilterTest` | Built |
| FR-002 | Role-based permissions (4 roles) | URL rules, `StaffGuard`, `ManagerGuard`, role redirect | `AccessRulesTest`, `RoleAwareSuccessHandlerTest`, `ManagerGuardTest`, controller tests | Built |
| FR-003 | Admin creates, activates, deactivates, assigns roles; self-registration grants no privilege | `UserAdminService`, Accounts pages (self-registration not enabled) | `UserAdminServiceTest`, `AdminUserControllerTest`, `TemporaryPasswordTest`, `PasswordPolicyTest` | Built |
| FR-004 | Log out; sessions expire (30 min) | Logout, `server.servlet.session.timeout=30m` | none specific | Manual (sign out; wait, or lower the timeout and check) |
| FR-005 | No access to another student's order, payment or credit | Order lookups by owner, session-based student pages | `OrderServiceTest`, `StudentOrderControllerTest`, `StudentCreditControllerTest` | Built, Manual (change the order id in the address) |
| FR-006 | Show active categories and available items | Student menu | `StudentMenuControllerTest`, `MenuServiceTest`, `MenuLookupTest` | Built |
| FR-007 | Manager manages categories, items, availability | Manage menu | `ManagerMenuControllerTest`, `MenuServiceTest`, `ImageProcessorTest`, `MenuImageControllerTest` | Built |
| FR-008 | Prices in decimal, KSh | Decimal columns, `PriceParser` | `PriceParserTest`, `SchemaAndConstraintsIT` | Built |
| FR-009 | Admin manages tables | Tables and QR codes | `AdminTableControllerTest`, `TableServiceTest` | Built |
| FR-010 | Unique non-sequential QR token resolves to a table | `TableToken` (hashed), `QrCodeImage` | `TableTokenTest`, `QrCodeImageTest`, `TableServiceTest` | Built, Manual (scan a printed code) |
| FR-011 | Reject bad tokens; no sequential ids in URLs | `/t/{token}` handling | `StudentTableControllerTest`, `ScanAfterLoginTest`, `TableTokenTest` | Built |
| FR-012 | Cart: add, change, remove | Cart pages | `CartTest`, `CartServiceTest`, `StudentCartControllerTest` | Built |
| FR-013 | Prices recalculated on the server | `CartService`, `OrderService` | `CartServiceTest`, `OrderServiceTest` | Built |
| FR-014 | Order needs signed-in student, active table, available item | `OrderService.place` | `OrderServiceTest` | Built |
| FR-015 | Order stores number, student, table, time, snapshots, total, method, status | `CustomerOrder`, `OrderService` | `OrderServiceTest`, `OrderNumberAndPaymentTest`, `PaymentFlowIT` | Built |
| FR-016 | Unavailable item flagged, cart kept | `OrderService`, cart page | `OrderServiceTest` | Built |
| FR-017 | Student sees status and history of own orders | My orders | `StudentOrderControllerTest`, `OrderServiceTest` | Built |
| FR-018 | No edits after acceptance; cancel before acceptance | `OrderService.cancelOwn` | `OrderServiceTest`, `CreditLedgerIT` | Built |
| FR-019 | Staff queue with order, time, table, items, payment, status | Order board | `OrderProcessingServiceTest`, `StaffOrderControllerTest`, `PaymentFlowIT` | Built |
| FR-020 | Valid status transitions; logged cancellation | `OrderStatus`, `OrderProcessingService` | `OrderStatusTest`, `OrderProcessingServiceTest` | Built |
| FR-021 | Who and when for each change; refused moves change nothing | Order history, audit | `OrderProcessingServiceTest`, `PaymentFlowIT` | Built |
| FR-022 | Students see changes after refresh or polling | Status endpoint and script | `StudentOrderControllerTest`, `StaffOrderControllerTest` | Manual (watch a status change on a phone) |
| FR-023 | Offline payment record (method, amount, status, time, reference) | `PaymentRecord`, V8 | `PaymentRecordTest`, `PaymentServiceTest`, `PaymentStatusTest`, `PaymentFlowIT`, `SchemaAndConstraintsIT` | Built |
| FR-024 | Only staff or manager verifies a payment; audited | `PaymentService.verify` | `PaymentServiceTest`, `StaffOrderControllerTest`, `PaymentFlowIT` | Built |
| FR-025 | Manager approves a credit account and sets its limit | `CreditService.setLimit`, Student credit page | `CreditServiceTest`, `ManagerCreditControllerTest`, `CreditLedgerIT` | Built |
| FR-026 | Credit order checks account and available credit | `CreditService.reserve` | `CreditServiceTest`, `CreditLedgerIT` | Built |
| FR-027 | Posting, balance and order commitment are atomic | One transaction in `OrderService.place` | `CreditLedgerIT` (an over-limit order leaves no order, payment, ledger or history row) | Built |
| FR-028 | Concurrent requests cannot exceed the limit | Account lock plus READ COMMITTED | `CreditConcurrencyIT` | Built, depends on `mvn verify` passing |
| FR-029 | Append-only credit history | `CreditEntry` (immutable), no update or delete | `CreditLedgerTypesTest`, `CreditLedgerIT`, `SchemaAndConstraintsIT` | Built (see limit 2 in the security notes) |
| FR-030 | Cancelling a credit order reverses the hold or charge | `releaseOnCancellation`, `settleOnCancellation` | `CreditServiceTest`, `PaymentServiceTest`, `CreditLedgerIT` | Built |
| FR-031 | Student sees own limit, balance, available, history; cannot change them | My credit | `StudentCreditControllerTest`, `CreditServiceTest` | Built |
| FR-032 | Payment records identified as prototype | `PaymentRecord.prototype`, screen notes | `PaymentRecordTest`, `PaymentViewTest`, `PaymentFlowIT` | Built |
| FR-033 | Manage account status and roles per the permission matrix | Accounts pages | `UserAdminServiceTest`, `AdminUserControllerTest` | Built |
| FR-034 | Order and payment totals over a date range | Reports | `ReportServiceTest`, `ReportRangeTest`, `ReportQueriesIT`, `ManagerReportControllerTest` | Built |
| FR-035 | Outstanding credit balances and transaction summaries | Reports credit section, Student credit | `ReportServiceTest`, `CreditServiceTest`, `ManagerCreditControllerTest`, `CreditLedgerIT` | Built |
| FR-036 | Search by order number, status, date range, table | Reports search | `ReportServiceTest`, `ReportQueriesIT` | Built |
| FR-037 | Audit of role, price, credit-limit and payment-verification changes | `AuditService` | `UserAdminServiceTest`, `MenuServiceTest`, `CreditServiceTest`, `PaymentServiceTest`, `PaymentFlowIT`, `CreditLedgerIT` | Built |

## Non-functional requirements

| ID | Requirement (short) | How it is met | Automated evidence | Status |
|---|---|---|---|---|
| NFR-01 | 50 concurrent users: reads under 2 s, orders under 3 s | Not yet measured | none | **Gap**: run a simple load check (for example 50 virtual users on menu and order submission) and record it |
| NFR-02 | Adaptive password hash; server-side role checks; parameterised SQL | BCrypt, guards, JPA | `PasswordPolicyTest`, `AccessRulesTest`, `ReportQueriesIT` (hostile search text) | Built |
| NFR-03 | HTTPS, HttpOnly, Secure, SameSite cookies, CSRF | `prod` profiles, `SecurityConfig` | `HttpsRequiredTest`, `SecurityHardeningTest`, CSRF cases in controller tests | Built, Manual (inspect the cookie over HTTPS) |
| NFR-04 | Privacy: needed data only, synthetic demo data, no secrets in logs | Policy, demo seeding | none | Manual (review logs and demo data) |
| NFR-05 | No partial orders or credit postings | One transaction per order | `CreditLedgerIT`, `CreditConcurrencyIT` | Built |
| NFR-06 | Backup before release; restore within 4 hours | `docs/deployment-and-operations.md` | none | **Manual**: perform and record one backup and restore |
| NFR-07 | First-time student completes an order unaided | Guided cart and table flow | none | **Manual**: user trial (template in the test report) |
| NFR-08 | WCAG 2.1 AA practices | Labels, focus, semantic HTML, contrast in the design | none | **Manual**: automated checker plus keyboard check |
| NFR-09 | Chrome, Firefox and a mobile browser | Responsive Bootstrap pages | none | **Manual**: browser matrix |
| NFR-10 | Separated layers; migrations; documented configuration | Package layout, Flyway V1 to V9, `README.md`, `docs/` | build and tests | Built |
| NFR-11 | Actor and time on privileged actions | `AuditService`, order history | tests listed under FR-037 and FR-021 | Built |
| NFR-12 | Available during demonstrations; documented restart and backup | `docs/deployment-and-operations.md` | none | Built (documentation); rehearse it |
