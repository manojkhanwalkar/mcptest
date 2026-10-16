# Tasks

## 1. Role-driven account generation

- [x] 1.1 In `CreditReportGenerator`, change `paymentHistory` to take the late-streak start and length (-1 for no streak), and have `account()` choose them with today's 1-in-5 rule. Verify `./mvnw test -Dtest=CreditReportCreatorTest` still passes.
- [x] 1.2 Add the private `Role` enum and a `role` parameter to `account()`, and apply the opened date, closed date, streak and minimum balance for each role as in the design.md role table. `NONE` must behave as today. Verify the project compiles and the existing tests pass.
- [x] 1.3 Rework `accounts()` to decide account types first, give `RECENTLY_CLOSED` to a credit card and the other three roles to separate accounts, add credit cards when there are fewer than 4 accounts, then build the accounts and sort them newest first. Verify with the tests in 1.4.
- [x] 1.4 In `CreditReportCreatorTest`, check every requirement in specs/credit-report-generation/spec.md for seeds 0 to 199: an older late streak with "Was past due", an "Open/Past due 30 days" account whose latest code is "30", an account closed 1 to 12 months ago, an open account opened 1 to 6 months ago, consistent closed accounts (zero balance and payments, "Closed…" status, `ND` after the closed date, `onRecordUntil` set) and newest-first order. Verify `./mvnw test` passes.

## 2. Sample data and documentation

- [x] 2.1 Regenerate the sample with `java -cp target/classes com.example.testdata.creditreport.CreditReportCreator test-data/credit-reports 20261015` after `./mvnw -q compile`, and delete the old `credit-report-2416-0737-69.md`. Verify the new file contains a "Past due 30 days" status, a "Closed/" status and a recently opened account, and that `./mvnw -f credit-report-mcp-server/pom.xml test` still passes.
- [x] 2.2 In the README's "Dummy credit report generator" section, say that every report includes late payments (older and current), an account closed in the past year and an account opened in the past 6 months, and that seeds produce different reports than before this change. Verify the README command still regenerates the sample file.
