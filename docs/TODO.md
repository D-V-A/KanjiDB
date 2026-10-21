# Plans and TODO

Current implementation and limitations:
[PROJECT_CONTEXT.md](PROJECT_CONTEXT.md).
Development conventions: [AGENTS.md](../AGENTS.md).

Current application on `release`: **v0.10.2-open**, `versionCode = 64`, **Android 10 / API 29+**.

Checked items describe implemented functionality. Existing placeholders do not
count as completed milestones. Unchecked items are future product, engineering
or research work; their exact implementation should be decided when started
unless explicitly fixed below.

## Completed milestones

- [x] **My Kanji:** separate Room Learning/Known states, collection sections,
  transactional Move/Remove and persistent per-state manual order.
- [x] **Kanji Groups:** dictionary-only JLPT/Grade groups, Frequency/Strokes
  sorting, rules, multi-group selection and transactional state assignment.
- [x] **Recommended Kanji / Learn Next:** JLPT progression, process-owned
  candidate pool, discovery eligibility, weighted sampling, Refresh history
  and reactive ownership updates.
- [x] **Custom Lists / My Lists:** Room many-to-many memberships, CRUD/name
  validation, list/member order, overlapping and empty lists, staged tri-state
  membership dialogs and per-list membership-effect previews.
- [x] **Collection UI and selection:** shared collapsible filters, independent
  options/scroll state, additive cross-list/group selection, counters,
  contextual reorder hints, drag state independent of selection and immediate
  membership updates. Selection Mode uses Finish without Back to My Lists.
- [x] **Kanji Training:** Review/Learning/New/My Lists sources, paper recall,
  meaning/readings, self-assessment, repeated attempts, full-pool Results and
  pending state changes committed only at Finish.
- [x] **Word Training:** coverage-driven word selection, source/setup parity,
  masks, length/prompt controls, counter deduplication, partial results and
  explicit-word repeats. Each word's latest assessment replaces its previous
  result and updates kanji aggregates.
- [x] **Fixed-pool Training:** Train Selected and entire Custom Lists share
  training-type selection, large-pool confirmation, direct Kanji sessions and
  fixed-pool Word setup. Visual collection filters do not change list sources.
- [x] **Training Results navigation/UI:** Kanji/Words presentations, independent
  retained scroll positions, direct Details navigation and preserved pending
  actions; presentation/navigation alone does not write Room data.
- [x] **Search relevance/help:** English relevance and matched meanings,
  individually clickable presentation groups, contextual input help and a
  persistent automatic-help opt-out.
- [x] **Current Search state:** one shared query across Kanji/Words; explicit
  mode switches search that query in the new mode. Details/Back retains the
  active session, completed results, pagination and scroll. Earlier independent
  mode sessions have been superseded.
- [x] **Dictionary installation/update (v0.9.0):** independent asset versioning,
  validated atomic replacement and About's local dictionary version display,
  without changing the separate user database.
- [x] **Source-preserving words (v0.9.1):** exact writings, direct source-entry
  membership, reading/sense applicability, entry-level Word Training, Search
  representatives and independent Details entry/writing selectors.
- [x] **JLPT override for 分:** N5 is present in the current dictionary,
  applied by the JLPT importer and covered by the dictionary verifier.
- [x] **Related Words:** static user-independent ranking, conservative counter
  families, Blobs/Strings modes and local search over the full related-word set.
- [x] **Stroke Order / KanjiVG (v0.9.2):** canonical source cache, dictionary
  enrichment, optional animated Kanji Details and packaged attribution/license.
- [x] **Details and adaptive UI (v0.9.3):** clipboard actions, text-selection
  transfer/dismissal guards, adaptive Readings/Other Meanings columns and shared
  one-line/two-line Search/My Kanji tab labels based on actual available width.
- [x] **Shared Tabler icons:** Compose vectors for navigation, collection,
  Details, Training and user-data controls, with packaged MIT attribution.
- [x] **User data export/import (v0.10.0):** versioned full `.kdb` JSON for all
  Room states, lists, memberships and manual orders; separate DTOs, strict
  validation, confirmed atomic replacement and reactive collection updates.
- [x] **MediaStore export (versionCode 60):** Compose filename dialog with the
  default `KanjiDB_Data_YYYY-MM-DD.kdb`, checked-by-default KanjiDB subfolder
  option, direct Downloads saving on API 29+, collision detection, rejection of
  provider auto-renaming, `IS_PENDING` publication and failed-write cleanup.
  Import continues through the system file picker; no storage permissions.
- [x] **Missing user kanji:** retain states, memberships, Unicode characters and
  order in Room and data files. Import warnings list missing symbols; tiles
  show a dash, red unavailable state and orange selection with an outline.
  List/state actions remain available; Details shows an unavailable message.
  Selection Training is disabled when missing kanji are selected; saved-list
  Training reports exclusions and filters its pool, including an empty result.
  Dictionary Groups stay dictionary-only; later dictionary availability
  restores normal behavior automatically.

