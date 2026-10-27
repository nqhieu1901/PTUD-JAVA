# Smart Task

A task management web application consisting of two independent services:

- `backend/`: REST API built with Spring Boot, Spring Security, Spring Data JPA, and MySQL.
- `frontend/`: React interface powered by Vite.

## Current Features

- Register and sign in via email or Google, edit profile, change password,
  forgot/reset password via one-time links, email verification, cookie sessions, and CSRF.
  Recovery tokens are stored only as SHA-256 hashes, have an expiry, and are rotated on resend;
  changing or resetting a password invalidates all existing sessions and recovery links. Email
  verification currently provides a trust status but does not block users from accessing workspaces.
  Google accounts are linked using Google's stable identifier and only use verified emails.
  Google accounts are not automatically merged with password accounts sharing the same email.
  After signing in with a password, users can explicitly link the correct Google account with
  a matching verified email or unlink it; both operations require password re-entry and revoke
  existing sessions. Link requests are stored server-side, scoped to the session/user/credential
  version, are single-use, and expire after 5 minutes by default.
  New Google-only accounts do not have internal sign-in or password change functionality.
  Internal passwords are checked against a 72-byte UTF-8 limit before being processed by BCrypt.
- System account administration: Admins can search, paginate, lock, and unlock users;
  locked accounts lose access even with active sessions.
  Admins use a dedicated admin workspace and cannot access project data or business notifications.
- Create, edit, close, and reopen projects; add, remove, and change member roles.
  Admins and locked accounts cannot be added to projects; tasks cannot be assigned to Admins.
- Three project roles: `OWNER`, `MEMBER`, and `VIEWER`; Viewers can only read and cannot be assigned tasks.
- Create, edit, delete, assign, and split tasks into parent-child relationships.
- Per-task comments; Members/Owners can discuss, and the author or Owner can delete comments.
- Per-task file attachments; Members/Owners can upload, all members can download,
  and the uploader or Owner can delete. Each file is limited to 10 MB. Files are cleaned up
  after a successful delete transaction; deleting a task also cleans up all associated files, and
  the backend periodically reconciles to remove orphaned metadata or orphan files.
- Drag-and-drop Kanban board with per-project workflows and `boardOrder` persistence; Owners can manage 2-7 columns, rename, reorder, and select the completion column.
- Deadline-based Calendar with task detail pop-ups and expandable days with multiple tasks;
  per-project progress Dashboard.
- In-app notifications when added to a project, assigned a task, when tasks are approaching or past their deadline, or when task status changes; tasks can be opened directly from notifications.
  Deadlines are scanned in the background every minute, deduplicated per deadline milestone, and
  re-triggered when a deadline is changed. The UI syncs notifications and project lists while the tab is active;
  member-added notifications can open the new project without requiring a re-login.
- AI-powered task splitting assistant with a streaming suggestion flow, accept or reject; supports
  `DEMO`, OpenAI, and Groq Responses API providers.
  AI calls do not hold transactions/project locks while waiting for a provider;
  failed requests are still saved to history. User permissions and project/task state are
  re-checked before saving results. Accepting suggestions validates task assignees
  and fires assignment notifications like a regular task creation operation.
- Sliding-window rate limiting for sign-in/sign-up, account emails,
  password re-entry operations, and AI; simultaneously limits the number of concurrent AI calls
  per account. `429` responses include `Retry-After`; CSRF is still validated before counting requests.
- PowerShell backup/restore toolkit for offline synchronization of the MySQL database and
  attachment directory, with SHA-256 manifests, dry-run support, and overwrite-protection safeguards.

The `DEMO` provider is the default so the project can run locally without an API key. When the
`OPENAI` provider is enabled, the backend uses Structured Outputs to receive the correct suggestion
structure, does not store responses on the provider side (`store=false`), and still preserves the
Owner review step before actual tasks are created.

## Project Structure

