## Why

The prototype does not yet express the agreed medication and procedure tracking rules consistently. The decisions in `docs/mvp-tracking-grilling-recap.md` (22 November 2026) need an implementation contract that preserves care history, distinguishes reports from actual care, and supports safe coordination between manager, caregiver and patient.

## What Changes

- Establish a mandatory patient identity with optional login, one manager and at most one active caregiver; support invitations and caregiver replacement.
- Introduce manually entered, versioned medication and procedure instructions, one-off and weekday schedules, and independently tracked occurrences with historical reporting.
- Support positive full-completion reports, author-only appended corrections and retractions, duplicate-care acknowledgment, and optional handling claims.
- Protect reported and actively handled occurrences during plan changes; support cancellation and manager reopening.
- Persist tracking facts with event sourcing, maintain change history for patient/access data, and provide the manager's persistent in-app notification inbox.
- **BREAKING**: Supersede incompatible prototype behavior, including the one-hour reporting window, automatic missed/not-done semantics and permissions that disagree with the recap. Existing backend behavior is not the requirements authority.
- Exclude inventory, OCR/import, chat, appointments/calls, devices/camera/integrations, structured procedure results, partial completion, as-needed/tapering/every-N-hours schedules, external notification delivery, read audits and expanded relationship cardinalities.

## Capabilities

### New Capabilities

- `care-access`: Patient identity, role permissions, invitation acceptance and caregiver replacement.
- `tracking-plans`: Instruction versions, supported schedules, patient-local occurrences, effective edits and cancellation/reopening.
- `completion-reporting`: Independent full-completion reports, actual/recording times, history visibility, corrections, retractions and duplicate-care safeguards.
- `occurrence-handling`: Optional exclusive claims, explicit takeover and completion-driven closure.
- `tracking-history`: Immutable tracking facts and reconstruction, conventional account/access storage and complete mutation history.
- `manager-inbox`: Persistent read/unread notifications for previous-day reports and last-report retractions.

### Modified Capabilities

None. There are no existing main capability specifications.

## Impact

Expected integration areas include the frontend plan, schedule, procedure, monitoring and notification views and models, and backend care, course, authentication and persistence boundaries. API contracts, event payloads, concurrency strategy and reuse/migration decisions require detailed design tasks before dependent implementation. No new library, event-store product or broker is selected by this proposal. This change creates planning artifacts only; it does not implement or remove prototype features.
