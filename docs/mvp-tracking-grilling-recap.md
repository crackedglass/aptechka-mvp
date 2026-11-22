# MVP medication and procedure tracking decisions

This recap records the decisions agreed during the MVP design interview on 22 November 2026. The frontend prototype is the starting point for backend design; the decisions below refine and supersede prototype behavior where they differ. Existing backend behavior does not automatically define requirements.

The first release focuses on medication and procedure tracking and its role model. It includes the supporting handling workflow, change history and minimal manager notifications agreed during the interview. This is a requirements recap, not a finalized database schema or API contract, and does not authorize implementation of deferred features.

## Scope

### Included

- Patient identity and optional patient login.
- Treatment manager and caregiver access, invitations and caregiver replacement.
- Manager-controlled medication and procedure instructions and schedules.
- Individual scheduled occurrences, including historical occurrences.
- Completion reporting by the manager, caregiver and patient.
- Late reports, appended corrections and retractions.
- Shared completion status and warnings against duplicate care.
- Optional claims indicating that someone is handling an occurrence, with release and takeover.
- Cancellation and manager reopening of occurrences.
- Full change history and event sourcing for the tracking core.
- A persistent in-app manager notification inbox with read/unread status.

### Considered but deferred

- Inventory, stock consumption, replenishment and shopping.
- Prescription OCR and document import; MVP instructions are entered manually.
- Chat, appointments, calls, connected devices, camera and external integrations.
- Structured procedure results, measurements, attachments and procedure-specific forms.
- Partial medication doses and partially completed procedures.
- As-needed instructions, tapering and schedules expressed as every N hours.
- Push, SMS and email notification delivery.
- Read-access audit logging.
- Multiple patients per manager and multiple caregivers per patient.

## Actors and relationships

The customer is an adult coordinating care for a relative who needs caregiver assistance. The three actors are the treatment manager, caregiver and patient.

- Each patient has exactly one treatment manager.
- For MVP, each manager has one patient and each patient has zero or one active caregiver.
- A patient has a mandatory distinct identity. Activating login is optional.
- Tracking can begin before either a caregiver joins or the patient activates login.
- The manager creates the patient identity and invites the caregiver, and can invite the patient to activate access.
- Invitations grant access only after acceptance.
- Replacing a caregiver revokes the previous caregiver's access while preserving their reports and history. The manager must resolve their active claims.
- Future relationship expansion should accommodate multiple patients and caregivers. Exact future cardinalities and cross-circle role combinations are not MVP requirements.

There is no exclusive assignment of individual tasks to a caregiver. For MVP, the caregiver's own tasks means the medication and procedure occurrences of their linked patient. Any of the three permitted actors can report completion.

## Permissions

| Capability | Treatment manager | Caregiver | Patient with activated login |
| --- | --- | --- | --- |
| View the patient's scheduled tracking tasks | Yes | Yes | Yes |
| Create, edit or stop instructions and plans | Yes | No | No |
| Manage access and invitations | Yes | No | No |
| Report full completion | Yes | Yes | Yes |
| Claim, release own claim or explicitly take over handling | Yes | Yes | Yes |
| Append corrections or retract own reports | Yes | Yes | Yes |
| Correct another actor's report | No | No | No |
| View all care reports and corrections | Yes | No | Yes |
| View own reports and corrections | Yes | Yes | Yes |
| See shared completion and handling status | Yes | Yes | Yes |
| Reopen a cancelled occurrence | Yes | No | No |

The patient's access to care history is the same as the manager's; this does not grant plan editing or access administration. Caregivers see necessary instructions, their own report history, shared completion status and the recorded completion time, but not other actors' detailed reports. Handling status shows who claimed the occurrence and when.

## Instructions and schedules

Medication instructions contain a medication name, scheduled dose and unit. Procedure instructions contain a title and instructions. Both have a start date, scheduled times and optional end date.

Supported schedules are:

- One-off activities.
- Recurring activities on selected weekdays, with one or more scheduled times.

Use one explicit patient time zone. Recurring activities remain at the same local clock time in that zone. Store report timestamps as absolute times. The patient's time zone determines which calendar day an occurrence belongs to.

A recurring instruction and an occurrence are different concepts. For example, a daily medication instruction produces separate occurrences for Monday at 09:00 and Tuesday at 09:00. Two doses on the same day at 09:00 and 18:00 are also separate occurrences. The same distinction applies to procedures.

### Schedule changes and cancellation

