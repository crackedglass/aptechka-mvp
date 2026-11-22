# Tracking rollout and recovery

The default app uses PostgreSQL-backed tracking. Users sign in with existing backend accounts or register at the tracking sign-in screen. A manager creates a tracking patient with a named IANA time zone and enters medication or procedure plans. A caregiver or patient registers or signs in with the invited email and enters the code supplied by the manager. An invitation expires after seven days.

The prior prototype remains accessible at `/?demo` with its original browser storage. The previous API and database tables also retain their records. The new tracking schema reuses backend accounts and starts with no care facts. Neither the browser prototype nor legacy dose logs provide enough provenance to convert their marks into the new independent completion reports. There is no care-data backfill. Keep historical sources and backups available to authorized operators. During setup, enter current instructions manually from their authoritative source.

## Deployment

1. Quiesce care writes and record the deployed application revision. Make a protected PostgreSQL backup covering the whole database before deploying, including the Liquibase history and backend account tables. Rehearse the restore in an isolated database. Preserve both the original backup and the new tracking database after the cutover.
2. Deploy matching backend and frontend revisions. Liquibase applies the additive `002-tracking` migration after the existing changelog. Use the same-origin nginx API proxy when running Compose. When deploying the frontend separately, provide its HTTPS backend origin through `VITE_API_URL` and allow the frontend origin in the backend CORS configuration.
3. In the isolated deployment, verify registration or existing account login, creation of one patient without an invitation, a plan appearing on the patient's local day, and authorization after accepting an invitation. Submit and retract a synthetic completion, then verify the inbox entry and its read state through a fresh session. Keep all synthetic fixtures out of production care history.
4. Open the deployed app to users after the smoke checks succeed.

## Recovery and rollback

Pause writes before restoring or changing writer versions. Take an incident backup of the current database before any restore. Use `pg_dump --format=custom` and `pg_restore --exit-on-error` with a matching PostgreSQL major version and credentials provided through the operator's protected connection configuration. Restore into a new database, then point a compatible backend at the restored copy. For a cold volume backup, stop PostgreSQL first and restore into a separate volume with the same major version and permissions.

Tracking reads replay the ordered `tracking_event` stream. Keep `tracking_patient`, `tracking_invitation`, `tracking_receipt`, `account_access_audit`, `app_user` and the Liquibase state alongside it. Check patient and event counts and last sequence values with the backup checkpoint. Verify a historical plan version, a reported occurrence, a closed claim and a read notification through the application before restoring user access. If writes committed after the selected checkpoint, recover them through the retained database or configured point-in-time recovery before reopening. Inbox delivery and read changes replay from the same stream.

If a release must be rolled back after tracking writes, keep the migration and event tables. Roll back application images only to a compatible reader/writer for all recorded event schemas. With a pre-tracking image, suspend tracking writes and retain the database until a compatible release is restored. Preserve originals throughout reconciliation. MVP has no destructive deletion or customer export action.

Account recovery is operator-assisted. Verify the account holder via the established support channel. In a restricted transaction, replace the existing account's `password_hash` with a fresh encoding produced by the application's delegating password encoder. Record a `CREDENTIAL_RESET` audit entry with the authenticated operator's account ID, target account ID and timestamp. Record only operation metadata in audit fields. Keep password hashes and temporary secrets out of command histories and logs. For a lost or expired invitation, the manager reissues it from the app.

## Checks

Run `bash gradlew test --no-daemon` under `backend/` with JDK 25. The tracking integration tests run the Spring request stack and Liquibase with an embedded test database, including parallel claim attempts, stale state, replay, time zones and persistent inbox read state.

Run `npm test` and `npm run build` under `frontend/`. Run `openspec validate mvp-medication-procedure-tracking --strict` and `docker compose --env-file .env.example config --quiet` from the repository root.

The rollout rehearsal validated the Compose configuration and applied the tracking DDL in a disposable PostgreSQL 17 cluster. An offline backup restored into a separate cluster retained the event sequence. Spring integration tests replayed persisted plan versions, report authorship, claim closure and inbox read state. A headless Chromium walkthrough exercised the built frontend against the in-process Spring request stack, including invitation acceptance, independent reports, protected plan edits, cancellation and reopening, and caregiver replacement.

The full backend suite passed all 18 tests. The frontend suite passed all 157 tests and the production build succeeded. Strict OpenSpec validation, Compose configuration validation and the whitespace check also passed.