```text
.
├── backend/
│   ├── src/
│   ├── pom.xml
│   ├── mvnw
│   └── mvnw.cmd
├── frontend/
│   ├── src/
│   ├── package.json
│   ├── package-lock.json
│   └── vite.config.js
├── ops/                    # backup/restore MySQL + attachments
├── docs/
│   └── BACKUP_RESTORE.md
└── README.md
```

## Environment Requirements

- Java 21
- Node.js and npm
- MySQL running at `localhost:3306`, or another MySQL server configured via environment variables

The backend connects to the `smart_task` database by default using the `root` account with an empty password. The MySQL account needs permission to create the database if `smart_task` does not yet exist.

## Database Configuration

Environment variables supported by the backend:

| Variable | Default Value | Description |
| --- | --- | --- |
| `DB_URL` | `jdbc:mysql://localhost:3306/smart_task?createDatabaseIfNotExist=true&serverTimezone=UTC` | MySQL JDBC URL |
| `DB_USERNAME` | `root` | MySQL login username |
| `DB_PASSWORD` | empty | MySQL password |
| `SESSION_COOKIE_SECURE` | `false` | Restrict session cookies to HTTPS only; the `prod` profile requires this to be `true` |
| `ATTACHMENT_STORAGE_PATH` | `./data/attachments` | Directory for storing attachment file content |
| `ATTACHMENT_CLEANUP_SCAN_MS` | `3600000` | Interval for reconciling the database and file directory |
| `ATTACHMENT_CLEANUP_INITIAL_DELAY_MS` | `300000` | Delay before the first reconciliation scan |
| `ATTACHMENT_ORPHAN_GRACE_MS` | `3600000` | Minimum age before a file with no metadata is considered an orphan |
| `NOTIFICATION_DEADLINE_SCAN_MS` | `60000` | Backend deadline scan interval in milliseconds |
| `NOTIFICATION_DEADLINE_INITIAL_DELAY_MS` | `60000` | Delay before the first deadline scan |
| `SMARTTASK_ADMIN_EMAIL` | empty | Email for a new Admin account on startup; rejects promotion of an existing USER with the same email |
| `SMARTTASK_ADMIN_PASSWORD` | empty | Initial password, minimum 8 characters and maximum 72 bytes UTF-8; only used when creating a new Admin |
| `SMARTTASK_ADMIN_FULL_NAME` | `SmartTask Administrator` | Display name for the newly created Admin |
| `GOOGLE_OAUTH_ENABLED` | `false` | Enable Google sign-in when set to `true` |
| `GOOGLE_CLIENT_ID` | empty | OAuth 2.0 Client ID for the Web application on Google Cloud |
| `GOOGLE_CLIENT_SECRET` | empty | OAuth 2.0 Client Secret; store only in the backend environment |
| `GOOGLE_REDIRECT_URI` | `http://localhost:8080/login/oauth2/code/google` | Backend redirect URI that receives the callback from Google |
| `GOOGLE_LOGIN_SUCCESS_URL` | `http://localhost:5173/` | Frontend URL after a successful sign-in |
| `GOOGLE_LOGIN_FAILURE_URL` | `http://localhost:5173/?authError=google` | Frontend URL when Google rejects or sign-in fails |
| `GOOGLE_LINK_INTENT_TTL_SECONDS` | `300` | Expiry of a single-use Google link request; accepts 30-900 seconds |
| `AI_PROVIDER` | `DEMO` | Choose `DEMO`, `OPENAI`, or `GROQ` for task splitting suggestions |
| `OPENAI_API_KEY` | empty | Required API key when `AI_PROVIDER=OPENAI`; do not store in source code |
| `OPENAI_MODEL` | `gpt-5-mini` | OpenAI model used for Structured Outputs |
| `OPENAI_BASE_URL` | `https://api.openai.com/v1` | Base URL of the OpenAI Responses API |
| `OPENAI_TIMEOUT_MS` | `30000` | Maximum wait time for a single AI call |
| `GROQ_API_KEY` | empty | Required API key when `AI_PROVIDER=GROQ`; do not store in source code |
| `GROQ_MODEL` | `openai/gpt-oss-20b` | Groq model supporting strict Structured Outputs |
| `GROQ_BASE_URL` | `https://api.groq.com/openai/v1` | Base URL of the Groq Responses API |
| `GROQ_TIMEOUT_MS` | `30000` | Maximum wait time for a single Groq call |
| `SMARTTASK_RATE_LIMIT_ENABLED` | `true` | Enable in-process rate limiting within each backend process |
| `SMARTTASK_AUTH_RATE_LIMIT` | `10` | Total sign-in/sign-up attempts per IP per minute |
| `SMARTTASK_EMAIL_RATE_LIMIT` | `5` | Total account email requests per IP per 15 minutes |
| `SMARTTASK_REAUTH_RATE_LIMIT` | `5` | Total password change/Google link/unlink attempts per account per 15 minutes |
| `SMARTTASK_AI_RATE_LIMIT` | `5` | AI suggestion generation attempts per account per minute |
| `SMARTTASK_AI_CONCURRENT_LIMIT` | `2` | Number of concurrent AI calls allowed per account |
| `SMARTTASK_MAIL_MODE` | `DISABLED` | Choose `DISABLED`, `FILE`, or `SMTP`; the `local` profile defaults to `FILE` |
| `SMARTTASK_MAIL_FROM` | `no-reply@smarttask.local` | Sender address for account emails |
| `SMARTTASK_MAIL_OUTBOX` | `data/mail-outbox` | Private `.eml` directory when using `FILE` mode |
| `SMARTTASK_FRONTEND_URL` | `http://localhost:5173/` | Root URL for generating verification/reset links; production must use HTTPS |
| `SMARTTASK_RESET_TOKEN_TTL_MINUTES` | `30` | Expiry of password reset links |
| `SMARTTASK_VERIFY_TOKEN_TTL_MINUTES` | `1440` | Expiry of email verification links |
| `SMARTTASK_MAIL_RESEND_COOLDOWN_SECONDS` | `60` | Cooldown before rotating and resending the same type of link |
| `SMARTTASK_MAIL_WORKER_COUNT` | `2` | Number of account email sender workers |
| `SMARTTASK_MAIL_QUEUE_CAPACITY` | `100` | Maximum number of emails queued in the backend process |
| `SMTP_HOST`, `SMTP_PORT` | empty, `587` | SMTP server host and port when using `SMTP` mode |
| `SMTP_USERNAME`, `SMTP_PASSWORD` | empty | SMTP credentials; set only in the backend environment or an ignored secrets file |

