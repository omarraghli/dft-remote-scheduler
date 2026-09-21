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
| Public holidays | the Moroccan calendar | `/admin/holidays`, seeded from `remote.public-holidays` |
| Standing closures | none by default | `remote.holidays` |
| Vacation returns | none by default | `remote.vacation-returns` |
| Preferred remote days | any, per week, a wish | the schedule page |
| On-site days | none, admins only | `/admin/week` |

Everything above lives in `application.yml`. Nothing about the team is hardcoded in Java.

**Capacity is tight on purpose.** 16 people × 3 days = **48 remote days** against
5 × 10 = **50 slots**. Two slots of slack. Losing a day drops capacity to 40, which would make the
week infeasible — so a week with a public holiday in it lowers the quota instead, and the app says
so clearly rather than quietly producing an unfair schedule.

---

## Jours fériés

The app knows the Moroccan public holidays and **nobody is remote on one** — the office is shut,
so the day is not a remote day. The column is marked on the chart with the holiday's name, and a
strip above it names the ones falling in the week on screen. Weeks you have not generated yet show
them too, so you can see a short week coming.

**A short week lowers the quota**, because the usual three remote days no longer fit:

| Holidays in the week | Remote days per person |
|---|---|
| none | 3 |
| one | 2 |
| two or more | 1 |

That ladder is `remote.public-holidays.quotas`, keyed by the number of holidays; a count with no
entry of its own uses the highest entry below it. The quota is only ever lowered, never raised
above `remote.remotes-per-person`. The page's **Each** figure and the red off-quota marking follow
the week being shown, so a holiday week is not flagged as everybody having missed their target.

Holidays are never computed, and they come from two places, because the two kinds behave
differently.

**The eleven national days are rules in `application.yml`** — `11-18`, `07-30`, `01-11` and the
rest, written as `MM-dd` under `remote.public-holidays.annual`. They hold for every year
automatically and nobody ever needs to touch them. The list includes the two recent additions,
`01-14` Nouvel An Amazigh and `10-31` Fête de l'Unité, which older holiday calendars predate.

**The religious days are rows in the database**, managed at **`/admin/holidays`**. They move with
the lunar calendar and are fixed by moon sighting, announced only days before — so waiting on a
code change and a redeploy is exactly the wrong shape. Any admin can add, edit or delete one from
the browser:

- **name**, **first day** and **length in days** — one entry however long it runs, so a two-day
  Aïd is a single holiday of 2 days rather than two rows
- entering the same one twice is refused, naming what it overlaps, because a silent duplicate
  would only show up the week it mattered
- a holiday landing on a weekend is kept but marked *no effect* — it has no column
- **if the week was already planned**, the page says so and links straight to it: those
  assignments were made when the day was still a working one, so the week needs a re-roll
- **if the calendar is within six months of running out**, the page says that too, because past
  its last entry every week plans as though the Aïds were ordinary working days

`remote.public-holidays.dated` keeps it topped up — the shipped list runs to July 2030, following
the Umm al-Qura calculation, which is routinely a day out from what Morocco observes. Confirm each
against the official announcement and correct it on the page.

Each start reads that list and adds **only the dates falling past the last stored holiday**.
Everything up to that point belongs to whoever has been editing the page: a corrected Aïd stays
corrected and a deleted one stays deleted, however the configuration reads. Extending the calendar
is therefore just a matter of appending to the list and restarting; changing a date already stored
is not, and has to be done on the page.

Anything else — a day the whole team takes off, or one person's return from vacation — is still
`remote.holidays` (weekday names, every week) or `remote.vacation-returns` (one person, one day).

---

## Wishes and pins

Two things steer a week before it is rolled, and they are deliberately not the same strength.

**You can ask for days.** Sign in, open the week you care about, and tick the days you would
rather work from home. It is a wish, not a booking: the schedule grants as many as it can, and
when more people want Vendredi than there are slots on it, who gets it is down to the draw — the
same draw that decides everything else. A wish can never make a week impossible and never bends
a rule; your three days, the two-in-a-row limit and the ten slots hold whatever anybody asked
for. Ask for nothing and nothing changes. Ask for all five and you still get three.

What a wish does change is the shape of the week. Left alone the schedule spreads people evenly,
10/10/10/9/9. If half the team wants Lundi, Lundi fills and the quiet days stay quiet — which is
the point, but it does mean a week with strong preferences will not look as flat as one without.

Your account has to be linked to a roster name for the control to appear. That is the same link
that marks your own row on the chart, and an admin sets it at `/admin/users`.

**Admins can require days.** A comité, a client on site, an onboarding — **`/admin/week`** is one
grid, the roster down the side and the week across the top, where an admin ticks who has to be in
the office when, and can tick wishes on behalf of anyone without an account. The office box is
**hard**: nobody is given a remote day they are needed in the office on, exactly as though it
were a public holiday for them alone. The chart marks those cells with a dot, so a row that looks
short has a visible reason.

**Two pins on one person can leave a week with no answer**, and the page says so instead of
letting you find out on Thursday. Three remote days out of five, never three in a row, means
holding somebody in the office on Lundi *and* Vendredi leaves them Mardi to Jeudi — a run of
three, and against the rules. So is Lundi and Mardi, and so is Jeudi and Vendredi. That save is
refused with the reason and nothing is stored. Any other pair is fine, and in a week with a férié
in it the room runs out one day sooner. A week that was already unplannable before the change is
not blamed on it, and the grid carries a standing warning while it stays that way.

**If the week was already planned**, both pages say so — the grid links straight to it when a pin
lands on a day that person is already remote on. The week is never re-rolled for you: those
assignments are what the team is working to, and discarding them is a decision, not a side
effect. Until somebody re-rolls, nothing either page records changes the chart.

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
./gradlew test    # 112 tests
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

