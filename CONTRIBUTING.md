# Contributing to ClaimShift

Bug reports, translations, documentation fixes, compatibility fixes, and focused code contributions are welcome.

Before opening a pull request:

1. Build and test with JDK 25 and Minecraft 26.2.
2. Keep changes focused and explain the problem being solved.
3. Do not introduce Spigot compatibility code. ClaimShift targets Paper, Purpur, Leaf, and Folia.
4. Avoid NMS unless there is a strong, discussed reason.
5. Keep configuration keys in English and stable across locales.
6. Never replace configured values or genuinely customized messages during locale/config migration.
7. Preserve WorldGuard recovery behavior when changing dynamic passthrough logic.
8. Preserve Folia-safe scheduling; do not assume one universal main thread.
9. Add or update tests/documentation for behavior changes.
10. Do not commit build output, IDE state, credentials, tokens, or private server data.

For behavior changes, include the relevant cases from `docs/TESTING.md` in your manual verification notes.

By intentionally submitting a contribution, you agree to the contribution terms in the ClaimShift Software License.