Example configuration in PowerShell before running the backend:

```powershell
$env:DB_URL = "jdbc:mysql://localhost:3306/smart_task?createDatabaseIfNotExist=true&serverTimezone=UTC"
$env:DB_USERNAME = "root"
$env:DB_PASSWORD = "your-password"
```

If the default values match your local machine, no environment variable declarations are needed.

### Enabling Google Sign-In

In the Google Cloud Console, create an OAuth 2.0 Client of type **Web application** and add
the following exact Authorized Redirect URI:

```text
http://localhost:8080/login/oauth2/code/google
```

Declare the configuration in the terminal that will run the backend:

```powershell
$env:GOOGLE_OAUTH_ENABLED = "true"
$env:GOOGLE_CLIENT_ID = "your-google-client-id"
$env:GOOGLE_CLIENT_SECRET = "your-google-client-secret"
```

Then build and restart the backend, and open the frontend at `http://localhost:5173`. The Google
button will be disabled when the feature is not configured. The same redirect URI serves both
sign-in and linking, so no second Google Cloud callback declaration is needed. Password
accounts manage linking in **Account Settings** and must select a Google account with a matching
email. The backend discards OAuth access/refresh tokens from the session immediately after
identity verification since the application does not call the Google API. Unlinking only removes
the sign-in method from SmartTask; users can revoke granted permissions from their Google Account
if desired. Do not include the Client Secret in Git or frontend code.

