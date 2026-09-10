#!/usr/bin/env bash
set -euo pipefail

required=(DB_BACKUP_HOST DB_BACKUP_NAME DB_BACKUP_USERNAME DB_BACKUP_PASSWORD)
for variable in "${required[@]}"; do
  if [[ -z "${!variable:-}" ]]; then
    echo "Missing required environment variable: $variable" >&2
    exit 2
  fi
done

if ! command -v mysqldump >/dev/null 2>&1; then
  echo "mysqldump is required (MySQL 8.x recommended)." >&2
  exit 2
fi

BACKUP_PORT="${DB_BACKUP_PORT:-3306}"
OUTPUT_DIR="${1:-./backups}"
mkdir -p "$OUTPUT_DIR"
chmod 700 "$OUTPUT_DIR" 2>/dev/null || true

STAMP="$(date -u +%Y%m%dT%H%M%SZ)"
OUTPUT_FILE="$OUTPUT_DIR/routepulse-${STAMP}.sql.gz"
CHECKSUM_FILE="$OUTPUT_FILE.sha256"

# MYSQL_PWD avoids leaking the password in the process list. It is scoped to
# this script and unset immediately after the dump command completes.
export MYSQL_PWD="$DB_BACKUP_PASSWORD"
set +e
mysqldump \
  --host="$DB_BACKUP_HOST" \
  --port="$BACKUP_PORT" \
  --user="$DB_BACKUP_USERNAME" \
  --single-transaction \
  --quick \
  --triggers \
  --hex-blob \
  --set-gtid-purged=OFF \
  --no-tablespaces \
  "$DB_BACKUP_NAME" | gzip -9 > "$OUTPUT_FILE"
PIPE_STATUSES=("${PIPESTATUS[@]}")
set -e
unset MYSQL_PWD

DUMP_STATUS=${PIPE_STATUSES[0]}
GZIP_STATUS=${PIPE_STATUSES[1]}
if [[ "$DUMP_STATUS" -ne 0 || "$GZIP_STATUS" -ne 0 ]]; then
  rm -f "$OUTPUT_FILE"
  echo "Database backup failed." >&2
  exit 1
fi

chmod 600 "$OUTPUT_FILE"
gzip -t "$OUTPUT_FILE"

if command -v shasum >/dev/null 2>&1; then
  shasum -a 256 "$OUTPUT_FILE" > "$CHECKSUM_FILE"
elif command -v sha256sum >/dev/null 2>&1; then
  sha256sum "$OUTPUT_FILE" > "$CHECKSUM_FILE"
else
  echo "Warning: no SHA-256 utility found; checksum was not created." >&2
  CHECKSUM_FILE=""
fi

[[ -z "$CHECKSUM_FILE" ]] || chmod 600 "$CHECKSUM_FILE"
echo "Backup created: $OUTPUT_FILE"
[[ -z "$CHECKSUM_FILE" ]] || echo "Checksum: $CHECKSUM_FILE"
