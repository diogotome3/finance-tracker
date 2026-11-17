# Commands Reference

The commands used to develop and run this project, grouped by tool. Unless noted otherwise, run them from the **repository root** (`Finance_Tracker/`).

> This file grows as the project does. New commands are added as each phase introduces them.

---

## 1. Daily workflow

```bash
# Start the database
docker compose up -d db

# Run the backend (from backend/) with the dev profile
cd backend && ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev

# In another terminal: check that the app is healthy
curl -s localhost:8080/actuator/health

# Stop the backend: Ctrl+C in its terminal
# Stop the database (data is kept)
docker compose stop
```

---

## 2. Environment variables (`.env`)

| Command | What it does |
|---|---|
| `cp .env.example .env` | Creates your private `.env` from the committed template. Then edit the passwords and ports. |
| `grep POSTGRES_PASSWORD .env` | Shows the database password stored in `.env`. |
| `set -a; source .env; set +a` | Loads every variable from `.env` into the current terminal (`$DB_PORT`, `$POSTGRES_USER`…). `set -a` exports them to programs you start; `set +a` turns that off again. Needed once per terminal. |

- `.env` is **git-ignored** and holds real credentials. `.env.example` is committed and holds placeholders.
- The backend loads `.env` by itself (via `spring.config.import` in `application.yaml`). You don't need to `source` it before running the app.
- **Precedence:** a real environment variable beats the value in `.env`. For example, `DB_PASSWORD=wrong ./mvnw spring-boot:run` overrides the file.

---

## 3. Docker Compose (database)

| Command | What it does |
|---|---|
| `docker compose up -d db` | Starts the PostgreSQL container in the background (`-d` = detached). The first run downloads the image. |
| `docker compose ps` | Lists the containers and their status. Wait for **`(healthy)`** before starting the backend. |
| `docker compose logs db` | Shows the database logs. Add `-f` to follow them live (Ctrl+C to exit). |
| `docker compose stop` | Stops the containers but keeps them, so they restart quickly. |
| `docker compose down` | Removes the containers. **Data is kept** in the `pgdata` volume. |
| `docker compose down -v` | Removes the containers **and the volume**, which **deletes all data**. Use it for a fresh database or after changing the password in `.env`. |
| `docker compose config` | Prints the final configuration with `.env` values filled in. Use `--quiet` to only validate the file. |
| `docker volume ls` | Lists Docker volumes. Ours is `finance_tracker_pgdata`. |

---

## 4. PostgreSQL (`psql`)

| Command | What it does |
|---|---|
| `docker compose exec db psql -U finance -d finance_tracker` | Opens psql **inside the container**. No password needed, and the client version matches the server. |
| `docker compose exec db psql -U finance -d finance_tracker -c "SELECT 1;"` | Runs a single SQL command without opening the prompt. |
| `PGPASSWORD="$POSTGRES_PASSWORD" psql -h localhost -p "$DB_PORT" -U "$POSTGRES_USER" -d "$POSTGRES_DB"` | Connects from your machine through the published port, the same way the backend does. Run `set -a; source .env; set +a` first. |

**Inside psql**

