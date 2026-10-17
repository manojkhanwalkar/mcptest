# Tasks

## 1. Command-line tool

- [x] 1.1 Add `RecentlyClosedAccountAlerts` in `com.example.testdata.creditreport` with `hasRecentlyClosedAccount`, `find` and `main` as described in design.md (decisions 2 to 4). Verify `./mvnw -q compile` succeeds.
- [x] 1.2 Add `RecentlyClosedAccountAlertsTest` covering every scenario in specs/credit-report-alerts/spec.md: closed within the year, only older closings, the 12-month boundary, file-name order, non-`.md` files and subdirectories ignored, a non-report `.md` file warned about and skipped, and a generated report (via `CreditReportCreator.create`) qualifying. Verify `./mvnw test` passes.

## 2. Documentation and check against sample data

- [x] 2.1 Add a README section after "Dummy credit report generator" showing how to run the tool and what it prints. Verify running the README command against `test-data/credit-reports` prints "ELENA C MOCKRIDGE".