You can configure these settings locally in
`backend/config/application-secrets.properties`. This file is imported when running
from the root or `backend/` directory, is excluded from Git, and is not bundled into the JAR.
Example (use only the placeholder values below as a template):

```properties
smarttask.oauth.google.enabled=${GOOGLE_OAUTH_ENABLED:true}
smarttask.oauth.google.client-id=${GOOGLE_CLIENT_ID:your-google-client-id}
smarttask.oauth.google.client-secret=${GOOGLE_CLIENT_SECRET:your-google-client-secret}
```

The `${VARIABLE_NAME:default-value}` syntax reads the environment variable and falls back
to the value after `:` if not declared. Do not set real keys as the variable name like `${real-key}`.
If sharing the entire directory/ZIP for submission, exclude this secrets file and local data;
`.gitignore` does not protect against ZIP submissions. Replace any keys that have been exposed in chat or logs.

### Enabling a Real AI Model

Declare the configuration in the terminal that will run the backend:

```powershell
$env:AI_PROVIDER = "OPENAI"
$env:OPENAI_API_KEY = "your-api-key"
$env:OPENAI_MODEL = "gpt-5-mini"
```

Then build and restart the backend. If `AI_PROVIDER=OPENAI` but the API key is missing, the
backend will fail on startup rather than silently falling back to DEMO data. Do not
include `OPENAI_API_KEY` in Git or frontend code; the key is only read on the backend.

To use Groq, create a new API key and configure it in the backend terminal:

```powershell
$env:AI_PROVIDER = "GROQ"
$env:GROQ_API_KEY = "your-new-groq-api-key"
$env:GROQ_MODEL = "openai/gpt-oss-20b"
```

Do not reuse keys that have ever been sent in chat, logs, or committed to source code.

### Email Verification and Password Reset

The `local` profile uses `SMARTTASK_MAIL_MODE=FILE`: each email attempt is written as an
`.eml` file in `backend/data/mail-outbox/` when the backend is run from the `backend` directory.
This directory is excluded from Git, is not served over HTTP, and is for local UI testing only.

To send real emails, set the configuration in the backend environment or
`backend/config/application-secrets.properties` and restart:

```powershell
$env:SMARTTASK_MAIL_MODE = "SMTP"
$env:SMARTTASK_MAIL_FROM = "no-reply@example.com"
$env:SMARTTASK_FRONTEND_URL = "https://app.example.com/"
$env:SMTP_HOST = "smtp.example.com"
$env:SMTP_PORT = "587"
$env:SMTP_USERNAME = "smtp-user"
$env:SMTP_PASSWORD = "smtp-secret"
```

Do not set the SMTP password on the frontend or commit it to source. The production backend
refuses to generate account links using non-localhost HTTP URLs. Emails are queued after a
successful transaction so SMTP errors do not roll back a password change; monitor warning logs
and test sending before deploying.

### Upgrading an Existing MySQL Database

Flyway automatically manages the schema using migrations in
`backend/src/main/resources/db/migration/mysql/`. New databases run migrations sequentially from V1;
existing databases without a Flyway history are baselined at V1 and then automatically apply
V2-V10 to expand notifications, add the Viewer role, create comment and task attachment tables,
migrate statuses to per-project workflows, and support Google account linking, email verification,
and account recovery.

Back up your MySQL database before the first startup after an update. There is no need to
run SQL files manually; the H2 `local` profile also uses an equivalent set of migrations.

