# Schema Changelog

All breaking changes, field deprecations, and new additions to the GraphQL supergraph must be recorded here before merging.

## Format

```
## [date] — [subgraph] — [change type: BREAKING | ADDITIVE | DEPRECATION | REMOVAL]
Short description of the change and the reason.
Fields affected: `TypeName.fieldName`
Migration: how consumers should adapt (for breaking/removal)
```

---

## [2026-06-16] — Initial schema — ADDITIVE

Initial supergraph schema across all five subgraphs:
- `member`: `Member` entity, `register` mutation, `member` query
- `billing`: `Plan`, `Subscription` types; extends `Member` with `subscription`
- `profile`: `Profile` type; extends `Member` with `profiles`
- `entitlement`: `StreamEntitlement`; extends `Member` with `canStream`; stream acquisition mutations
- `discovery`: `HomeScreen`, `BrowseScreen` SDUI queries with `DiscoveryComponent` union
