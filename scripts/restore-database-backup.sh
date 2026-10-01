#!/usr/bin/env bash
set -euo pipefail

BACKUP_FILE="${1:-}"
if [[ -z "$BACKUP_FILE" || ! -f "$BACKUP_FILE" ]]; then
  echo "Usage: CONFIRM_ROUTE_PULSE_RESTORE=YES $0 backup.sql.gz" >&2
  exit 2
fi

if [[ "${CONFIRM_ROUTE_PULSE_RESTORE:-}" != "YES" ]]; then
  echo "Restore blocked. Set CONFIRM_ROUTE_PULSE_RESTORE=YES only after" >&2
  echo "confirming the target database is the intended recovery target." >&2
  exit 2
fi

required=(DB_RESTORE_HOST DB_RESTORE_NAME DB_RESTORE_USERNAME DB_RESTORE_PASSWORD)
for variable in "${required[@]}"; do
  if [[ -z "${!variable:-}" ]]; then
    echo "Missing required environment variable: $variable" >&2
    exit 2
  fi
done

if ! command -v mysql >/dev/null 2>&1; then
  echo "mysql client is required (MySQL 8.x recommended)." >&2
  exit 2
fi

gzip -t "$BACKUP_FILE"
RESTORE_PORT="${DB_RESTORE_PORT:-3306}"
export MYSQL_PWD="$DB_RESTORE_PASSWORD"

echo "Restoring $BACKUP_FILE into $DB_RESTORE_HOST:$RESTORE_PORT/$DB_RESTORE_NAME"
gzip -dc "$BACKUP_FILE" | mysql \
  --host="$DB_RESTORE_HOST" \
  --port="$RESTORE_PORT" \
  --user="$DB_RESTORE_USERNAME" \
  "$DB_RESTORE_NAME"

mysql \
  --host="$DB_RESTORE_HOST" \
  --port="$RESTORE_PORT" \
  --user="$DB_RESTORE_USERNAME" \
  "$DB_RESTORE_NAME" \
  --execute="SELECT COUNT(*) AS applied_migrations FROM flyway_schema_history;"

unset MYSQL_PWD
echo "Restore completed. Run the RoutePulse smoke-test checklist before reopening traffic."
