## 1. Resolve detailed design before dependent implementation

- [x] 1.1 Inventory frontend/backend behavior against the six specs; record reuse candidates, incompatible semantics and whether persisted prototype data needs migration.
- [x] 1.2 Define authentication, invitation acceptance/delivery/expiry, patient activation and recovery contracts without adding external notification delivery scope.
- [x] 1.3 Define tracking aggregate/stream boundaries, event payloads and schema evolution; choose persistence and document replay and transaction guarantees.
- [x] 1.4 Define occurrence identity, generation horizon, persistence, DST behavior and patient-time-zone changes with examples preserving historical occurrences.
- [x] 1.5 Resolve displayed completion time for multiple reports, correction fields, reinstatement and any early/backdated-care limits; record product decisions explicitly.
- [x] 1.6 Define claim transitions and manager release/takeover sequences for edits, cancellation and caregiver replacement, including concurrent completion.
- [x] 1.7 Define API contracts and role-filtered responses, errors, idempotency, concurrency checks and shared-status refresh/reconnection behavior.
- [x] 1.8 Define durable notification processing/deduplication and history retention, deletion and recovery policies; document export policy without adding an export feature.
- [x] 1.9 Update design and affected acceptance scenarios with the resolved decisions; confirm no deferred scope was introduced before starting dependent implementation.

## 2. Identity, relationships and authorization

- [x] 2.1 Implement distinct patient identity and explicit time zone, optional login and MVP relationship cardinality; verify tracking before invitations.
- [x] 2.2 Implement manager-controlled invitations and acceptance/activation with no access from pending invitations.
- [x] 2.3 Enforce command permissions and current relationship checks, including denial for stale sessions after revocation.
- [x] 2.4 Implement role-filtered queries: manager/patient full care history, caregiver own history plus shared status; test cross-author and unrelated-patient access denial.

## 3. Tracking persistence and plans

- [x] 3.1 Implement immutable tracking fact persistence and replay using the resolved event contract; verify append and retry behavior.
- [x] 3.2 Implement conventional account/patient/access mutation logs with actor, time and reconstructable prior state, excluding secrets.
- [x] 3.3 Implement medication and procedure instruction validation and versioned one-off/weekday schedules with date bounds and multiple times.
- [x] 3.4 Implement stable occurrence generation/querying with historical version references and patient-local day selection; test the resolved time-zone edge cases.
- [x] 3.5 Implement effective edit preview/save preserving past unreported and future reported occurrences; recheck protections at commit.

## 4. Handling and completion

- [x] 4.1 Implement optional claim, own release and reasoned takeover with immutable history and no expiry; test concurrent claim attempts.
- [x] 4.2 Implement full-completion reports with separate author/patient and actual/submission timestamps; reject future actual times and remove the one-hour restriction.
- [x] 4.3 Enforce one report lineage per author/occurrence and retry idempotency; derive one completed occurrence from any number of valid independent reports.
- [x] 4.4 Implement shared-status recheck and same-care acknowledgment; test a second actor completing between view and submission.
- [x] 4.5 Close active claims on accepted completion by any actor and verify claim/report history survives replay.
- [x] 4.6 Implement author-only appended corrections/retractions with mandatory reasons; derive pending/overdue when the last assertion is retracted.
- [x] 4.7 Implement cancellation and manager reopening, including protected claimed occurrences, reported exceptions and ordinary-report rejection while cancelled.
- [x] 4.8 Implement caregiver replacement using the resolved claim-resolution sequence; preserve history and revoke previous access without overlap.

## 5. Manager inbox and shared projections

- [x] 5.1 Implement persistent inbox entries and read/unread mutations with change history and manager authorization.
- [x] 5.2 Generate notifications for previous-patient-local-day report submissions and last-valid-report retractions; verify same-day reports and nonfinal retractions do not trigger them.
- [x] 5.3 Verify notification recovery and deduplication after retries/interruption/replay without resetting read state.
- [x] 5.4 Implement shared completion/handling projections and the resolved refresh/reconnection mechanism without leaking detailed reports to caregivers.

## 6. Frontend integration

- [x] 6.1 Connect patient setup, invitations/activation and role-aware navigation to the access contracts.
- [x] 6.2 Connect manual instruction editors and schedule edit previews, showing preserved reported exceptions and protected active handling.
- [x] 6.3 Connect today's default tasks and historical day navigation to stable occurrence references and pending/overdue/completed state.
- [x] 6.4 Connect full reporting, actual-care-time entry, already-complete acknowledgment and author-only correction/retraction with reasons.
- [x] 6.5 Connect claim/release/takeover and manager cancellation/reopening/replacement flows, showing handler and claim time.
- [x] 6.6 Connect manager inbox read/unread state and role-filtered report history; remove conflicting tracking semantics from integrated MVP flows.

## 7. Acceptance and rollout readiness

- [x] 7.1 Verify recap scenarios 1–6 end to end: manager-only start, caregiver visibility, independent reports, nonfinal/final retractions and previous-day reporting.
- [x] 7.2 Verify recap scenarios 7–12 end to end: concurrent claims, completion by a nonholder, protected schedule edits, cancellation/reopening, caregiver replacement and retry idempotency.
- [x] 7.3 Verify privacy, cross-author denial, commit-time races, time-zone boundaries and all additional scenarios in the six capability specs for both medication and procedure tracking.
- [x] 7.4 Rebuild tracking projections and verify historical versions, report/correction authorship, handling closure and notification durability against persisted facts.
- [x] 7.5 If migration is needed, implement and rehearse mappings and reconciliation without fabricated care facts; otherwise record why migration is unnecessary.
- [x] 7.6 Document and rehearse deployment/recovery/rollback with preserved event history, run applicable project checks and OpenSpec validation, and record results.
