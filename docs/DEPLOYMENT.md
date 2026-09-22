# Deploying 3Color

3Color can run as a Java 21 application or as a Docker image. PostgreSQL is
optional. Without it, the web application uses the bundled puzzle bank and live
search.

PostgreSQL adds persistent puzzle supply and a separate discovery worker. The web
process and worker use the same application image or executable JAR with different
roles.

PostgreSQL 17 is the tested database version.

## Contents

* [Docker Compose](#docker-compose)
* [Database setup](#database-setup)
* [Public access](#public-access)
* [Running without Docker](#running-without-docker)
* [Puzzle supply and replay](#puzzle-supply-and-replay)
* [Discovery worker](#discovery-worker)
* [Pausing and inspecting discovery](#pausing-and-inspecting-discovery)
* [Resource limits](#resource-limits)
* [Backups](#backups)
* [Local database tests](#local-database-tests)
* [Deployment status](#deployment-status)

## Docker Compose

The included `compose.yml` runs two services.

`web` serves the browser game and REST API on port 18380.

`worker` searches for additional certified puzzles and stores them in PostgreSQL.
It has no public port.

The Compose setup expects PostgreSQL to be managed separately. It does not create
a database container or database storage.

After configuring the database, build and start both services with:

```sh
docker compose up -d --build
```

Follow their logs with:

```sh
docker compose logs -f web worker
```

The game is available at:

```text
http://localhost:18380
```

To run only the game:

```sh
docker compose up -d --build web
```

Database migrations run automatically during startup. The bundled certified
puzzle bank is imported transactionally once for each bank and model version.

The health endpoint is:

```text
/actuator/health
```

Use it for a reverse proxy, process monitor or container health check.

Initial puzzle bank validation can take time on slower hardware. Allow a generous
startup window and adjust it after measuring the target machine. Around 600
seconds is a conservative starting point for Raspberry Pi class hardware.

## Database setup

Create a PostgreSQL database and a dedicated user. The user must be able to create
and modify tables in its schema so Flyway migrations can run.

Copy the example environment file:

```sh
cp .env.example .env
```

Set:

```text
THREECOLOR_DB_URL=jdbc:postgresql://DATABASE_HOST:5432/DATABASE_NAME
THREECOLOR_DB_USER=YOUR_DATABASE_USER
THREECOLOR_DB_PASSWORD=YOUR_DATABASE_PASSWORD
```

The URL must use PostgreSQL JDBC syntax. A `postgres://` URI is not accepted.

For hosted databases, add any required TLS parameters to the JDBC URL and make
required certificates available to the containers.

The database hostname must be reachable from both application containers.
`localhost` inside a container refers to that container.

If PostgreSQL also runs in Docker, attach the 3Color services to the appropriate
Docker network with a local Compose override, or use another reachable address.

The local `.env` file is excluded from Git and from the Docker build context.

A database startup failure stops a database enabled process rather than starting
a worker with nowhere to save its results.

## Public access

The Compose file binds the web service to loopback by default:

```text
THREECOLOR_BIND_ADDRESS=127.0.0.1
THREECOLOR_PORT=18380
```

Change these values in `.env` when another host binding or port is required.

For public deployment, place an HTTPS reverse proxy in front of the web service.
A proxy on the same Docker network can connect to:

```text
web:18380
```

The worker does not need a public port. Database credentials are not sent to the
browser, and the application exposes no public database administration endpoint.

## Running without Docker

Build the frontend and executable JAR:

```sh
(cd web && npm ci && npm run build)
./gradlew :api:bootJar
```

Run the application without PostgreSQL:

```sh
java -jar api/build/libs/three-color.jar
```

To enable persistent supply, set `THREECOLOR_SUPPLY_ENABLED=true` together with
the database variables from [Database setup](#database-setup), then start the web
process with the same command.

Start discovery as a separate process:

```sh
THREECOLOR_ROLE=worker java -jar api/build/libs/three-color.jar
```

The worker needs the same database environment as the web process.

Use a service manager such as systemd when automatic restarts and resource limits
are required. Unexpected discovery failures exit the worker with status 1 so a
supervisor can restart it. Temporary database failures during discovery are
retried.

A `.env` file is read by Docker Compose. Java does not read it automatically when
the JAR is started directly.

## Puzzle supply and replay

Random requests can use stored puzzles in the requested difficulty and size.
Recently seen topologies are excluded using browser history.

A stored puzzle is independently recertified before it is served. If no suitable
stored puzzle is available, the normal bundled bank and live search path remains
available.

The database stores the graph, clues, layout, origin seed, provenance, proof model
version and player model version. It does not store hidden solutions or complete
explanation traces.

Generation depends on code, model and puzzle bank versions, so a seed alone is
not permanent archival data. Explicit numeric seed requests bypass database
supply and use the normal generator replay path.

Stored puzzle responses contain a `supplyId`. A stored puzzle can be loaded
directly with:

```text
GET /api/v1/puzzles/supply/{supplyId}
```

This path does not use browser history filtering. Stored records must match the
current proof and player models.

The puzzle JSON returned by the API remains the portable saved representation.

Deduplication uses a conservative topology fingerprint. Equal fingerprints can
cause distinct graphs to be treated as duplicates. The fingerprint is not an
exact graph isomorphism test and does not guarantee different human solving
experiences.

Puzzles are reusable across players and are not consumed when served.

## Discovery worker

The worker cycles through the 18 supported size and difficulty cells. It uses the
same bounded search as normal generation, and existing bank puzzles can be used as
parents for edited graph candidates.

Seeds and aggregate outcomes are stored after every completed attempt. When a
puzzle is accepted, the puzzle and corresponding search progress are committed
together.

If a worker stops during an unfinished attempt, the seed can be retried after its
lease expires. The default lease duration is 600 seconds.

Multiple workers can use the same database. Discovery uses fenced leases, so a
worker that loses a lease cannot commit results for it.

The discovery controls are:

| Variable | Default | Meaning |
| --- | ---: | --- |
| `THREECOLOR_WORKER_TARGET_PER_CELL` | `0` | Continue without a count target, a positive value sets a target for each cell |
| `THREECOLOR_WORKER_INTERVAL_SECONDS` | `10` | Pause between bounded attempts and idle polls |

With the default target of `0`, discovery continues across all supported cells.
With a positive target, a cell becomes idle when it reaches that count.

Harder cells can have a low yield. Continuous discovery does not guarantee a
successful attempt and never relaxes certification or clue limits.

## Pausing and inspecting discovery

Stop discovery without stopping the game:

```sh
docker compose stop worker
```

Resume it with:

```sh
docker compose start worker
```

Discovery can also be paused through PostgreSQL after the active attempt finishes:

```sql
UPDATE discovery_control
SET paused = true
WHERE singleton = true;
```

Resume with:

```sql
UPDATE discovery_control
SET paused = false
WHERE singleton = true;
```

Inspect stored puzzle counts:

```sql
SELECT category, node_count, count(*)
FROM puzzle_supply
WHERE quarantine_reason IS NULL
GROUP BY category, node_count
ORDER BY category, node_count;
```

Inspect discovery progress:

```sql
SELECT search_version,
       category,
       size,
       next_seed,
       attempts,
       added,
       duplicates,
       failures,
       last_outcome,
       last_duration_ms,
       lease_until
FROM discovery_cell
ORDER BY search_version, category, size;
```

Search cursor versions include the generator version, range search version, proof
model version, player model version and bundled bank hash. A version change starts
a new cursor namespace while existing puzzle records remain available.

## Resource limits

The default Docker limits are:

```text
THREECOLOR_WEB_MEMORY=1g
THREECOLOR_WORKER_MEMORY=1g
THREECOLOR_WORKER_CPUS=0.5
```

Adjust them to the target machine. PostgreSQL and other services also need memory,
and Docker image builds can need more memory than the final runtime containers.

For a 64 bit Raspberry Pi, use Linux ARM64 images. If local image builds are too
heavy for the device, build on another machine and transfer or publish the image.

## Backups

The PostgreSQL database contains the persistent puzzle catalogue and discovery
progress. Container filesystems are disposable.

Back up PostgreSQL with the tools provided by the database host, or with
`pg_dump`. Test restoration as part of the backup process.

## Local database tests

The database integration tests use a unique temporary schema and remove it
afterward. The test user needs permission to create schemas.

Run them with:

```sh
THREECOLOR_TEST_DB_URL=jdbc:postgresql://localhost:5432/threecolor_test \
THREECOLOR_TEST_DB_USER=threecolor_test \
THREECOLOR_TEST_DB_PASSWORD=YOUR_TEST_PASSWORD \
./gradlew :api:test --tests io.threecolor.supply.PuzzleSupplyTest
```

Without `THREECOLOR_TEST_DB_URL`, these tests are skipped. They do not drop or
clean unrelated database schemas.