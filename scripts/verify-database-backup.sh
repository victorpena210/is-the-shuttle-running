#!/usr/bin/env bash
set -euo pipefail

BACKUP_FILE="${1:-}"
if [[ -z "$BACKUP_FILE" || ! -f "$BACKUP_FILE" ]]; then
  echo "Usage: $0 path/to/routepulse-backup.sql.gz" >&2
  exit 2
fi

if ! command -v docker >/dev/null 2>&1; then
  echo "Docker is required for the isolated restore drill." >&2
  exit 2
fi

if ! gzip -t "$BACKUP_FILE"; then
  echo "Backup gzip integrity check failed." >&2
  exit 1
fi

CONTAINER="routepulse-restore-drill-$$"
ROOT_PASSWORD="routepulse-restore-drill-only"
DATABASE="routepulse_restore_drill"

cleanup() {
  docker rm -f "$CONTAINER" >/dev/null 2>&1 || true
}
trap cleanup EXIT

echo "Starting isolated MySQL 8.4 restore target..."
docker run -d \
  --name "$CONTAINER" \
  -e MYSQL_ROOT_PASSWORD="$ROOT_PASSWORD" \
  -e MYSQL_DATABASE="$DATABASE" \
  mysql:8.4 >/dev/null

for _ in {1..60}; do
  if docker exec \
      -e MYSQL_PWD="$ROOT_PASSWORD" \
      "$CONTAINER" \
      mysqladmin --user=root ping --silent >/dev/null 2>&1; then
    break
  fi
  sleep 1
done

if ! docker exec \
    -e MYSQL_PWD="$ROOT_PASSWORD" \
    "$CONTAINER" \
    mysqladmin --user=root ping --silent >/dev/null 2>&1; then
  echo "Restore-drill MySQL container did not become ready." >&2
  exit 1
fi

echo "Restoring backup into isolated database..."
gzip -dc "$BACKUP_FILE" | docker exec -i \
  -e MYSQL_PWD="$ROOT_PASSWORD" \
  "$CONTAINER" \
  mysql --user=root "$DATABASE"

TABLE_COUNT="$(docker exec \
  -e MYSQL_PWD="$ROOT_PASSWORD" \
  "$CONTAINER" \
  mysql --batch --skip-column-names --user=root "$DATABASE" \
  -e "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='${DATABASE}';")"

if [[ "$TABLE_COUNT" -lt 1 ]]; then
  echo "Restore drill failed: restored database contains no tables." >&2
  exit 1
fi

if ! docker exec \
    -e MYSQL_PWD="$ROOT_PASSWORD" \
    "$CONTAINER" \
    mysql --batch --skip-column-names --user=root "$DATABASE" \
    -e "SELECT COUNT(*) FROM flyway_schema_history;" >/dev/null; then
  echo "Restore drill failed: Flyway history table is missing or unreadable." >&2
  exit 1
fi

echo "Restore drill passed: $TABLE_COUNT tables restored into disposable MySQL 8.4."
