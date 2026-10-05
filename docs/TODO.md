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
    -   My Kanji and My Lists keep their existing
        single-section/single-list selection isolation.
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

## Next milestones

-   [ ] **Train Selected:** launch Kanji Training directly from the
    current selection using an explicit/custom Training Pool.
    -   Bypass Review/Learning/New/My Lists source selection for this
        entry path.
    -   Training core should accept an arbitrary explicit kanji pool
        independent of Room learning state.
    -   Do not break existing selection boundaries merely to support
        mixed-state training: My Kanji remains section-limited; My Lists
        remains list-limited; Kanji Groups may contain a mixed
        multi-group selection.
    -   Collection-panel Training actions remain disabled until this
        milestone is implemented.
-   [ ] **Stroke Order / KanjiVG:** integrate stroke-path/order data
    after attribution/license verification.
-   [ ] **Handwriting Training:** build handwriting practice after
    stroke-order data is available. Exact recognition/validation UX
    should be designed at implementation time; preserve the possibility
    of results such as Wrong / Wrong Stroke Order / Correct.
-   [ ] **TTS / Japanese speech:** add pronunciation playback after the
    core training/handwriting work. Choose the implementation/backend
    when starting the feature rather than committing to one now.

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

-   [ ] Word Training.
-   [ ] Future Kanji Training improvements: stroke order,
    components/radical Hint and TTS.
-   [ ] Consider weighting/SRS/statistics only if later feedback shows
    they are useful.
-   [ ] Preserve the current separation of concerns: Kanji Training
    focuses on meaning/readings and eventually glyph reproduction; Word
    Training should handle words/readings/context.

## Dictionary and data

-   [ ] Improve word ranking beyond `common=1`.
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

## UI / product backlog

-   [ ] Download the chosen icon set and replace temporary/text
    placeholders.
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
