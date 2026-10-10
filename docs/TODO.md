# Plans and TODO

Current implementation and limitations:
[PROJECT_CONTEXT.md](PROJECT_CONTEXT.md).

Checked items are complete. Existing placeholders do not count as
completed milestones. This file is the forward-looking project plan;
exact implementation details for future features should be decided when
that feature is started unless explicitly fixed below.

## Completed milestones

-   [x] **My Kanji:** persist personal Known/Learning states in a
    separate Room database and connect the collection with Kanji
    Details, including multi-selection Move/Remove.
-   [x] **Kanji Groups / Jōyō Kanji Browser:** JLPT/Grade grouping,
    Frequency/Strokes sorting, JLPT/Grade/Jōyō/Status rules and
    transactional bulk Known/Learning overwrite. Shares
    selection/grid/panel infrastructure with My Kanji.
-   [x] **Recommended Kanji:** JLPT progression, process-lifetime
    candidate pool, shared discovery quality gate, weighted sampling,
    Refresh anti-repeat and incremental Room updates.
-   [x] **Kanji Training v1:** Review/Learning/New, paper recall and
    self-assessment, repeat attempts, full-pool Results and pending
    Learning/Known changes applied at Finish or explicit bulk-and-finish
    actions.
-   [x] **Custom Lists / My Lists (0.6.x):** Room many-to-many
    memberships with non-destructive migration, CRUD/list reorder,
    shared filtered kanji grids/manual order, staged single/bulk
    tri-state dialogs and My Lists Training source.
-   [x] **Collection UI foundation:** fixed collection tabs, shared
    collapsible filters, Group by / Sort by / Rules, persisted manual
    order, selection and drag/reorder infrastructure.

## Current milestone

-   [ ] **Finish and manually verify Custom Lists / collection UI
    polish.**
    -   Unified collapsible filters behavior across My Kanji / My Lists
        / Kanji Groups.
    -   My Lists uses one tab-wide filters header; filters are not
        duplicated per list.
    -   Selection/reorder temporarily force filters collapsed without
        overwriting the user's normal expanded/collapsed state.
    -   Collapsed Show filters participates in normal scrolling.
    -   My Lists reorder hint matches the existing My Kanji hint.
    -   Kanji Groups allows additive selection across multiple groups;
        collapsing/expanding a group does not alter selection.
    -   My Kanji keeps section isolation; My Lists uses the existing
        multi-section selection behavior across lists (0.8.2-alpha).
    -   Custom List action dialog uses pill-style action buttons;
        selection controls remain rounded-rectangle where appropriate.
    -   Dragging has a temporary visual state independent of selection
        and does not change selection.
    -   Removing selected kanji from the active Custom List updates the
        visible list immediately; empty selection does not automatically
        exit selection mode.
    -   Keep Cancel on the left and confirmation/Apply on the right.
-   [ ] **Release/upgrade verification for the 0.6 milestone:** install
    the public 0.5.3-alpha build with existing Learning/Known data, then
    install the signed 0.6.x build over it and verify that Room
    migration preserves existing data and Custom Lists work correctly.
-   [ ] Verify Custom Lists CRUD/name validation, overlapping
    memberships, list reorder Apply/Cancel, staged membership dialog
    Apply/Cancel, Partial state, empty/nonempty lists and My Lists
    Training source on a phone.
-   [ ] Verify the current collection filters/selection/reorder behavior
    in both light and dark themes where visual state matters.

## Word Details 0.7.0 manual verification

- [ ] Today: primary reading follows JMdict order; remaining readings retain that order and the title gloss is absent from Meanings.
- [ ] Tomorrow: primary reading follows reading_order rather than reading_priority.
- [ ] A five-code-point form uses compact layout; a six-code-point form uses long layout. Also check supplementary characters if available.
- [ ] A one-gloss word has no Meanings section; a multi-gloss word has the first as title and only additional glosses below.
- [ ] Mixed kana/kanji shows only linked kanji; repeated kanji appears once in first-occurrence order.
- [ ] Long English kanji meanings occupy one secondary line with ellipsis; cards retain four-column geometry.
- [ ] A very long dictionary phrase wraps written form, glosses and readings within screen width and remains scrollable above the panel.
- [ ] Search -> Kanji Details -> Word Details -> Kanji Details: Back reverses one step; Return to origin returns directly to Search. Repeat visits must add history.
- [ ] Repeat Return to origin from My Kanji, My Lists, Kanji Groups and Recommended; preserve tab/filter/scroll state and existing Recommended return behavior. Check rotation within the chain.
- [ ] Return to origin is available on first details and during loading/errors; Word's remaining panel actions stay disabled. Check light/dark themes and larger font sizes.

