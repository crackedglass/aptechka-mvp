## Context

The authoritative input is `docs/mvp-tracking-grilling-recap.md`. The repository contains a frontend prototype and an existing backend, but neither overrides the interview decisions. This proposal establishes behavior and an implementation sequence; it does not claim the open implementation choices in the recap were settled. There are no existing main OpenSpec capabilities to modify.

## Goals / Non-Goals

**Goals:** Make role boundaries, occurrence identity, reporting, coordination and history independently testable. Preserve historical instructions and all actors' assertions. Support reliable shared status and the two agreed notification triggers.

**Non-Goals:** All deferred scope in the proposal remains excluded. No mandatory claim before reporting, patient acknowledgment workflow, automatic not-done event, claim expiry, offline reporting or exclusive caregiver task assignment is introduced. This planning change does not select a framework, broker, database schema or final API.

## Decisions

### 1. Separate access relationships from tracking facts

Use conventional account, patient and relationship storage with append-only mutation history. Enforce authorization for commands and queries using current accepted relationships, including after caregiver replacement. Patient identity exists independently of an activated login. Event sourcing the entire account system adds scope that the recap explicitly does not require.

### 2. Keep instruction versions and occurrences distinct

An occurrence identifies one patient-local scheduled activity and references the applicable immutable instruction/schedule version. Medication and procedure instructions have different content but share occurrence lifecycle rules. Do not identify historical occurrences solely by mutable plan fields or the current schedule. Whether occurrences are materialized or deterministically generated remains a detailed-design decision; either strategy must preserve past unreported and future reported occurrences.

Evaluate edits against an explicit effective time and current state. Preview preserved reported exceptions and protected claims before saving, then recheck at commit. Edits can proceed for eligible occurrences without rewriting protected ones. Cancelling a plan stops future eligible activities and preserves history; past unreported occurrences remain available for late reporting. Report-bearing and actively handled occurrences retain the protections in the specs.

### 3. Derive completion from independent positive assertions

Keep one report lineage per occurrence and author, with appended corrections/retractions. Completion is the existence of at least one currently valid assertion; report count is not dose count. Store actual care time separately from submission time. Pending and overdue are derived from the absence of a valid assertion and the scheduled instant, not emitted not-done facts.

| Input | Result |
| --- | --- |
| Valid report on an open occurrence | Completed; active claim closes |
| Another actor confirms the same care after acknowledgment | Another report lineage; still one completed occurrence |
| Author corrects their report with a reason | New immutable revision; original retained |
| Author retracts while another valid assertion remains | Completed |
| Author retracts the final valid assertion | Pending or overdue; manager notified |
| Ordinary report on cancelled occurrence | Rejected until manager reopens |

Cancelled/open lifecycle, completion and handling are separate dimensions. Reopening removes the cancellation restriction; it does not manufacture a completion report. Claim closure caused by a report must remain reconstructable even if that report is later retracted; retraction alone does not recreate a previous claim.

### 4. Make coordination checks authoritative at mutation time

At most one active claim can exist. Takeover requires an explicit action and reason and never implies completion. A manager cannot bypass a claim by editing or cancelling. The release/takeover sequence for cancellation and caregiver removal remains to be specified before those commands are implemented.

Submission must re-evaluate current completion and authorization, not trust the screen the actor previously viewed. If another completion appeared, require acknowledgment of the same care before accepting the new assertion. Concurrent claim attempts cannot both succeed. Retries must preserve one logical submission. These are required outcomes; transaction boundaries, revision tokens, conflict responses and idempotency-key details are implementation decisions to resolve in task group 1.

### 5. Use event sourcing for the tracking core and role-filtered projections

Persist immutable plan, occurrence lifecycle, reporting and handling facts; derive tracking state and retain sufficient versioned data to replay it. Exact aggregates, streams, event envelopes, occurrence-generation facts and schema evolution remain open. Mutable rows plus a superficial audit trail alone would not meet the event-sourcing requirement.

Separate shared status (completion time and claim holder/time) from report details. Manager and activated patient can read all care reports/corrections; caregiver queries return only their own detailed reports plus shared status. Enforce this in backend responses rather than client-side hiding. Define the displayed completion-time policy before implementing shared projections.

### 6. Keep manager notification delivery durable and narrow

Create persistent inbox entries for previous-patient-local-day report submissions and transitions caused by last-valid-report retraction. Keep read/unread state across sessions. Use durable processing and deduplication so retries/replay do not lose or duplicate logical notifications. The specific transaction/outbox or other delivery mechanism is open; no external delivery channel or generic reminder engine is required. Read/unread mutations are changes to inbox state, not read-access auditing.

## Risks / Trade-offs

- Concurrent reporting/editing or stale screens → Recheck invariants at commit and exercise races in integration tests.
- Replay changes history or duplicates notifications → Preserve original facts and versions; test projection rebuild and delivery deduplication.
- Caregiver information leakage → Separate shared-status responses from author-scoped history and test denied reads as well as denied writes.
- Time-zone changes or DST alter occurrence identity → Resolve policies before calendar generation; preserve existing historical occurrence references.
- Indefinite claims block changes → Show holder/time and support explicit resolution; do not invent automatic expiry.
- Existing backend/prototype semantics conflict with requirements → Inventory compatibility before reuse, and test against the recap scenarios.
- Design choices remain open → Complete task group 1 before dependent implementation; artifact readiness does not mean those choices have already been approved.

## Migration Plan

1. Inventory existing care, course, authentication, notification and persistence behavior against these specs, including whether any deployed data needs migration.
2. Resolve the open contract and persistence decisions below, recording them in this design and updating specs only where new product decisions are necessary.
3. Implement and verify the tracking model and role-filtered APIs before connecting prototype workflows.
4. If migration is needed, define source mappings, preserve known authorship/timestamps/history and explicitly identify missing provenance rather than inventing care facts. Rehearse migration and projection rebuild on representative data.
5. Roll out with a documented recovery checkpoint and tested rollback procedure. Do not roll back to a writer that destroys or misinterprets newly recorded facts; retain the event history and restore a compatible reader/writer or suspend writes during recovery.

## Open Questions

Resolve the following during the first implementation tasks, before building the dependent behavior. They are not silently approved defaults:

- Authentication, invitation transport/expiry, patient activation and recovery.
- Event-store technology, aggregates/streams, event payloads/schema evolution, and whether occurrence generation or claim closure needs a dedicated event.
- API contracts, errors, authorization integration, transaction boundaries, idempotency and concurrency controls.
- Occurrence generation/persistence horizon; DST ambiguity/nonexistence and patient time-zone changes.
- Which completion time shared status displays when valid reports disagree.
- Allowed correction fields, reinstatement after retraction, and any backdating/early-care limits beyond rejecting future actual times.
- Manager claim-release authority and precise release/takeover sequence for cancellation or caregiver replacement.
- Shared-status refresh/reconnection mechanism; offline reporting remains outside agreed scope.
- Retention, account deletion, export and operational recovery policies, without assuming new export functionality is part of MVP.
- Backend reuse and prototype-data migration requirements.
