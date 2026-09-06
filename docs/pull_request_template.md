## Change

State the problem and the resulting behaviour. Link the relevant feature document or ADR when one applies.

## Validation

State which checks ran and their results. Separate automated checks from live server tests. State any checks that remain unverified.

## Compatibility

State changes to server requirements, configuration, Slack scopes or existing behaviour. Write "None" when the change has no compatibility impact.

## Review

- [ ] The change has one clear scope.
- [ ] The relevant checks pass, or a blocker is stated.
- [ ] Documentation matches the implementation.
- [ ] Configuration and dependency changes follow [the conventions](conventions.md).
- [ ] The change preserves work outside its scope.
