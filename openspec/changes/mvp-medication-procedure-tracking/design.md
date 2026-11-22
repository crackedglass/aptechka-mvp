## Context

The authoritative input is `docs/mvp-tracking-grilling-recap.md`. The repository contains a frontend prototype and an existing backend, but neither overrides the interview decisions. The implementation contracts below settle the choices deferred in the recap. There are no existing main OpenSpec capabilities to modify.

## Goals / Non-Goals

**Goals:** Make role boundaries, occurrence identity, reporting, coordination and history independently testable. Preserve historical instructions and all actors' assertions. Support reliable shared status and the two agreed notification triggers.

**Non-Goals:** All deferred scope in the proposal remains excluded. No mandatory claim before reporting, patient acknowledgment workflow, automatic not-done event, claim expiry, offline reporting or exclusive caregiver task assignment is introduced.

## Decisions

### 1. Separate access relationships from tracking facts

Use conventional account, patient and relationship storage with append-only mutation history. Enforce authorization for commands and queries using current accepted relationships, including after caregiver replacement. Patient identity exists independently of an activated login. Event sourcing the entire account system adds scope that the recap explicitly does not require.

### 2. Keep instruction versions and occurrences distinct

An occurrence identifies one patient-local scheduled activity and references the applicable immutable instruction/schedule version. Medication and procedure instructions have different content but share occurrence lifecycle rules. Do not identify historical occurrences solely by mutable plan fields or the current schedule. Occurrences are generated per requested day and snapshotted on mutation. This preserves past unreported slots and future reported exceptions.

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

At most one active claim can exist. Takeover requires an explicit action and reason and never implies completion. A manager cannot bypass a claim by editing or cancelling. The explicit release sequence appears below.

Submission must re-evaluate current completion and authorization, not trust the screen the actor previously viewed. If another completion appeared, require acknowledgment of the same care before accepting the new assertion. Concurrent claim attempts cannot both succeed. Retries must preserve one logical submission. The transaction and HTTP contracts below implement these invariants.

### 5. Use event sourcing for the tracking core and role-filtered projections

Persist immutable plan, occurrence lifecycle, reporting and handling facts; derive tracking state and retain sufficient versioned data to replay it. The tracking stream and event envelope appear below. Mutable rows plus a superficial audit trail alone would not meet the event-sourcing requirement.

Separate shared status (completion time and claim holder/time) from report details. Manager and activated patient can read all care reports/corrections; caregiver queries return only their own detailed reports plus shared status. Enforce this in backend responses rather than client-side hiding. Define the displayed completion-time policy before implementing shared projections.

### 6. Keep manager notification delivery durable and narrow

Create persistent inbox entries for previous-patient-local-day report submissions and transitions caused by last-valid-report retraction. Keep read/unread state across sessions. Use durable processing and deduplication so retries/replay do not lose or duplicate logical notifications. The inbox is reconstructed directly from committed events. Read/unread mutations are changes to inbox state, not read-access auditing.

## Risks / Trade-offs

- Concurrent reporting/editing or stale screens → Recheck invariants at commit and exercise races in integration tests.
- Replay changes history or duplicates notifications → Preserve original facts and versions; test projection rebuild and delivery deduplication.
- Caregiver information leakage → Separate shared-status responses from author-scoped history and test denied reads as well as denied writes.
- Time-zone changes or DST alter occurrence identity → Resolve policies before calendar generation; preserve existing historical occurrence references.
- Indefinite claims block changes → Show holder/time and support explicit resolution; do not invent automatic expiry.
- Existing backend/prototype semantics conflict with requirements → Inventory compatibility before reuse, and test against the recap scenarios.

## Migration Plan

1. Inventory existing care, course, authentication, notification and persistence behavior against these specs, including whether any deployed data needs migration.
2. Resolve the open contract and persistence decisions below, recording them in this design and updating specs only where new product decisions are necessary.
3. Implement and verify the tracking model and role-filtered APIs before connecting prototype workflows.
4. If migration is needed, define source mappings, preserve known authorship/timestamps/history and explicitly identify missing provenance rather than inventing care facts. Rehearse migration and projection rebuild on representative data.
5. Roll out with a documented recovery checkpoint and tested rollback procedure. Do not roll back to a writer that destroys or misinterprets newly recorded facts; retain the event history and restore a compatible reader/writer or suspend writes during recovery.


## Implementation contracts

### Prototype inventory and cutover

