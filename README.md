# dft-remote-scheduler

Generates the team's weekly remote-work schedule, stores it, and shows it on a web page.

Every Thursday morning the app plans the coming week and saves it. No more racing each other to
fill in a shared spreadsheet.

---

## The rules

| Rule | Value | Where |
|---|---|---|
| People | 16 | `remote.people` |
| Working days | Lundi – Vendredi | `remote.days` |
| Remote slots per day | 10 | `remote.slots-per-day` |
| Remote days per person | exactly 3 | `remote.remotes-per-person` |
| Consecutive remote days | at most 2, so never 3 in a row | `remote.max-consecutive-days` |
| Holidays | none by default | `remote.holidays` |
| Vacation returns | none by default | `remote.vacation-returns` |

Everything above lives in `application.yml`. Nothing about the team is hardcoded in Java.

**Capacity is tight on purpose.** 16 people × 3 days = **48 remote days** against
5 × 10 = **50 slots**. Two slots of slack. Declaring a holiday drops capacity to 40, which makes
the week infeasible — the app says so clearly instead of quietly producing an unfair schedule.
For a short week, lower `remote.remotes-per-person` to 2.

---

## Running it

```bash
docker compose up -d   # PostgreSQL on localhost:5435
./gradlew bootRun
```

Then open <http://localhost:8080>.

Nothing else to configure — the datasource defaults match the compose database, so this also
works from a plain **Run** in IntelliJ with no run configuration, environment variables or active
profile.

There is also a `local` profile that spells the same credentials out explicitly and turns on debug
logging:

```bash
./gradlew bootRun --args='--spring.profiles.active=local'
```

```bash
./gradlew test    # 35 tests
./gradlew build   # compile, test, package
```

### Database

PostgreSQL 16. `docker-compose.yml` runs it on host port **5435** — 5432, 5433 and 5434 are
already taken by other services on this machine. Database, user and password are all
`dft_remote`, following the house convention that the database name, the username and the service
short name match.

Connection settings come from the environment, defaulting to the compose database
(see `.env.example`):

| Variable | Default |
|---|---|
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://localhost:5435/dft_remote` |
| `SPRING_DATASOURCE_USERNAME` | `dft_remote` |
| `SPRING_DATASOURCE_PASSWORD` | `dft_remote` |

Those defaults exist so local development needs no setup. **They are development credentials,
published in this repository — any real deployment must override all three.** An explicit
environment variable always wins over the default.

**Schema is owned by Liquibase**, not Hibernate: `db/changelog/db.changelog-master.yaml` includes
ordered change files under `db/changelog/changes/`. `spring.jpa.hibernate.ddl-auto` is `validate`,
so Hibernate checks the entities still match the schema and refuses to start if they have drifted.
To change the schema, add a new change file and include it — never edit an applied one.

---

## Accounts and access

Everything is behind a sign-in. There are two roles:

| | Read the schedule | Export .xlsx | Generate / re-roll | Manage accounts |
|---|---|---|---|---|
| **User** | yes | yes | no | no |
| **Admin** | yes | yes | yes | yes |

A read-only user simply doesn't see the buttons they can't use, and the server refuses the
requests anyway — the page hiding them is a courtesy, not the control.

**Admins create accounts** at `/admin/users`: an email, a role, and optionally which person on
the roster they are. The app generates a temporary password and **shows it once** — it is stored
only as a BCrypt hash and cannot be recovered, so copy it before leaving the page. The person is
held on the change-password screen from the moment they sign in until they replace it; no other
page will load until they do.

Admins can also reset a password (issues a fresh temporary one), deactivate and reactivate
accounts, change roles, and delete outright. Three things are refused: acting on your own
account, and anything that would leave **no active admin** — the last admin cannot be demoted,
deactivated or deleted, because there would be no way back in.

Linking an account to a roster name is what lets the chart mark **your own row**.

### The first admin

On an empty database one admin is created so there is someone to sign in as. Configure it with
`REMOTE_ADMIN_EMAIL` and `REMOTE_ADMIN_PASSWORD`, or leave the password unset and a random one
is generated and printed to the log **once** at startup. Either way it is temporary and must be
replaced at first sign-in.

### Machine access

`REMOTE_SERVICE_TOKEN` sets a shared secret. A request carrying it as `X-Service-Token` is
treated as an admin, which is how an external scheduler can call the generate endpoint without a
session. **Unset by default** — no token is accepted at all until you configure one. The
comparison is constant-time, and a schedule generated this way is recorded as `service` rather
than `api`, so the page shows where it came from.

