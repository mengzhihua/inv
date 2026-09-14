---
name: inv-browser-e2e
description: Run local INV invoice lifecycle and permission checks through the Vue UI.
---

# Environment

Use the repo blueprint for Maven dependency caching, frontend npm install,
and the Spring Boot (:8080) / Vite (:5173) startup commands. Reuse healthy
processes; if Vite's existing process serves stale modules or HMR repeatedly
fails, restart it from frontend and reload the browser before testing.
Backend uses persistent backend/data/inv: take baseline totals and use unique
invoice identities rather than assuming a clean seed database.

## Devin Secrets Needed

- INV_ADMIN_PASSWORD for the local administrator (must match the existing DB).
- INV_OPEN_API_KEY only if separately testing the intended open-API interface.

# Browser flow

Read frontend/src/router/index.js and the relevant view before planning.
Login, then sales requests → invoice query → red information form; purchase
input → deduction; expense; tax; basic master data; system users.

For valid manual purchase/expense fixtures, read
backend/src/main/java/com/inv/common/CheckCodes.java. The amount seed is
totalAmount (NET, two decimal places), not amountWithTax. Use the invoice's
code/number/date and buyer tax number exactly. An incorrectly gross-seeded
code is useful for a negative verification test, not a valid fixture.

Partial red flush reduces remaining NET and TAX separately. Confirm the
information form before flushing; verify the original invoice's remaining
amount via its next red dialog, and compare VAT against the baseline.

Close the selected entity's current period for negative cancellation and
deduction tests, then reopen it. Keep a verified, pending input and an
undelivered current-month paper invoice available for these tests.

Test VIEWER with a separate UI-created account. Check both button visibility
and actual save rejection when write controls are exposed. Directly navigate
to /system/user to test the route guard, then restore the admin session.
Hidden electronic-cancel or risk-reimbursement controls prove UI prevention
only; do not claim a backend rejection was observed without a reachable action.