- Version instructions and schedules and preserve the version associated with historical occurrences and reports.
- Apply edits from an explicit effective time to future, unreported occurrences.
- Preserve past unreported occurrences so that late reports remain possible.
- Preserve future occurrences that already have reports and show these exceptions before the manager saves an edit.
- Cancelling a plan stops future activities without erasing its history.
- A cancelled occurrence cannot receive ordinary completion reports. The manager must explicitly reopen it if care happened despite cancellation.
- An actively claimed occurrence cannot be changed or cancelled without explicit release or takeover first.
- Plan edits can still affect other future occurrences while identifying the occurrence currently being handled.

The exact handling of takeover followed by cancellation remains an implementation-design detail to resolve; takeover alone must not be mistaken for completion.

## Completion reports

Reports are positive statements of completed care. There is no explicit “not done” report or separate “not done” domain event.

- A medication completion report means the full scheduled dose was taken.
- A procedure completion report means the whole scheduled procedure was finished.
- Show the scheduled dose or procedure instructions when reporting.
- Every report references one specific occurrence and identifies its author separately from the patient receiving care.
- Caregiver and patient reports are independent reports, not an administration report followed by a separate patient acknowledgment.
- The manager may report care they personally provided.
- Each actor has one report per occurrence; subsequent changes are appended corrections or retractions.
- Retrying a submission must not create duplicate reports.
- Different actors may independently report the same occurrence. Retain all such reports, but count the occurrence as completed once.
- An occurrence is completed while at least one valid completion report remains.

### Completion time and late reporting

Store when care actually happened separately from when the report was submitted. Care may happen before or after the scheduled time. Reject actual completion times in the future; do not impose the prototype's one-hour reporting window.

Today's tasks are the default view, but users can open previous days and report late. A report submitted today about yesterday's dose remains linked to yesterday's occurrence. A report against a previous patient-local day's occurrence notifies the manager.

An occurrence without a valid completion report is pending and becomes overdue after its scheduled time. Overdue means there is no valid report; it does not prove that care failed to happen. Automatic “missed” or “not done” reporting is not part of the agreed model.

### Corrections and retractions

- Only a report's author may append its correction or retraction, including for manager-authored reports.
- Preserve the original report and every subsequent correction, with the actual correcting actor, timestamps and a required reason.
- A retraction invalidates that author's completion assertion without erasing it.
- If another valid report remains, the occurrence remains completed.
- If the last valid completion report is retracted, the occurrence returns to pending or overdue and the manager is notified.
- The manager and patient see all care-report corrections; the caregiver sees their own.

The earlier proposal for conflicting “done” and “not done” reports and manager resolution was superseded by the positive-report-only model. It is not an additional MVP workflow.

## Preventing duplicate care

All three actors see shared completion status before reporting. When a valid completion report already exists:

- Show “Already reported complete” and the recorded completion time.
- Require explicit acknowledgment that the new report confirms the same care rather than another administration.
- Refresh shared status before accepting a report.
- Preserve the caregiver's restricted access to other actors' report details.

Independent reports still count as one completed occurrence. This is a recorded status, not proof of how many times care physically happened.

### Handling claims

A claim expresses “I'm handling this.” It is coordination state, not evidence of completed care.

- Any of the three actors can claim an occurrence.
- At most one active handler exists for an occurrence.
- Claiming is optional when reporting care that already happened.
- Show the handler and claim time to all three actors.
- A claim stays active until the holder releases it, completion closes it, or another actor explicitly takes over.
- Require a reason for takeover and retain its history.
- Do not automatically expire claims; care may still be underway.
- A valid completion report from any actor closes an active claim, even if someone else holds it.
- Preserve both handling and completion actions and immediately expose the completed status to everyone.
- Manager edits and cancellation must respect the active-claim restriction described above.

## Manager notifications

MVP includes a persistent in-app inbox with read/unread status. Push, SMS and email are deferred.

Agreed notification triggers are:

1. A report is submitted for an occurrence on a previous day in the patient's time zone.
2. Retraction removes the last valid completion report, reopening an occurrence as pending or overdue.

A report entered later on the same patient-local day does not trigger the previous-day notification. Other notification triggers and reminder delivery were not agreed as MVP requirements. Read/unread inbox state is supported even though audit logging of reads is deferred.

## History and persistence architecture

Use event sourcing for the tracking core: plan changes, occurrence cancellation and reopening, completion reports, corrections and the agreed handling workflow. Store immutable facts and derive current tracking state from them.

Keep accounts and access relationships in conventional storage with an append-only change log. Event sourcing is not required for the entire backend, and this decision does not select a message broker or event-store product.

Record every change, including instructions, schedules, occurrences, reports, patient details and access permissions. Preserve who changed what, when it changed and the previous values or sufficient history to reconstruct them. Distinguish actual care time from recording time. Deletion and cancellation must not erase history. Never put passwords or authentication secrets into the change log. Read-access logging is future work.

