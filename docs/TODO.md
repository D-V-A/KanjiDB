# Plans and TODO

Current implementation and limitations: [PROJECT_CONTEXT.md](PROJECT_CONTEXT.md).
Checked items are complete; existing placeholders do not count as completed milestones.

## Milestones

- [x] **My Kanji:** persist personal Known/Learning states in a separate Room database and connect the collection with Kanji Details, including multi-selection Move/Remove.
- [x] **Kanji Groups / Jōyō Kanji Browser:** JLPT/Grade grouping, Frequency/Strokes sorting, JLPT/Grade/Jōyō/Status rules and transactional bulk Known/Learning overwrite. Shares selection/grid/panel infrastructure with My Kanji; accessed through the swipe pager. My Lists remains a placeholder and Training actions remain disabled.
- [x] **Recommended Kanji:** JLPT progression, process-lifetime candidate pool, shared discovery quality gate, weighted sampling, Refresh anti-repeat and incremental Room updates.
- [ ] **Training:** implement practice sessions with answers written on paper.
- [ ] **User Lists:** create/manage custom lists and enable assignment from details.
- [ ] **Stroke Order / KanjiVG:** integrate stroke-order data after checking attribution/license.

## TODO

- [ ] Global Settings and recommendation strategy selector; implement Grade/Frequency/Rare strategies later.
- [ ] Manually verify 0.4.0 Recommended on a phone: tab tap/swipe, Refresh anti-repeat, Details -> Back with/without state change, updates from My Kanji/Groups/bulk actions, Activity recreation and process restart.

- [x] Keep collection tabs fixed and collapse only page-owned controls with shared nested scroll; reset controls after tab changes.
- [x] Add shared Group by / Sort by / Rules inside My Kanji Learning/Known, including selectable JLPT/Grade groups and nested technical Ranked/Unranked sections.
- [ ] Manually verify 0.3.4 collection UX on a phone: fixed tabs/page controls during tap/swipe, controls reset, nested header selection, card clearance, section restoration and Details -> Back in both collections.

- [x] Persist one manual order per Learning/Known with Room v2 migration; allow selection-only 250 ms hold/drag in ungrouped, unfiltered Manually mode, including edge autoscroll and drop deselection.
- [ ] On a phone, verify upgrade from Room v1 with existing records, manual order after restart, append-on-Move/Add, drag delay versus tap/entry long press, multi-screen edge scrolling in both directions, cancel/failure behavior, controls omission/restoration, Snackbar entry timing and forbidden reorder configurations.

- [x] Capitalise standalone English kanji meanings in the UI.
- [x] Make dictionary text selectable/copyable.
- [ ] Implement Recommended words.
- [ ] Improve word ranking beyond common=1.
- [ ] Improve presentation of huge reading sets such as 生.
- [ ] Test that the Jōyō badge is absent for joyo=0.
- [ ] Correct the JLPT source mapping for 分 (expected N5): the current local tools/data/jlpt.tsv omits it, and the asset import matches that source. No runtime override is used.
- [ ] Define and implement a dictionary.db version/update mechanism.
- [ ] Verify EDRDG attribution and licenses.
- [ ] Verify KanjiVG attribution/license before integration.
- [ ] Implement Kanji of the Day using a deterministic local-date hash and a stable Unicode codepoint list, respecting discovery eligibility.
- [ ] Download the chosen icon set and replace temporary/text placeholders.
- [ ] Visual polish later.
