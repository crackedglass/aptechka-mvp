# Aptechka (Аптечка)

Aptechka is a family care and medication management MVP with a Russian-language interface. It helps patients follow their treatment schedule and lets caregivers and family members review medication intake, manage home supplies, and keep track of care activities.

The project includes:

- A mobile-oriented React interface with patient, caregiver, and treatment manager demo accounts.
- Medication schedules, intake confirmation, procedures, and a calendar of care activities.
- Prescription import from text, photos, and PDFs with local OCR and manual review.
- Home medication inventory, shopping lists, notifications, and reports for a doctor.
- A separate backend API for accounts, patient profiles, care circles, prescriptions, stock, health measurements, and help requests.

**Current integration status:** the frontend uses demo accounts and stores its state in browser `localStorage`; it does not currently call the backend API. Running the full stack makes both components available, but does not synchronize the demo with PostgreSQL. Calls, external appointments, pharmacy integrations, and some health displays are demonstrations.

## Technology

| Component | Stack |
| --- | --- |
| Frontend | React 19, TypeScript, Vite 7; local PDF/OCR processing |
| Backend | Java 25, Spring Boot 4.1, Spring Security, Spring Data JPA |
| Database | PostgreSQL 17, Liquibase migrations |
| Containers | Docker Compose, nginx serving the frontend and proxying the API |
| Deployment | Ansible playbooks and GitHub Actions |

## Run the full stack with Docker

Install Docker with the Docker Compose plugin and start the Docker daemon. No host Java or Node.js installation is required for this option. Run these commands from the repository root.

1. Create the local configuration:

   ```bash
   cp .env.example .env
   ```

   Edit `.env` and replace `POSTGRES_PASSWORD=change-me` with your own password before starting. Leave `VITE_API_URL` empty for the supplied setup.

2. Build and start the services:

   ```bash
   docker compose up --build -d
   ```

   The first build downloads images and dependencies. PostgreSQL must become healthy before the backend starts; the frontend waits for the backend health check. Liquibase applies database migrations automatically.

3. Open [http://localhost:8765](http://localhost:8765) and select a demo account.

| Service | Local address |
| --- | --- |
| Web interface | http://localhost:8765 |
| Backend API | http://localhost:8080/api |
| Backend health check | http://localhost:8080/actuator/health |
| PostgreSQL | `127.0.0.1:5432` |

The nginx frontend also proxies `/api/` and `/actuator/health` to the backend. The API has no landing page at `/api`; see the [backend API reference](backend/README.md#api) for endpoints. Backend accounts are created with `POST /api/auth/register`, and authenticated requests use HTTP Basic. These accounts are separate from the frontend's demo account selector.

Useful commands:

```bash
docker compose ps
docker compose logs -f backend
docker compose down
```

Database data lives in a named Docker volume and survives ordinary `docker compose down`. Adding `--volumes` deletes that data; see the [Docker Compose command reference](https://docs.docker.com/reference/cli/docker/compose/down/). Browser demo data is stored separately and is removed when you clear the site's browser storage.

## Local development

### Frontend only

Use Node.js 24 and npm, matching the frontend CI configuration. The demo can run without the backend or database.

```bash
cd frontend
npm ci
npm run dev
```

Open [http://127.0.0.1:8765](http://127.0.0.1:8765). The development server binds to the loopback interface and requires port `8765` to be free. Stop the Docker frontend first if it is using that port.

From `frontend/`:

```bash
npm test          # Run frontend logic tests
npm run build    # Check TypeScript and build into dist/
npm run format   # Format source and tests
```

The Docker frontend build uses pnpm with its committed lockfile; the commands above use npm with the lockfile used by CI. The current Vite configuration has no API proxy, and setting `VITE_API_URL` alone does not connect the demo UI to the backend.

### Backend with a local JVM

Install JDK 25. Use the included Gradle wrapper; a separate Gradle installation is not required.

First create and edit the root `.env` as described above, then start only PostgreSQL from the repository root:

```bash
docker compose up -d postgres
```

In a terminal, set the backend database credentials to the same values used in `.env`, then run the backend:

```bash
cd backend
export DB_URL='jdbc:postgresql://localhost:5432/aptechka'
export DB_USERNAME='aptechka'
export DB_PASSWORD='replace-with-your-postgres-password'
./gradlew bootRun
```

If you changed `POSTGRES_DB` or `POSTGRES_USER`, update the URL and username accordingly. `bootRun` does not automatically load the root Compose `.env`. Keep port `8080` free by stopping the containerized backend before running a local one. On Windows, use `gradlew.bat` and your shell's environment variable syntax.

From `backend/`, run tests with:

```bash
./gradlew test
```

Backend tests use H2 and do not require Docker or a running PostgreSQL instance.

## Configuration

| Variable | Purpose |
| --- | --- |
| `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD` | Database settings for Docker Compose; the password is required |
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | Database connection for a backend running outside Compose |
| `APP_TIMEZONE` | Patient's local time zone for dose schedules; defaults to `Europe/Moscow` |
| `CORS_ALLOWED_ORIGINS` | Comma-separated browser origins allowed to access the backend |
| `VITE_API_URL` | Frontend build argument exposed by the container setup; currently unused by frontend source |

The root `.env` configures the root Compose stack. The separate `backend/compose.yaml` uses `backend/.env`; use one Compose setup at a time to avoid port conflicts.

## Repository layout

```text
frontend/         React application, browser assets, and frontend tests
backend/          Spring Boot API, database migrations, and backend tests
compose.yaml      Full local stack: PostgreSQL, backend, and frontend
deploy/ansible/   Server provisioning and deployment configuration
openspec/         Specifications and change planning artifacts
docs/             Product planning notes
.github/workflows/ CI and deployment workflows
```

See the [frontend guide](frontend/README.md) for detailed demo workflows and the [backend guide](backend/README.md) for API endpoints, care-circle roles, and migration conventions. Some component documentation describes earlier development stages; the integration status above reflects the current source.
