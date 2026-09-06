# Architecture decision records

Record decisions that change a runtime boundary, dependency strategy, data contract or supported platform.

- Use one file per decision, named `NNNN-short-title.md`.
- State the date, status, context, decision and consequences.
- Use `proposed`, `accepted` or `superseded` as the status.
- Keep proposals separate from accepted decisions.
- Link the record from the architecture or feature document it affects.
- Supersede an accepted record with a new record. Keep the old rationale available.
- Do not invent a decision history for undocumented legacy code.

## Records

| Record | Status | Decision |
| --- | --- | --- |
| [0001-target-minecraft-26-2.md](0001-target-minecraft-26-2.md) | Accepted | Target Spigot/Paper 26.2 and Java 25. |
| [0002-build-releases-locally.md](0002-build-releases-locally.md) | Accepted | Build and publish releases locally through mise and GitHub CLI. |

This index starts with the current modernisation. It is not a complete history of the plugin.