```bash
curl -X POST -H "X-Service-Token: $TOKEN" https://your-host/api/schedules/generate
```

The API answers `401`/`403` rather than redirecting, so a machine caller gets a status code
instead of a login page. CSRF protection is on for the pages and off for `/api/**`, where there
is no cookie-driven write to forge.

## The web page

`GET /` opens on the **current week**, drawn as a wall chart: one row per person, one column per
day, a bar where they are remote.

That orientation is the point. A list of names under each day answers "who is off on Wednesday"
and nothing else. The chart answers it just as well — read the column — while also letting anyone
find their own row and read their week straight across. **Consecutive remote days fuse into a
single bar**, so a run is a shape rather than something you have to work out, and a bar spanning
three days would be a rule violation you could spot from across the room. Column headings carry
each day's fill (`10/10`) with a meter, the Σ column carries each person's total, and any row that
missed its quota is marked in red.

**←** and **→** step a week at a time — as do the arrow keys — and **Today** jumps back to the
current one. When the week on screen is the current one, today's column is tinted.
Navigation is not limited to weeks that exist: land on a week nobody has scheduled and you get an
empty state with a **Generate this week** button for exactly that week. That is how you schedule
any week from the page — the buttons always act on the week you are looking at.

A week that already has a schedule shows **Re-roll** and **Export .xlsx** instead. Generate never
overwrites: it refuses and says so, and re-rolling is a separate, explicit button. Same rule the
Thursday job follows, so nothing the team is already using gets discarded by a stray click.

A **Light / Auto / Dark** switch sits in the top bar. Auto follows the system setting and is the
default; picking a side stores it in the browser and an inline script applies it before first
paint, so switching never flashes the other theme. The choice is per browser, not shared.

The page is plain server-rendered Thymeleaf with one small stylesheet and a few dozen lines of
JavaScript for the theme switch and the arrow keys — no build step, no framework, no webfont to
fetch.

## The API

| Method | Path | What it does |
|---|---|---|
| `GET` | `/api/schedules` | every stored schedule, newest week first |
| `GET` | `/api/schedules/current` | this week |
| `GET` | `/api/schedules/{date}` | the week containing `{date}` — any day of it works |
| `POST` | `/api/schedules/generate?week=&replace=` | run the distribution now; `week` defaults to next week |
| `GET` | `/api/schedules/{date}/export` | download that week as `.xlsx` |

**Generating never writes a file.** It produces a schedule and stores it, nothing more. The
workbook is built on demand, in memory, by the export endpoint — the only place `.xlsx` comes
from.

Failures come back as RFC 7807 problem details: `409` if the week already has a schedule,
`422` if the constraints cannot be satisfied (with the arithmetic in the message), `404` if the
week was never generated.

```bash
curl -X POST localhost:8080/api/schedules/generate
curl localhost:8080/api/schedules/current
curl -OJ localhost:8080/api/schedules/2026-09-21/export
```

## The Thursday job

**The job ships switched off** (`remote.job.enabled: false`): admins generate each week
themselves from the page. It is disabled rather than deleted — set `REMOTE_JOB_ENABLED=true` and
it plans weeks again, which only works on a host that is awake at the time.

When enabled, `WeeklyScheduleJob` runs on `remote.job.cron` (default `0 0 8 * * THU`, zone
`Africa/Casablanca`) and plans the week starting the following Monday, so the team has next week
before the weekend. 
It is **idempotent**: if that week already has a schedule — because someone generated it by hand
— the job logs it and leaves their version alone, never overwriting it. A failure is logged rather
than thrown, so one bad week never kills the scheduler thread. Set `remote.job.enabled=false` to
turn it off.

It stores the schedule and nothing else; no files are written.

---

## How the solver works

The interesting part is `ScheduleSolver`. The obvious approach — for each day, pick a subset of
people that fits the slots — does not scale: at 16 people and 10 slots a single day has **58,651**
candidate subsets, and "everyone gets exactly 3" can only be checked once all five days are
chosen, so the search grinds through enormous numbers of branches that were doomed from day one.

So the search is transposed. Each person is assigned one **week pattern** — a 5-bit mask of the
days they are remote:

- exactly 3 bits set (the quota, true by construction)
- no 3 bits in a row (the consecutive rule, one bit test: `mask & (mask>>1) & (mask>>2)`)
- nothing on a holiday or a vacation-return day

That leaves **7 valid patterns** per person, out of 32 possible subsets — and crucially that
number does not grow with the team. Only daily capacity has to be tracked while backtracking.
The solver handles 200 people in well under a second.

Two more touches:

