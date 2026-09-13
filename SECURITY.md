# Security

Titan DSL is in early development. Security fixes currently target the latest
`main` revision; there are no maintained stable release branches yet.

## Reporting a vulnerability

Do not post sensitive exploit details or credentials in a public issue. Use
**Report a vulnerability** in the repository's Security tab. Private vulnerability
reporting is enabled:

<https://github.com/rbilleci/titan-dsl/security/advisories/new>

If private reporting is temporarily unavailable, open an issue requesting a
private security contact without including exploit details. The maintainer must
establish that channel before you share sensitive information. No response-time
SLA is offered.

## Safe use and boundaries

- Prefer `DSL.using(dialect)` queries with `render()` and bind their ordered
  parameters when executing SQL. Static unconfigured queries still require
  `render(dialect)`. `toSql()` emits literal text, not a prepared-statement API.
- Treat identifiers and raw SQL fragments as trusted developer input. In
  particular, `Condition.of(...)`, raw field expressions, table/column names,
  and aliases must not contain untrusted input. Runtime rendering does not
  universally quote identifiers or validate raw fragments.
- Bind parameters protect values, not SQL structure. Do not concatenate user
  input into SQL or identifiers before passing it to the DSL.
- Rendered literals and bind values can contain sensitive data; avoid logging
  them in production.
- Some composition helpers capture literal SQL before outer rendering. Read the
  [composition and binding boundaries](README.md#composition-and-binding-boundaries)
  before placing request values in CTEs, inline views, or scalar subqueries.
- Rendering tests do not certify deployment permissions or every database
  version. Review and validate queries on the intended target.

The library does not open database connections, execute SQL, or enforce application
authorization. Execution permissions and database validation belong to the caller.