Reuse Spring Security, the account registry, the application clock, Liquibase, PostgreSQL, the nginx API proxy and the React build. The browser prototype has local demo identities, local medication/procedure schedules, mutable marks, stock consumption and synthetic inbox items. The separate backend has account passwords, immediately assigned care-circle membership, medication-only courses, mutable dose outcomes and prototype signals. Neither tracking implementation supplies the required accepted relationships, independent report revisions, claims or replayable inbox. The tracking entry point uses a separate API namespace and durable schema. Legacy endpoints and prototype storage remain isolated. Reuse account IDs; create a new explicit tracking patient and manually enter plans. Preserve legacy stores and backups as source records. No automatic conversion of demo marks or legacy dose logs is safe because their semantics and provenance differ.

### Identity and access

Reuse stateless HTTP Basic over TLS, with credentials held in page memory and explicit sign-out. Manager creates one tracking patient. A manager issues a seven-day, single-use opaque token for a normalized email and the caregiver or patient role. The manager copies it out-of-band. Acceptance requires an authenticated account for that email; the recipient may register first. Pending tokens grant no tracking access. Only digests go into the invitation table; audit snapshots omit the digest. Reissue invalidates the previous pending token for that role. An existing patient account is linked to the original patient identity. For MVP, account recovery is operator-assisted after identity verification, using a credential reset with a secret-free audit record. A lost invitation is reissued by the manager.

### Persistence and calendar

Use one ordered tracking stream per patient and conventional patient, invitation and access records. Serialize reads and writes through a patient row lock, including authorization, version and idempotency checks. Each event records sequence, schema version, actor, time, type and a typed JSON payload. Plan versions carry the complete instruction, schedule, effective instant and IANA time zone. Occurrence actions carry the original occurrence snapshot (plan version, nominal local day/time and scheduled instant). Report revisions record author, actual time, recording time and reason where applicable. Claim transitions record previous and new holders. Replay applies events in sequence and rejects unknown versions. Persist accepted facts, request receipts and access audit records in the same database transaction.

Generate one patient-local date at a time without a fixed terminal horizon for open-ended recurrence. Use plan version plus nominal date and clock time as the occurrence key. Persist a snapshot whenever a command references a slot. Use the earlier offset during an overlap and shift forward by the gap during a spring transition. For example, New York 02:30 becomes 03:30 on the spring gap day, and repeated 01:30 yields one occurrence on the earlier offset. Edits keep historical slots and protected exceptions. The patient's zone change applies from the change instant to future eligible slots using new versions for active and queued schedule segments. For example, after moving from Moscow to London, past 09:00 slots retain their Moscow version and future eligible 09:00 slots use London. A protected future slot retains its old version and nominal local day. If a zone change would generate the same nominal time for a slot already preserved on that day, the original occurrence takes precedence.

### Reports, edits and handling

Shared completion shows the earliest actual time among valid report revisions. A reasoned correction can amend actual time. Retraction ends that author's lineage with its history intact; there is no reinstatement in MVP. Early and backdated care are allowed on open occurrences as long as actual time is nonfuture. A report closes any active claim in the report event. An actor may release their own claim. The manager must take over with a reason and then release before editing or cancelling a claimed occurrence or replacing a caregiver who holds a claim. A concurrent completion may close the claim first, in which case the manager refreshes and the existing report protection applies.

A plan edit has an explicit effective instant. Preview reports every protected future occurrence; save checks the plan version and recomputes the protected set under lock. Preserve all slots before the later of commit and effective time, and future occurrences with any report history or a live claim. When two versions would show the same nominal plan date and time, the older preserved slot takes priority. Stop preserves those same exceptions and marks future eligible slots cancelled. Reopening explicitly restores one slot.

### HTTP and recovery

Use /api/tracking for patient listing and creation, patient-local day queries, manager plan preview and edits, invitation management and acceptance, occurrence actions and inbox queries. Plan, occurrence and inbox commands use client-generated request UUIDs scoped to an actor and patient. Invitation acceptance is one-time and access operations recheck the current state. Identical retries return the recorded result subject to current access. Conflicting key reuse and stale preconditions return 409. Malformed inputs return 400, unauthorized commands on known relationships return 403, and unrelated patient IDs return 404. A same-care acknowledgment refers to the current valid-report revision, so the backend can reject a stale warning even when another valid report remains. Refresh after mutations and on focus/reconnect, and poll while viewing a patient. Manager and patient receive full reports; caregiver receives own lineages and shared status.

Inbox delivery is a projection of committed trigger events keyed by triggering event sequence; delivery and read/unread changes replay from the same stream. Determine previous-day delivery using the submission instant in the patient's current zone and the occurrence's stored nominal day. Replay derives delivery immediately, including after interrupted requests, and subsequent read/unread facts preserve state. Retain facts and mutation logs across cancellation, revocation and compatible rollbacks. MVP exposes no destructive deletion or export endpoint. Restore from PostgreSQL backups and replay with a compatible reader; suspend writes when rolling back to a version that cannot interpret the event schema. Operational backups stay access-controlled and are distinct from customer exports.