## Related Words 0.7.2 manual verification

- [ ] 本: useful common words lead the first six; expand Show more and confirm at most one exact numeric #本 family member.
- [ ] 人: similarly inspect the #人 family; 一人 is currently the best representative, while longer lexical expressions remain available.
- [ ] 生 or another kanji with many words: compare short/common examples with long rare terms and confirm Show more remains usable.
- [ ] 一: confirm 一番 and 一部 remain, alongside other distinct lexicalized expressions. Review canonical numeral+whitelisted-counter words with secondary lexical senses.
- [ ] Long phrases: confirm they remain reachable with lower scores and still open Word Details correctly.
- [ ] Reopen the same kanji: identical Related Words order. Toggle Known/Learning or edit Custom Lists and confirm the order does not change.
- [ ] Compare examples with the current kanji itself rare/missing frequency; only other constituents should influence difficulty. Check older installed dictionaries lacking JLPT metadata if available.
- [ ] Measure loading/Show more on a phone for large candidate sets; desktop SQL timing does not establish device performance.

## Custom icons V0.8.3 manual verification

- [ ] Bottom navigation displays Search / Vocabulary / PencilCheck icons, preserving labels and selected state.
- [ ] Kanji Details Glyph/Strokes are icon-only with accessible labels; all three collection tabs show leading icons without text clipping.
- [ ] Show/Hide filters has trailing icons; Group by / Sort by and direction controls have leading icons. Verify Frequent first ascending / Rare first descending, group direction and disabled N/A.
- [ ] Training setup Back, End training and Correct/Incorrect retain text/action; check both Kanji and Word flows.
- [ ] Results success/partial/failure keeps previous colors; practice/Finish and all repeat options have consistent icons. Selection-panel Finish is unchanged.
- [ ] Search Info remains icon-only and opens About; Explore/Recommended refresh actions retain their text.
- [ ] Return to origin works in both detail screens and uses the shared definition; test accessible labels.
- [ ] Check disabled tint, narrow screens / larger fonts, light/dark themes and Credits with Tabler MIT notice.

## Selection counters and reorder hints (versionCode 28)