- **Fairness** — the people order and each person's pattern order are shuffled, so the same
  people do not always get the same days. Pass a seeded `Random` to make a run reproducible.
- **Balance** — patterns are tried least-loaded-day first, so a week lands on 10/10/10/9/9
  rather than 10/10/10/10/8. This only reorders the search, so no valid schedule is ruled out.

## Layout

```
solver/      ScheduleSolver, WeekPatterns — pure algorithm, no Spring or JPA
domain/      WeekSchedule, RemoteAssignment — JPA entities
repository/  WeekScheduleRepository
service/     ScheduleService (solve + persist), ScheduleExcelExporter, WeekStarts
scheduler/   WeeklyScheduleJob — the Thursday cron
web/         REST controller, page controller, DTO, problem-detail handler
config/      RemoteScheduleProperties — the YAML binding

resources/db/changelog/   Liquibase master + change files
templates/schedule.html   the page (Thymeleaf)
```

A schedule is always keyed by the **Monday its week starts on** (`WeekStarts`), so any date in a
week resolves to the same record and a week cannot be scheduled twice.

## Deploying

### The image

```bash
docker build -t dft-remote-scheduler .
docker run -p 8080:8080 \
  -e SPRING_DATASOURCE_URL=jdbc:postgresql://host:5432/dft_remote \
  -e SPRING_DATASOURCE_USERNAME=dft_remote \
  -e SPRING_DATASOURCE_PASSWORD=... \
  -e REMOTE_ADMIN_EMAIL=you@cirestechnologies.ma \
  -e REMOTE_ADMIN_PASSWORD=... \
  dft-remote-scheduler
```

Multi-stage: a JDK builds the jar, a JRE runs it, and nothing from the build stage ships. It runs
as a non-root user, the JVM is PID 1 so it stops on `SIGTERM` rather than waiting out the kill
timeout, and there is a `HEALTHCHECK` against `/login` — public, so it needs no credentials.

The heap is set with `MaxRAMPercentage` rather than a fixed `-Xmx`, so it follows whatever the
host allows. Measured under a 512 MB limit: **384 MB heap, boots in about five seconds, sits
around 250 MB**. `SerialGC` because on one or two cores the parallel collectors cost more in
memory and threads than they return at this traffic.

Tests are skipped during the image build — they need a real PostgreSQL through Testcontainers,
which would mean a Docker daemon inside the build. Run them in CI instead.

### The Thursday trigger

`.github/workflows/plan-next-week.yml` calls the API on a schedule.

**Its weekly trigger is commented out**, to match the app's job being off — the workflow still
runs on demand from the Actions tab. Uncomment the `schedule:` block to turn it back on.

**You need this if your host sleeps when idle.** The in-process `@Scheduled` job only fires if a
JVM is running at the time, so on a free tier that scales to zero, Thursday 08:00 passes and
nothing happens — silently, until somebody notices next week is empty. The workflow's request
wakes the host and generates the week. Set `remote.job.enabled=false` there so the two cannot
both run. On an always-on host, keep the in-process job and delete the workflow.

Two repository secrets, under Settings → Secrets and variables → Actions:

| Secret | Value |
|---|---|
| `SCHEDULER_BASE_URL` | `https://your-host` |
| `SCHEDULER_SERVICE_TOKEN` | the same value as `REMOTE_SERVICE_TOKEN` on the server |

It runs Thursday 07:00 UTC, and can be triggered by hand from the Actions tab with an optional
week. A `409` counts as success — that means someone already planned the week and the app
correctly refused to overwrite. It retries five times with backoff, because a sleeping host can
take most of a minute to answer.

Three things worth knowing about GitHub's scheduler:

- **Scheduled workflows are disabled after 60 days without repository activity.** A quiet repo
  stops planning weeks. GitHub emails you first.
- Scheduled runs are queued, not guaranteed on the minute; delays of tens of minutes happen.
- The cron is UTC. Morocco is UTC+1 most of the year and UTC+0 during Ramadan, so the local hour
  shifts. Harmless here — the job only has to run some time on Thursday.

## Tests

`./gradlew test` runs 35 tests. The 24 solver and `WeekStarts` tests are plain unit tests with no
Spring context. The rest extend `AbstractPostgresIntegrationTest`, which starts a throwaway
**PostgreSQL 16 in Testcontainers** with Liquibase enabled and `ddl-auto=validate` — so every run
checks the changelog and the entities still agree. Docker must be running.

## Origin

Grew out of the [`remote`](../remote) command-line generator, which solved the same problem but
printed to a terminal and had nowhere to keep the result.
