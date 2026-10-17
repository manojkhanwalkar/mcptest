# Design

## Context

See proposal.md (Why) for the motivation and specs/credit-report-generation/spec.md for the guarantees. Today `CreditReportGenerator` works like this:

- `accounts()` always picks 2 to 5 credit cards, then adds auto, mortgage, student, personal and home-equity accounts at random. It sorts the list by opened date, newest first.
- `account(type, historyStart)` draws the opened date from `historyStart` up to 4 months before the report date. An account closes only if it is an installment loan past its term, or (1 in 6) a revolving account, closed at a random date between 3 months after opening and 1 month before the report date.
- `paymentHistory(opened, closed)` gives about 1 in 5 accounts a late streak of 1 to 3 months starting 2 to 21 months back. Because the streak never starts at month 0, no account is ever past due in the latest month, so "Open/Past due …" never appears.
- `status(...)` and the `onRecordUntil` logic already produce the right text and dates for closed and late accounts. `MarkdownCreditReportWriter` already displays all of these cases.

## Goals / Non-Goals

**Goals:**
- Meet the four guarantees in every report while keeping everything else random and reproducible from the seed.
- Keep the existing consistency rules (zero balance when closed, `ND` after closing, status text, `onRecordUntil`) by reusing `account()` instead of building forced accounts separately.

**Non-Goals:**
- Changing the `CreditReport` model, `MarkdownCreditReportWriter` or the command-line interface.
- Making other sections of the report match the forced accounts, for example adding a hard inquiry for the recently opened account.
- Keeping the reports old seeds produced.

## Decisions

### 1. Give chosen accounts a role and pass it into `account()`

Add a private enum, roughly `Role { NONE, PAST_LATE, CURRENTLY_PAST_DUE, RECENTLY_CLOSED, RECENTLY_OPENED }`. `account(type, historyStart, role)` uses the role to set the inputs it already works with: the opened date, the closed date and the late streak. Balance, status, `onRecordUntil` and the creditor details then follow from the existing code.

| Role | Opened | Closed | Late streak | Other |
|---|---|---|---|---|
| `RECENTLY_CLOSED` | random, at least 3 months before the closed date | random date 1 to 12 months before the report date | random, as today | given only to a credit card, so it shows as "Closed/…" |
| `RECENTLY_OPENED` | random date 1 to 6 months before the report date | never (skip the random revolving closure) | none | |
| `CURRENTLY_PAST_DUE` | as today (at least 4 months back) | never | starts at month 0, length 1, so the latest month is `LATE_30` | revolving balance at least 10% of the limit, so a past-due account owes money |
| `PAST_LATE` | as today, but a loan is kept within its term | never (skip the random revolving closure) | starts 2 or more months back and fits inside the account's history | stays open so the streak can't be hidden by `ND` months after an old closing |
| `NONE` | current behavior | current behavior | current behavior | |

*Alternative considered:* first generate the accounts, then check them and fix up whatever is missing. Rejected because fixing up a finished record means recomputing balance, status, history and `onRecordUntil` together, which duplicates `account()` and makes inconsistent records easy to create.

### 2. Assign each role to a different account, adding accounts only when there aren't enough

In `accounts()`, first decide the account types as today, without building the accounts. Then:
1. Give `RECENTLY_CLOSED` to one of the credit cards, chosen at random. There are always at least 2.
2. Give `RECENTLY_OPENED`, `CURRENTLY_PAST_DUE` and `PAST_LATE` to other accounts, chosen at random from those left.
3. If fewer than 4 accounts were picked, add credit cards until each role has its own account. With the current minimum of 2 accounts, at most 2 are added.
4. Build the accounts with their roles, then sort by opened date as today.

Separate accounts keep each scenario clear when reading a report, and the role table stays simple. For example, a past-due account opened 2 months ago would look odd.

*Alternative considered:* always add four extra accounts. Rejected because it inflates every report and makes the base distribution less realistic.

### 3. Put the streak position in `paymentHistory()`'s parameters

Change `paymentHistory(opened, closed)` so the caller passes the streak start and length, with -1 meaning no streak. `account()` chooses them from the role: today's random 1-in-5 rule for `NONE` and `RECENTLY_CLOSED`, forced for `PAST_LATE` and `CURRENTLY_PAST_DUE`, and none for `RECENTLY_OPENED`. For `PAST_LATE` the start is limited to the number of months the account has existed, so the streak shows up. Its opened date is at least 4 months back, so there is always room for a streak starting at month 2.

### 4. Lower the 4-month floor only for `RECENTLY_OPENED`

Other accounts keep the "at least 4 months old" rule, so the rest of the generated data doesn't change.

## Risks / Trade-offs

- [Every seed now produces a different report] → Expected and stated in the proposal. `sameSeedProducesSameReport` still holds. The sample file is regenerated, and the old file is deleted because its name comes from the report number.
- [A closed `PAST_LATE` account (for example a loan paid off years ago) would show only `ND` months, hiding the streak] → `PAST_LATE` accounts stay open, like `CURRENTLY_PAST_DUE` and `RECENTLY_OPENED`: no random closure, and an installment loan's opened date is redrawn if it would be past its term. The new test checks for a late code across 200 seeds.
- [`CURRENTLY_PAST_DUE` given to an installment loan] → Fine. Its balance is already positive while open, and `status()` already produces "Open/Past due 30 days".
- [Hard inquiries do not line up with the recently opened account] → Left out on purpose (see Non-Goals). The data is fictitious test data.
