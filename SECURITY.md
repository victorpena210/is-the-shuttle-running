# Security and privacy

Report suspected vulnerabilities privately to **victor@renatus.technology**.
Include the affected behavior and a minimal reproduction with fictional data.
Do not put employee records, credentials, private checkpoint links, or exploit
payloads against the live service in public issues.

## Repository handling

- Keep credentials in environment variables or hosting secret settings.
- Use fictional names, employee IDs, and schedules in fixtures and screenshots.
- Keep exports, uploads, database backups, and spreadsheet files outside Git.
- Review the staged diff before committing. `.gitignore` does not clean files
  already tracked or erase earlier commits.
- Enable GitHub secret scanning and push protection where available. These help
  detect credentials; they do not reliably identify employee names or schedules.

## If private data was committed

Restrict repository access while investigating. Rotate any exposed credentials.
Clean every affected branch and tag, plus old file paths, using GitHub's
[sensitive-data removal procedure](https://docs.github.com/en/authentication/keeping-your-account-and-data-secure/removing-sensitive-data-from-a-repository).
Coordinate cleanup of local clones so old history is not pushed back. GitHub
Support may be needed to purge cached commits or pull-request references.
Previously downloaded copies cannot be recalled.

See `PRIVACY_CLEANUP.md` for this project's data-retention behavior and historical
cleanup notes. Source cleanup must never be confused with deleting production
records or resetting a production database.