| | Read the schedule | Export .xlsx | Ask for days | Generate / re-roll | Require days on site | Manage accounts | Manage holidays |
|---|---|---|---|---|---|---|---|
| **User** | yes | yes | for themselves | no | no | no | no |
| **Admin** | yes | yes | for anyone | yes | yes | yes | yes |

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

A **public holiday** closes its column: the heading carries the holiday's name, the cells are
hatched rather than merely empty, and the strip above the chart spells out the week's fériés and
the remote days each person gets in it.

A cell an admin has marked **on site** carries a dot: that person is needed in the office that
day, so the schedule never gave them one there. Under the chart, if your account is linked to a
roster name, a row of checkboxes lets you say which days you would rather be remote the next time
that week is rolled.

**←** and **→** step a week at a time — as do the arrow keys — and **Today** jumps back to the
current one. When the week on screen is the current one, today's column is tinted.
Navigation is not limited to weeks that exist: land on a week nobody has scheduled and you get an
empty state with a **Generate this week** button for exactly that week. That is how you schedule
any week from the page — the buttons always act on the week you are looking at.

A week that already has a schedule shows **Re-roll** and **Export .xlsx** instead. Generate never
overwrites: it refuses and says so, and re-rolling is a separate, explicit button. Same rule the
Thursday job follows, so nothing the team is already using gets discarded by a stray click.

**Every page wears the same bar**: the mark, the sections — Chart, Semaine, Fériés, Accounts,
with the one you are on marked and the ones you cannot open simply absent — then the theme switch,
who you are signed in as, and the way out. The section links carry the week you are looking at, so
stepping from the chart to the week grid and back stays on that week instead of snapping to today.
Under it, on the two pages that have a week, a strip carries **←**, the week, **→** and **Today**.
That is a strip rather than more bar because it belongs to the page and not to the site — and
because four sections, an email address and a date range do not fit on one line. It is all one
Thymeleaf fragment, `templates/fragments/chrome.html`, rather than a header per page, which is how
the pages drifted apart in the first place.

A **Light / Auto / Dark** switch sits in that bar, on every page. Auto follows the system setting
and is the default; picking a side stores it in the browser and an inline script applies it before
first paint, so switching never flashes the other theme. The choice is per browser, not shared.

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

Each day in the response carries the holiday closing it, if any, and the schedule carries the
`remotesPerPerson` that week was planned with — 2 or 1 in a week with holidays.

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

- exactly as many bits set as the week's quota — 3 normally, fewer in a holiday week (true by
  construction)
- no 3 bits in a row (the consecutive rule, one bit test: `mask & (mask>>1) & (mask>>2)`)
- nothing on a holiday, a vacation-return day, or a day they are needed in the office

That leaves **7 valid patterns** per person, out of 32 possible subsets — and crucially that
number does not grow with the team. Only daily capacity has to be tracked while backtracking.
The solver handles 200 people in well under a second.

Three more touches:

- **Fairness** — the people order and each person's pattern order are shuffled, so the same
  people do not always get the same days. Pass a seeded `Random` to make a run reproducible.
- **Balance** — patterns are tried least-loaded-day first, so a week lands on 10/10/10/9/9
  rather than 10/10/10/10/8. This only reorders the search, so no valid schedule is ruled out.
  A week with strong preferences in it lands less flat than that, by design.
- **Wishes** — people who asked for days are placed first, and each of them is offered the
  patterns granting the most of what they asked for before the rest. Like balance this is *only*
  an ordering: a preference never enters the pattern enumeration, so it cannot rule out a valid
  week and cannot bend the quota, the consecutive-day limit or the slots. Days somebody is needed
  in the office are the opposite — they go in with the holidays, as days the pattern may not
  touch at all.

Chasing wishes can walk the search into a corner. If a whole team asks for Lundi and Vendredi,
only four of them can have both — the rest of the week cannot absorb the others — and finding
that out means unwinding a long way. So the wish-first pass has a node budget, and past it the
week is planned with the wishes demoted to a tie-break, then without them. Every pass obeys every
rule; the later ones simply grant less of what was asked for. A week always comes out, in
milliseconds.

## Layout

```
solver/      ScheduleSolver, WeekPatterns — pure algorithm, no Spring or JPA
domain/      WeekSchedule, RemoteAssignment, AppUser, Holiday, RemotePreference,
             OnSiteDay — JPA entities
repository/  WeekScheduleRepository
service/     ScheduleService (solve + persist), WeekPlanService, HolidayCalendar,
             HolidayService, HolidaySeed,
             ScheduleExcelExporter, WeekStarts
scheduler/   WeeklyScheduleJob — the Thursday cron
web/         REST controller, page controller, DTO, problem-detail handler
config/      RemoteScheduleProperties, PublicHolidayProperties — the YAML binding

resources/db/changelog/   Liquibase master + change files
templates/schedule.html   the page (Thymeleaf)
templates/fragments/      the head, bar and footer every page shares
templates/admin/          accounts, holidays and the week grid
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

`./gradlew test` runs 112 tests. The 43 solver, `WeekStarts` and `HolidayCalendar` tests are plain
unit tests with no Spring context. The rest extend `AbstractPostgresIntegrationTest`, which starts a throwaway
**PostgreSQL 16 in Testcontainers** with Liquibase enabled and `ddl-auto=validate` — so every run
checks the changelog and the entities still agree. Docker must be running.

## Origin

Grew out of the [`remote`](../remote) command-line generator, which solved the same problem but
printed to a terminal and had nowhere to keep the result.