| Command | What it does |
|---|---|
| `\l` | Lists databases |
| `\dt` | Lists tables |
| `\d table_name` | Describes a table (columns, constraints, indexes) |
| `\conninfo` | Shows the connection details (user, database, port) |
| `SELECT * FROM flyway_schema_history;` | Lists the Flyway migrations that have been applied (from issue #4) |
| `\q` | Quits psql |

SQL statements end with `;`. Backslash commands don't.

---

## 5. Backend (Maven wrapper)

Run these from **`backend/`**. `./mvnw` is the Maven wrapper: it downloads the right Maven version automatically, so you don't need Maven installed.

| Command | What it does |
|---|---|
| `./mvnw compile` | Compiles the code. A quick check that everything builds. |
| `./mvnw spring-boot:run` | Starts the application on http://localhost:8080. |
| `./mvnw spring-boot:run -Dspring-boot.run.profiles=dev` | Starts it with the **`dev` profile**, which also loads `application-dev.yaml` (SQL logging, health details). |
| `./mvnw test` | Runs the **unit tests** only (fast). *(from issue #5)* |
| `./mvnw verify` | Runs unit **and integration tests**. Integration tests use Testcontainers, so Docker must be running. *(from issue #5)* |
| `./mvnw clean` | Deletes `target/` (compiled files). Use it when the build behaves strangely. |
| `./mvnw dependency:tree` | Shows every dependency and where it comes from. Add `-Dincludes=org.springdoc` to filter. |
| `./mvnw -B ...` | Batch mode, with no colours or progress bars. Used in CI. |

Stop a running app with **Ctrl+C**.

---

## 6. Checking the running app (`curl`)

| Command | What it does |
|---|---|
| `curl -s localhost:8080/actuator/health` | Health check. `"status":"UP"` means the app is running; with the `dev` profile it also shows `"db"`. `-s` = silent (no progress bar). |
| `curl -s localhost:8080/actuator/health \| jq` | Same, pretty-printed (requires `jq`: `sudo apt install jq`). |
| `curl -i localhost:8080/some/path` | `-i` also shows the response status and headers, for example `401 Unauthorized` for protected endpoints. |

**Useful URLs** (once the app is running)

| URL | What |
|---|---|
| http://localhost:8080/actuator/health | Health check |
| http://localhost:8080/swagger-ui.html | Interactive API documentation (Swagger UI) |
| http://localhost:8080/v3/api-docs | Raw OpenAPI specification (JSON) |

---

## 7. Git

| Command | What it does |
|---|---|
| `git status` | Shows changed, new and staged files. Check it before every commit. |
| `git add <path>` | Stages files for the next commit. |
| `git commit -m "type: title" -m "Closes #N"` | Commits with a title and a second paragraph. `Closes #N` closes issue N when the commit reaches `main`. |
| `git push` | Uploads commits to GitHub. |
| `git pull` | Downloads and merges new commits from GitHub. |
| `git log --oneline -5` | Shows the last 5 commits, one line each. |
| `git checkout main` | Switches to the `main` branch. |
| `git checkout -b <type>/<name>` | Creates a new branch and switches to it, e.g. `feat/categories-api`. |
| `git push -u origin <branch>` | First push of a new branch. `-u` links it to GitHub, so later pushes are just `git push`. |
| `git diff` | Shows unstaged changes. Use `git diff --staged` for staged ones. |

**Commit types:** `feat` (new feature), `fix` (bug fix), `test`, `refactor`, `docs`, `build` (build or dependencies), `ci`, `chore` (maintenance).

---

## 8. GitHub CLI (`gh`)

| Command | What it does |
|---|---|
| `gh auth status` | Shows whether you're logged in. |
| `gh repo view --web` | Opens the repository in the browser. |
| `gh issue list` | Lists open issues. Add `--milestone "Phase 1: Backend skeleton"` to filter. |
| `gh issue view <N>` | Shows issue N. |
| `gh issue close <N>` | Closes issue N by hand. |
| `gh pr create --fill` | Opens a pull request from the current branch, using the commit message. |
| `gh pr merge --squash --delete-branch` | Merges the PR as one commit, deletes the branch, switches to `main` and pulls. |
| `gh pr checks` | Shows the CI status of the current PR *(from issue #6)*. |
| `gh run watch` | Follows the current CI run live *(from issue #6)*. |

**Workflow with a PR** (from issue #6 on):
```bash
git checkout main && git pull
git checkout -b feat/<name>
# ...work and commit...
git push -u origin feat/<name>
gh pr create --fill
gh pr merge --squash --delete-branch
```

---

## 9. Troubleshooting

| Problem | Fix |
|---|---|
| `port is already allocated` when starting the database | Something else uses the port (e.g. a local PostgreSQL on 5432). Set another `DB_PORT` in `.env` (we use 5433) and update the port in `DB_URL` too. |
| `password authentication failed` | The database volume was created with a different password. Run `docker compose down -v && docker compose up -d db` (deletes the data). |
| `Connection refused` from the backend | The database isn't running. Run `docker compose up -d db` and wait for `(healthy)`. |
| `Port 8080 was already in use` | Another app instance is still running. Stop it with Ctrl+C, or find it with `ss -ltnp \| grep 8080`. |
| VS Code: "declared package does not match the expected package" | VS Code didn't import the Maven project. Check that `java.jdt.ls.java.home` points to JDK 25, then run **Java: Clean Java Language Server Workspace**. |
| `java -version` shows the wrong version | `sudo update-alternatives --config java` (and `javac`), then open a new terminal. |