V1 for MySQL uses a trigger to prevent a task from being its own parent, as MySQL 8.4
does not allow CHECK constraints referencing AUTO_INCREMENT columns. H2 keeps V1 in `common/`.
If the existing database has a V1 recorded with a checksum from a previous version or a prior
failed migration, inspect the schema/history on a copy before upgrading.
Do not manually drop the database, modify `flyway_schema_history`, or run `repair` to bypass errors.

### Backup and Restore

SmartTask requires backing up the database and the `ATTACHMENT_STORAGE_PATH` directory together
as a single unit. The scripts in `ops/` require stopping all backend/writer processes, create
SHA-256 manifests, and default to dry-run mode when restoring into the same database or directory. See
the procedure, commands, and safety safeguards at
[`docs/BACKUP_RESTORE.md`](docs/BACKUP_RESTORE.md).

## Running the Backend

From the project root directory:

```powershell
cd backend
.\mvnw.cmd clean package
java -jar target\smart-task-0.0.1-SNAPSHOT.jar
```

The API runs at `http://localhost:8080`.

On Windows, the `spring-boot:run` command can generate an incorrect classpath when the project
path contains Unicode characters. The `clean package` then `java -jar` workflow above is the
stable way to run from the current project location.

If OneDrive/Windows prevents Maven from cleaning `target`, stop the running backend and then
build into a new output directory without needing to delete the old one:

```powershell
.\mvnw.cmd "-Dsmarttask.build-directory=target-validation" verify
java -jar target-validation\smart-task-0.0.1-SNAPSHOT.jar
```

When a completely fresh build is needed, choose a `target-...` name that does not yet exist.

To do a quick test without starting MySQL, use the H2 local profile after packaging:

```powershell
java -jar target\smart-task-0.0.1-SNAPSHOT.jar --spring.profiles.active=local
```

The `local` profile stores business data in memory and discards it when the backend stops.
Uploaded files still remain in `data/attachments`, so use this profile only for quick testing.
The default startup above uses MySQL for persistent data storage.

The `local` profile auto-creates an admin account for UI testing:

- Email: `admin@smarttask.local`
- Password: `Admin@123`

Both values can be overridden with `SMARTTASK_ADMIN_EMAIL` and
`SMARTTASK_ADMIN_PASSWORD`. In the default MySQL profile, Admin bootstrapping is disabled
unless both variables are configured, so there is no default admin password in a
persistent data environment.

## Running the Frontend

Open a second terminal, return to the project root directory, and run:

```powershell
cd frontend
npm ci
npm run dev
```

Open `http://localhost:5173` in a browser.

In the development environment, Vite proxies `/api`, `/oauth2`, and
`/login/oauth2` requests to `http://localhost:8080`. Therefore the backend must be running
before or alongside the frontend. The frontend uses relative paths so that the session login
cookie and CSRF work on the same origin through the development proxy.

## Testing and Building

Backend tests:

```powershell
cd backend
.\mvnw.cmd clean verify
```

Frontend tests:

```powershell
cd frontend
npm ci
npm test
```

The test suite currently includes 204 backend tests and 88 frontend tests. By default, one
real MySQL test is skipped when no test server is declared; the remaining tests use
H2, mocked HTTP/providers, or pure function tests. To run the full MySQL test suite:

```powershell
cd backend
$env:SMARTTASK_MYSQL_TEST_URL = "jdbc:mysql://localhost:3306/?serverTimezone=UTC"
$env:SMARTTASK_MYSQL_TEST_USERNAME = "root"
$env:SMARTTASK_MYSQL_TEST_PASSWORD = ""
.\mvnw.cmd clean verify
```

This URL points to the server and does not include a database name. The test creates a database
with the prefix `smarttask_it_` and a UUID, runs V1-V10, re-runs to verify no duplicate application,
boots Spring Boot/JPA, and checks foreign keys, Unicode, and deadline queries.
The test database is deleted when the test finishes; it does not use or delete the `smart_task` database.
The test account needs CREATE/DROP DATABASE and CREATE TRIGGER permissions.