- [x] **Global Settings (v0.10.1):** shared gear on Search, My Kanji and the
  Training type-selection home; separate Settings/Back navigation without the
  bottom bar, reactive System/Light/Dark theme, persisted startup choice and last
  My Kanji tab. Fresh-root Automatic/Training uses dictionary-valid user kanji;
  an empty pool forces Kanji Groups over the remembered tab. Export/Import and
  About now open from Settings; subsequent Training screens retain their UI.

- [x] **Settings selectors (v0.10.2):** shared circular-thumb multi-state control
  replaces Theme/Startup dropdowns. Follow-up (versionCode 63) adds direct slot
  taps, compact 44 dp spacing and a title/selector main row. VersionCode 64
  replaces permanent selected-value text with a non-interactive overlay bubble:
  tap/drag feedback, nearest-position tail, edge clamping, short fade and one
  restartable hide timer. No space is reserved below the control. Measured
  fallbacks try Startup before stacking the selector. Drag, nearest-option
  preview/snap, settled enlargement, preferences and Details UI are unchanged.

## User data

- [ ] Add export/import of individual Custom Lists; define payload scope,
  compatibility and merge/replace semantics before implementation. The current
  format supports only full restoration.

## Recommendations, discovery and home screen

- [ ] Implement **Recommended Words**.
- [ ] Add **Kanji of the Day** using a deterministic local-calendar-date hash
  and a stable Unicode codepoint list, respecting discovery eligibility.
- [ ] Revisit Search/home discovery UI when Recommended Words/daily content
  is implemented. Current direction: top-level Kanji/Word choice and a second
  row such as Of the Day / Recommended / Explore; define Word-side daily
  behavior when implementing it.
- [ ] Add a recommendation strategy selector to **Settings**; implement
  Grade / Frequency / Rare-or-Discovery strategies separately.
- [ ] Add **Include Custom Lists in Kanji recommendations** to Settings.
  Define whether list membership excludes, boosts, prioritizes or otherwise
  affects candidates when implementing the feature.
- [ ] Improve meaning/reading usefulness ranking when concrete cases justify
  it. Control case: for 漢, prefer `China` over `Sino-` as a training meaning.

## Training backlog

Kanji Training focuses on meaning/readings and eventually glyph reproduction;
Word Training handles words/readings/context.

- [ ] Implement **Handwriting Training** with an explicitly designed
  recognition/assessment UX. Preserve the possibility of Wrong / Wrong Stroke
  Order / Correct outcomes.
- [ ] Add **TTS / Japanese speech**; choose the implementation when this feature
  is started.
- [ ] Add stroke order and components/radical hints to Kanji Training.
- [ ] Profile and optimize Word Training for large pools; tune the counter
  whitelist/usefulness floor when supported by concrete performance or quality
  issues.
- [ ] Consider weighting/SRS/statistics if product feedback establishes a need.

## Dictionary and data

- [ ] Improve presentation of especially large reading sets such as 生 beyond
  the existing adaptive one-column/two-column layout.
- [ ] Develop Explore word relevance ranking; retain its separation from
  Search relevance and Related Words ranking.
- [ ] Complete the EDRDG attribution and license review.
- [ ] Add **Group by: My Lists** to My Kanji; define behavior for kanji belonging
  to multiple Custom Lists before implementation.

## Dictionary variants: remaining research

The current model preserves source entries and their direct writing membership.
It does not implement cross-entry lexical deduplication or transitive variant
closure; diagnostic Model C is not a production model.

- [ ] Investigate entries with identical written forms but different
  readings/meanings, such as 五 or 男.
- [ ] Determine whether cross-entry readings/meanings can safely be combined
  while preserving applicability restrictions and distinct senses.
- [ ] Investigate orthographic relationships beyond existing direct source-entry
  alternatives: 飛切り / 飛び切り / 飛びきり / とびっ切り,
  今時 / 今どき, 学びや / 学び舎 / 学舎. Do not add kana removal or fuzzy
  matching to Search presentation grouping.
- [ ] Separately research kanji variants, historical and alternative forms,
  such as 五 / 伍, after suitable component/key data or another dataset exists.
- [ ] Use explicit dictionary variant information in future Search Results
  once such a model is available, rather than extending presentation heuristics
  into lexical identity rules.

## UI / product backlog

- [ ] Replace remaining icon placeholders where needed.
- [ ] Continue targeted visual polish of collections, Details and Training.
- [ ] Implement localization and dictionary translations as a separately
  scoped feature.

## Release / feedback direction

- [x] Integrate current `main` into `release` as v0.10.2-open, preserving
  branch-specific Feedback/first-launch notice and inheriting `main`'s
  versionCode 64. Search retains Feedback next to the new Settings action.
- [ ] Collect broader external feedback and use it to prioritize subsequent
  product milestones.