- [ ] My Kanji, manual sort / no grouping / no Rules: entering selection shows the drag hint and live selected count.
- [ ] My Kanji with any Rule, nonmanual sort or grouping: no drag hint; counter still updates.
- [ ] My Lists, exactly one expanded full-membership manual list / no Rules: selection entry shows the two-line hint and counter.
- [ ] My Lists with Rules (including Select list's kanji temporary filter bypass), nonmanual sort or multiple expanded lists: no drag hint; shared unique count still works.
- [ ] Across My Lists lists, count retains the combined set; hint states that reorder is within the current list and does not replay on list changes.
- [ ] Kanji Groups: same counter placement/style, no drag hint. Check all three with select/deselect/clear-to-zero and restored selection.

## My Lists cross-list selection 0.8.2 manual verification

- [ ] List A: select several kanji; Back / Back to My Lists returns to collapsed headers with selection and count intact.
- [ ] Open B and select more; count is the unique union. Shared kanji are already selected and deselect globally from either list.
- [ ] From collapsed C's long-press menu, Select list's kanji adds all C members without replacing earlier selections, including with normal filters active.
- [ ] Expanded-list header long press selects all / clears only its current displayed targets; kanji outside that list remain selected.
- [ ] Train Selected uses the combined unique pool; both warning/modal Cancel paths preserve it, acceptance clears it, Kanji starts directly and Word retains fixed-pool setup.
- [ ] Change tabs and return: My Lists state does not appear in My Kanji/Groups. Compare selection additions, duplicates and header toggles with Kanji Groups.
- [ ] With exactly one list expanded and manual/unfiltered sorting, drag reorders only that list without losing other-list selections. With multiple expanded lists, drag is unavailable. Include overlapping glyphs.
- [ ] Removing membership/deleting a list retains selected kanji still present elsewhere; rotation and overview return preserve count and do not replay card reveal.

## Next milestones

-   [x] **Fixed-pool Training (0.8.0-alpha):** Train Selected and entire
    Custom Lists share mode selection, >50-kanji soft warning, direct
    Kanji sessions and fixed-pool Word setup/session construction.
-   [ ] **Fixed-pool Training manual verification:**
    - Selection <=50 opens mode selection directly; >50 opens warning.
    - Cancel warning/mode menu preserves selection; mode acceptance clears it.
    - Kanji starts immediately with every selected/list kanji, including >50.
    - Word setup hides source/pool-size controls, shows Selected kanji: N,
      keeps all other settings and passes the complete pool to the usual builder.
    - >30 generated words still triggers the independent Word warning.
    - Word setup Back/bottom navigation discards the fixed pool without
      restoring the old selection; verify rotation in setup and sessions.
    - Entire-list Training ignores active visual filters; empty lists disable actions.
    - Select list's kanji selects/displays all members even with rules active;
      header long press clears them, repeating selects all again. Exiting
      selection restores the preceding filters.
    - My Kanji, My Lists and multi-group Kanji Groups header/subgroup toggles
      affect only their context, preserving other selected groups.
-   [ ] **Stroke Order / KanjiVG:** integrate stroke-path/order data
    after attribution/license verification.
-   [ ] **Handwriting Training:** build handwriting practice after
    stroke-order data is available. Exact recognition/validation UX
    should be designed at implementation time; preserve the possibility
    of results such as Wrong / Wrong Stroke Order / Correct.
-   [ ] **TTS / Japanese speech:** add pronunciation playback after the
    core training/handwriting work. Choose the implementation/backend
    when starting the feature rather than committing to one now.
-   [ ] **Post-release milestone — User data export/import:** add export 
    and import of user-owned data so study progress can be moved between 
	devices or transferred between users (for example, teacher → student). 
	Scope should cover personal data such as Learning/Known states and 
	Custom Lists, while keeping the bundled dictionary database separate. 
	Define file format, merge/replace behavior, compatibility/versioning 
	and validation when implementing the feature.

## Recommendations, discovery and home screen

-   [ ] Implement **Recommended Words**.
-   [ ] Add **Kanji of the Day** using a deterministic
    local-calendar-date hash and a stable Unicode codepoint list rather
    than dictionary IDs, while respecting discovery eligibility.
-   [ ] Revisit the Search/home discovery UI when Recommended Words /
    daily content is implemented. Current design direction: top-level
    Kanji / Word choice with a second row of discovery modes such as Of
    the Day / Recommended / Explore; finalize the exact Word-side daily
    behavior when implementing it.
-   [ ] Add **Global Settings** and a recommendation strategy selector;
    implement Grade / Frequency / Rare-or-Discovery strategies later.
-   [ ] Add a Settings option **"Include Custom Lists in Kanji
    recommendations"**. Define the exact recommendation behavior and
    semantics only when implementing the feature; do not assume whether
    lists exclude, boost, prioritize or otherwise affect candidates
    before then.
-   [ ] Future recommendation quality work: revisit meaning/reading
    usefulness ranking when needed. Control case: for 漢, prefer `China`
    as the primary training meaning over `Sino-`.

## Training backlog

-   [x] Word Training (v0.7.3-alpha): shared sources/setup, coverage-driven words, masks, partial results and explicit-word mistake repeats.
-   [ ] Manually validate Word Training source parity, pool size (max 50/step 5), coverage 1..5, all prompt/length settings, masks, numeral eligibility, counter dedup, coverage balancing, unique words, >30 confirmation, scoring/statuses, partial-safe bulk actions, exact mistake repeats and cancellation without writes.
-   [ ] Assess Word Training responsiveness on large pools and tune the conservative counter whitelist/usefulness floor only after real examples.
-   [ ] Future Kanji Training improvements: stroke order,
    components/radical Hint and TTS.
-   [ ] Consider weighting/SRS/statistics only if later feedback shows
    they are useful.
-   [ ] Preserve the current separation of concerns: Kanji Training
    focuses on meaning/readings and eventually glyph reproduction; Word
    Training should handle words/readings/context.

## Dictionary and data

-   [x] Static, user-agnostic Related Words ranking on Kanji Details (0.7.2-alpha).
-   [ ] Further assess Related Words weights/whitelist using manual examples; Search now has separate relevance ranking; Explore ranking remains future work.
-   [ ] Improve presentation of huge reading sets such as 生.
-   [ ] Test that the Jōyō badge is absent for `joyo=0`.
-   [ ] Correct the JLPT source mapping for 分 (expected N5) if the
    current source still omits it; keep source/import/verifier behavior
    coherent.
-   [ ] Define and implement a `dictionary.db` asset version/update
    mechanism so an APK update can replace the copied `noBackupFilesDir`
    dictionary when required.
-   [ ] Verify EDRDG attribution and licenses.
-   [ ] Verify KanjiVG attribution/license before integration.
-   [ ] Do not persist dictionary-internal SQLite IDs in user-owned
    data; continue using stable character text/codepoints where
    appropriate.
-   [ ] Add **Group by: My Lists** to My Kanji. Define the exact behavior for kanji that belong to multiple Custom Lists when implementing the feature.

## UI / product backlog

-   [x] First Tabler icon pass: shared Compose vectors for agreed controls,
    bottom-navigation placeholders replaced and MIT attribution bundled.
-   [ ] Future icon coverage: review remaining placeholders separately.
-   [ ] Continue visual polish after the current 0.6 collection/list
    work stabilizes.
-   [ ] Keep the emerging UI convention: actions normally use pill-style
    controls; selection/options normally use rounded rectangles, while a
    compact panel may intentionally use one consistent style for all
    peer controls.
-   [ ] Keep confirmation/Apply actions on the right and Cancel on the
    left.
-   [ ] Localization and dictionary translations are **post-0.9** work;
    do not expand current feature work into localization unless
    explicitly requested.

## Release / feedback direction

-   [ ] After Custom Lists and Train Selected are stable, prepare a
    build suitable for broader external feedback (for example Reddit)
    before committing to large secondary features.
-   [ ] Use that feedback to decide how much additional functionality
    belongs before 0.9 versus after it.

## UI cleanup versionCode 30 manual verification

- [ ] My Kanji: Reset filters hidden at defaults; changing grouping/sort/Rules shows it left of Show/Hide; reset restores N/A, Manually and no Rules.
- [ ] My Lists: reset visibility follows sort/Rules settings and reset restores Manually/no Rules.
- [ ] Kanji Groups: Reset rules appears only for active Rules and matches Rules dialog reset, preserving grouping/sorting.
- [ ] Frequency label and icons match numeric ascending Frequent first / descending Rare first, with missing ranks last.
- [ ] Continue training uses CirclePlay, End training uses CircleX in header and confirmation.
- [ ] Glyph/Strokes remain selected/functional and icon-only with accessible labels; check narrow widths and larger fonts, including reset row.

## Glyph/Strokes switch versionCode 31 manual verification

- [ ] Default/restored Glyph: Switch OFF/left, Eye active, Brush muted; Strokes ON/right reverses tints.
- [ ] Tap Eye, Switch or Brush: each toggles once in either direction through the one shared tap zone.
- [ ] Content changes as before; no clipping/wrapping or unnecessary height growth on narrow screens.
- [ ] Accessibility exposes one Switch with Glyph/Strokes labels and current mode, without duplicate Switch announcements.

## Glyph/Strokes touch area versionCode 32 manual verification

- [ ] Eye/Brush are equally sized at 40 dp; active/inactive tints remain readable. Standard Switch retains its original dimensions by agreement.
- [ ] Tap anywhere across the kanji-width control row, including empty sides: state toggles exactly once.
- [ ] Check centered symmetric spacing, no clipping/wrapping on narrow screens, 48 dp touch height, unchanged content and accessibility.

## Custom Glyph/Strokes selector v0.8.4 manual verification

- [ ] Default Glyph: Eye open on left, thumb on right covering Brush; Strokes reverses thumb and visible icon with unchanged content behavior.
- [ ] Check Eye 35 dp / Brush 29 dp visual balance, opaque inactive cover, smooth 180 ms movement and unchanged track color.
- [ ] Any tap across the full kanji-width area toggles once, including empty margins.
- [ ] Verify narrow layouts, no clipping/protrusion, minimum 48 dp touch height and light/dark theme colors.
- [ ] Accessibility exposes one display-mode action with current Glyph/Strokes and next-mode action, without on/off announcements.

## Search v0.8.6-alpha manual verification

- [x] Implement English relevance and Search-only matched meanings on existing dictionary data.
- [x] Implement isolated pairwise subsequence presentation groups with individually clickable forms.
- [x] Add anchored Search help with persistent automatic-display opt-out and manual access.
- [x] Search Sunday: direct meanings precede Sunday best, parenthetical examples and Whitsunday; 五旬節 displays its matched gloss, while Details retains its existing heading. Repeat with other words and multiword queries.
- [x] Verify 日曜日 / 日曜 / 日 grouping when their primary glosses agree; longer forms first, each opens its own Details; Show more preserves all identities. Review heuristic groups against real dictionary examples.
- [x] Verify written/kana/romaji search, unchanged earliest reading, Explore and Details return state.
- [x] Empty-field focus shows help; typing, focus loss and outside tap dismiss it naturally without disrupting the keyboard. There is no Close action.
- [x] Don't show again survives process restart; manual help on the empty field still works and no longer offers the opt-out action.
- [x] Check popup, grouped rows and the single state-dependent trailing icon on narrow screens, larger fonts and light/dark themes.
- [ ] Measure broad/single-letter query responsiveness on a phone; full candidate relevance evaluation happens before pagination.


## Search UI/state follow-up (versionCode 37) manual verification

- [x] Search -> Word Details -> Back retains query, Words mode and results without repeating completed SQL.
- [x] Search -> Kanji Details -> Back retains query, Kanji mode and results without repeating completed SQL.
- [x] Repeat both after Show more results and opening a result far down the list; preserve expanded result count and exact scroll position.
- [x] Query change starts the appropriate fresh search; clearing returns to discovery. The mode-switch behavior tested here is superseded by independent sessions in versionCode 38.
- [x] Primary meanings begin with capitals (Day / Every day); matched secondary meanings do too (One's Sunday best / Whitsunday), preserving the remaining spelling.
- [x] Empty query: only supplied TextQuestion; nonempty query: only supplied Backspace, which clears the entire query. Never both, and no info/circle-x action in the field.
- [x] Search Help has correct Kanji and Words examples, no Close, and closes naturally without obstructing typing.
- [x] Don't show again disables automatic help across restarts; manual help via TextQuestion remains available and hides the already-used opt-out action.
- [x] Grouped result: transparent rounded outline, separate filled written/reading blobs, one centered meaning on the right; longest variant first. Single results have no group outline.
- [x] Tap every variant of a grouped result; each opens the correct entry/written form in Word Details. Check narrow screens, larger fonts and both themes.

## Dictionary deduplication / word variant model (future research)

- [ ] Investigate duplicate dictionary entries with identical written forms (for example 五 or 男 with different readings/meanings).
- [ ] Determine whether readings/meanings can safely be combined at import time or in the dictionary model, preserving their applicable restrictions and distinct senses.
- [ ] Investigate a representation of orthographic variants of one word: 飛切り / 飛び切り / 飛びきり / とびっ切り, 今時 / 今どき, 学びや / 学び舎 / 学舎. Do not add kana removal/fuzzy matching to Search grouping.
- [ ] Separately assess kanji variants (for example 五 / 伍) and historical/alternative forms after component/key data or a suitable external dataset becomes available.
- [ ] Future Search Results should consume explicit dictionary variant information instead of guessing it through presentation heuristics. No part of this research is implemented in versionCode 37.

## Search independent sessions / grouped blob width (versionCode 38)

- [ ] Short written forms: every blob fills the same left-column width; forms/readings stay left-aligned, outline remains transparent and one shared meaning wraps on the right.
- [ ] Long written form uses one line with ellipsis; long reading also uses one line with ellipsis. Neither changes blob/column width.
- [ ] Each group variant still opens its correct entry/written form independently; longest-first order is unchanged.
- [ ] Words: query -> Kanji -> Words restores its query/results; Kanji: query -> Words -> Kanji restores its own query/results. A never-used mode starts empty, with no copied query.
- [ ] Keep different queries in both modes simultaneously; editing/clearing one mode leaves the other untouched, without SQL solely for mode switching when its results are complete.
- [ ] Show more in Words -> Kanji -> Words retains expanded count; repeat Show more in Kanji -> Words -> Kanji where applicable.
- [ ] Scroll restoration Words -> Kanji -> Words and Kanji -> Words -> Kanji preserves each mode's own index/offset.
- [ ] Words -> Word Details -> Back and Kanji -> Kanji Details -> Back retain the active session. Combine mode switching with Details navigation and repeat for both modes.
- [ ] Check narrow screens, larger fonts and both themes for equal columns, padding and ellipsis/meaning wrapping.

## Word Training Results v0.8.7-alpha manual verification

- [ ] Incorrect -> Repeat -> Correct: last word result Correct; old error removed from kanji aggregates.
- [ ] Correct -> Repeat -> Incorrect: last word result Incorrect.
- [ ] Incorrect -> Incorrect: no additional penalty.
- [ ] Correct -> Correct: no additional bonus.
- [ ] Repeat one word several times: Shown stays the unique evaluated word count.
- [ ] Mistakes only updates only repeated words; other last results remain.
- [ ] Entire session counts every word once by its last result.
- [ ] Current iteration with mixed Correct/Incorrect replaces the corresponding per-word results.
- [ ] Kanji -> Words -> Kanji retains presentation state and pending choices.
- [ ] Words -> Kanji -> Words returns to the Words presentation.
- [ ] Words lists each unique word once with its latest result.
- [ ] Kanji aggregates agree with current Words results.
- [ ] Kanji Results -> Kanji Details -> Back returns to the same Results session.
- [ ] Words Results -> matching Word Details -> Back returns to Words.
- [ ] Pending Learning/Known -> Kanji Details -> Back preserves pending choices.
- [ ] Kanji scroll restores after Details -> Back.
- [ ] Words scroll restores after Details -> Back.
- [ ] Kanji and Words have independent retained scroll positions across switches.
- [ ] Details navigation does not write Results pending changes.
- [ ] Kanji/Words switches do not write pending changes.
- [ ] Finish applies pending changes once through the existing Room transaction; Repeat preserves them. Check failure/retry.
- [ ] Header switch uses circular arrows alone at shared 20 dp beside headlineMedium Kanji/Words in a stable maximum-label-width control, with one icon/label tap zone without highlight; check rotation, rapid taps, large fonts, narrow screens and light/dark themes. Search retains its central character and existing size.

## Results UI/navigation follow-up (versionCode 41) manual verification

- [ ] Kanji switch: arrows only, Kanji label, shared 20 dp icon and header headlineMedium label.
- [ ] Kanji -> Words -> Kanji: icon/label start remain stationary, control stays right, whole area including spare width toggles.
- [ ] Search switch retains its central symbols, size and behavior.
- [ ] Words cards: compact written/reading left, meaning right, green check/error cross left, no separate status text.
- [ ] Long written/reading/meaning: bounded columns and ellipsis; check narrow screens, larger fonts and both themes.
- [ ] Words -> matching Word Details -> Back retains Words, results and scroll.
- [ ] Word Training Kanji Results -> matching Kanji Details -> Back retains results, pending and scroll.
- [ ] Kanji Training Results -> matching Kanji Details -> Back retains session, pending choices and scroll.
- [ ] Pending Learning/Known -> Details -> Back neither applies nor discards choices; Finish still applies them through the existing transaction.

## Results switch icon polish (versionCode 42) manual verification

- [ ] Results arrows render at 30 dp in the main foreground/text color matching the label.
- [ ] Kanji -> Words -> Kanji retains icon/label positions and the existing tap zone.
- [ ] Search switch retains its size, central symbol, colors and animation.