Building the frontend production bundle:

```powershell
cd frontend
npm run build
```

The frontend build output is generated in `frontend/dist/`. For production deployment,
configure a web server or reverse proxy to serve this directory and forward
`/api`, `/oauth2`, `/login/oauth2` to the backend, and set the appropriate callback URLs.
The production backend must run with the `prod` profile, behind HTTPS, and with a secure session cookie:

```powershell
$env:SPRING_PROFILES_ACTIVE = "prod"
$env:SESSION_COOKIE_SECURE = "true"
```

If `SESSION_COOKIE_SECURE` is not enabled, the `prod` profile will refuse to start to avoid
accidentally sending `JSESSIONID` over HTTP. Frontend URLs, Google callback URLs, and
email links must also use the real HTTPS origin of the deployment environment.

## Validation Status and Pre-Report Limitations

Validation on 08/09/2026: backend **204/204**, frontend **88/88** passing;
JAR and frontend production builds succeeded. Full backend tests were run on
H2 and MySQL 8.4.3 with UUID-named test databases; migrations V1-V10, JPA startup,
foreign keys, Unicode, and deadline queries all passed. Due to OneDrive temporarily locking a
build file, the final validation run was performed in a new output directory outside OneDrive;
the delivered JAR was built at `backend/target-release-20260908-final/`.

The backup/restore drill also passed on real MySQL: dry-run wrote no data,
Unicode, triggers, and binary files restored correctly; a tampered snapshot was rejected.
HTTP smoke tests via the Vite proxy confirmed that registration creates a local verification email,
sign-in, verification, requesting and using a reset link, old sessions receiving `401`, then
signing in again with the new password all work. Smoke test data used H2 `local`, not the user's MySQL business database.

Additional scenarios covered by tests: Admin under React StrictMode; slow responses after
switching projects or sessions; opening a new project from a notification; Calendar with more than three tasks per day;
Google account with duplicate email, Google-only account, guard blocking public entry link, session rotation,
linking/unlinking with CSRF and password re-entry, incorrect email, subject conflict, expired/replayed intent,
consent cancellation, session revocation, safe redirect, and shared rate limiting; discarding OAuth access tokens
from the session after callback and production profile rejecting cookies without the `Secure` flag; Unicode password
exceeding the BCrypt limit; Admin bootstrap conflict with existing USER; deleting a column with only soft-deleted tasks;
re-notification on deadline change; AI failure being saved, permission check after timeout, and assignee validation on accept;
single-use/expired/wrong-purpose account tokens, session revocation, sending mail after commit, URL not leaking tokens,
shared rate limiting after CSRF, and concurrent AI call limits.

These results should not be interpreted as a fully production-hardened system:

- Real Google sign-in and linking from consent to callback have not been tested end-to-end
  with the user's account in this round; automated cases use MockMvc and a simulated provider.
  Readable configuration does not prove that credentials/redirects are valid on Google Cloud.
- AI is running on `DEMO`; provider tests use simulated responses and have not confirmed real
  OpenAI/Groq calls with a new key or evaluated suggestion quality on real data.
- Real SMTP has not been tested with the user's provider and domain; the smoke test
  only used the local `.eml` mailbox.
- Google accounts created by older versions retain their password hash because it is impossible
  to distinguish a random hash from a password the user previously set; the new Google-only
  policy does not automatically convert existing accounts.
- Load testing, multi-instance operation, HTTPS/reverse proxy production deployment, and
  monitoring have not been validated. The rate limiter currently lives within each process and
  resets when the backend restarts; multiple instances require a gateway or shared store. The
  backup/restore tooling exists but scheduling, encryption, and off-site storage remain operational concerns.

The report can begin with the scope of an **MVP personal/team task manager**, describing
implemented features, test evidence, and listing the above limitations in the limitations/future work section.
Do not describe DEMO as a real AI model.
