# Plans and TODO

Current implementation and limitations: [PROJECT_CONTEXT.md](PROJECT_CONTEXT.md).
Checked items are complete; existing placeholders do not count as completed milestones.

## Milestones

- [x] **My Kanji:** persist personal Known/Learning states in a separate Room database and connect the collection with Kanji Details, including multi-selection Move/Remove.
- [x] **Kanji Groups / Jōyō Kanji Browser:** JLPT/Grade grouping, Frequency/Strokes sorting, JLPT/Grade/Jōyō/Status rules and transactional bulk Known/Learning overwrite. Shares selection/grid/panel infrastructure with My Kanji; accessed through the swipe pager. My Lists remains a placeholder and Training actions remain disabled.
- [ ] **Recommended Kanji:** implement automatic suggestions using sufficiently confirmed Japanese kanji; follow the discovery/Search distinction in project context.
- [ ] **Training:** implement practice sessions with answers written on paper.
- [ ] **User Lists:** create/manage custom lists and enable assignment from details.
- [ ] **Stroke Order / KanjiVG:** integrate stroke-order data after checking attribution/license.

## TODO

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
