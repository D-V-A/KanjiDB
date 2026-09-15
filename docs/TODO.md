# Plans and TODO

Current implementation and limitations: [PROJECT_CONTEXT.md](PROJECT_CONTEXT.md).
Checked items are complete; existing placeholders do not count as completed milestones.

## Milestones

- [ ] **My Kanji:** persist personal Known/Learning states in a separate Room database and implement the collection screen.
- [ ] **Jōyō Kanji Browser:** browse levels and support bulk Known/Learning assignment.
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
- [ ] Define and implement a dictionary.db version/update mechanism.
- [ ] Verify EDRDG attribution and licenses.
- [ ] Verify KanjiVG attribution/license before integration.
- [ ] Implement Kanji of the Day using a deterministic local-date hash and a stable Unicode codepoint list, respecting discovery eligibility.
- [ ] Download the chosen icon set and replace temporary/text placeholders.
- [ ] Visual polish later.