## Candidate domain entities

These names organize the agreed concepts. They are proposed design vocabulary, not approved table names, aggregate boundaries or an inheritance hierarchy.

| Candidate | Responsibility |
| --- | --- |
| User or account | An authenticated actor; patient login may remain inactive |
| Patient | Mandatory identity of the person receiving care and their time zone |
| Care relationship | Manager, patient and optional caregiver linkage and access lifecycle |
| Invitation | Pending and accepted access invitation |
| Medication instruction | Medication name, full dose and unit, with versioned instructions |
| Procedure instruction | Procedure title and instructions, with versions |
| Schedule | One-off or weekday recurrence and effective versions |
| Occurrence | One scheduled medication dose or procedure, tied to its instruction version |
| Completion report | One actor's completion assertion for one occurrence |
| Report correction | Appended amendment or retraction of the author's report |
| Handling claim | Current handler and the history of claim, release, takeover and closure |
| Manager notification | Inbox entry and read/unread state |
| Change record | Immutable tracking fact or change-log entry for other mutable records |

Medication and procedure occurrences share tracking rules. Whether their instructions use separate aggregates or a common abstraction remains to be designed.

## Candidate events and change records

Event names below are illustrative. Their exact payloads, stream boundaries and granularity require backend design. They distinguish facts that happened from commands requesting a change.

| Area | Candidate facts |
| --- | --- |
| Instructions and schedules | InstructionCreated, InstructionRevised, ScheduleRevised, PlanCancelled |
| Occurrence lifecycle | OccurrenceCancelled, OccurrenceReopened |
| Reports | CompletionReported, CompletionReportCorrected, CompletionReportRetracted |
| Handling | OccurrenceClaimed, ClaimReleased, ClaimTakenOver; claim closure on completion must be reconstructable |
| Patient and access change log | PatientCreated, PatientDetailsChanged, InvitationCreated, InvitationAccepted, CaregiverAccessRevoked |
| Inbox changes | ManagerNotificationCreated, NotificationMarkedRead, NotificationMarkedUnread |

There is no NotDoneReported event. Pending, overdue and completed are derived states; no requirement was established for an event on every clock-driven status change. Whether occurrence generation and completion-driven claim closure need dedicated events is still open.

## Scenarios for later acceptance checks

1. A manager creates a patient without login and starts tracking before inviting a caregiver.
2. The caregiver accepts an invitation and sees the linked patient's tasks, shared status and their own reports only.
3. The caregiver reports a dose complete. The patient sees that status and can explicitly confirm the same care with an independent report; completion counts once.
4. The caregiver retracts their report while the patient's valid report remains. The occurrence stays completed.
5. The last valid report is retracted. The occurrence becomes pending or overdue and the manager receives an inbox notification.
6. A user opens yesterday's schedule and reports yesterday's dose today. The original occurrence receives the report and the manager is notified.
7. Two users attempt to claim the same occurrence. Only one active handler is allowed; another user must explicitly take over with a reason.
8. The patient reports completion while the caregiver holds the claim. The report completes the occurrence and closes the claim.
9. The manager edits a recurring schedule. Past unreported occurrences and future reported occurrences remain intact; an actively handled occurrence is identified and protected.
10. A cancelled occurrence rejects ordinary reporting until the manager reopens it.
11. Replacing the caregiver preserves history, revokes old access and requires resolution of their active claims.
12. Retrying the same completion submission does not create a second report.

## Questions remaining for detailed design

The interview settled the rules above, but did not finalize every implementation choice. Do not treat the following as implicitly approved:

- Authentication method, invitation delivery and expiry, patient login activation and recovery.
- Event-store technology, aggregate and stream boundaries, event payloads and schema evolution.
- API contracts, error responses, concurrency controls and idempotency keys, including completion-status changes between viewing and submitting.
- Occurrence generation horizon and persistence strategy.
- Daylight-saving transitions, ambiguous or nonexistent local times and changes to the patient's time zone.
- Which completion time to display when multiple valid reports give different times.
- Correction fields, reinstatement after retraction and any limits on backdating or early completion.
- Claim-release authority for the manager and the exact takeover or release sequence before cancellation or caregiver removal.
- Refresh mechanism and reconnection behavior for shared status; offline reporting was not agreed.
- Retention, account deletion, export and operational recovery policies.
- How much of the existing backend to reuse and how to migrate prototype data, if needed.

The next design step can turn this recap into precise state transitions, event contracts and an integration plan without adding deferred product scope.
