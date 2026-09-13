# Typed projection arity policy

Titan DSL provides typed `DSL.select(...)` overloads, `SelectBuilder1..10`,
`Tuple1..10`, and matching callback shapes for projections of up to ten columns.
This is an intentional API boundary.

## Why the cap exists

Every additional arity adds builder, tuple, overload, and callback surfaces that
need consistent behavior and testing. Ten covers many compact projections while
keeping the public API manageable.

The tuple and builder classes are generated from templates in `titan-dsl/build.gradle.kts`.
Run `./gradlew :titan-dsl:generateAritySources` to regenerate them; do not edit files under
`titan-dsl/build/generated/` or add manual `Tuple11` classes.

## Wider projections

The general `DSL.select(Column<?>...)` builder can describe wider SQL projections.
Use explicit result mapping in the application, preferably into named records
when a row has a meaningful domain shape. The library's `fetchInto(recordType)`
checks a mapping shape and returns SQL text; it does not read JDBC results or
construct the records.

See the [README](../README.md#typed-projections-and-convenience-methods)
for the difference between typed projection shapes and execution.

## Reconsidering the cap

A larger fixed cap is a design change, not a missing-overload bug. Revisit it
when concrete user queries show that the general builder and application record
mapping are insufficient. Any change should update source-generation templates,
public overloads, callback shapes, documentation, and tests together.
