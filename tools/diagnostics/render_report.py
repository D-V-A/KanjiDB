"""Render the read-only JSON research into a human-readable Russian report."""
from pathlib import Path
import json
from collections import defaultdict, deque
import sqlite3

HERE=Path(__file__).resolve().parent
ROOT=HERE.parent.parent
r=json.loads((HERE/'report.json').read_text(encoding='utf-8'))
s=r['stats']; e=r['examples']; m=s['models']; lines=[]

def put(text=''): lines.append(text)
def heading(n,title): put(f'## {n}. {title}\n')
def fmt(n): return f'{n:,}'.replace(',',' ')
def pct(n,d): return f'{100*n/d:.2f}%'
def table(headers,rows):
    put('| '+' | '.join(headers)+' |'); put('| '+' | '.join(['---']*len(headers))+' |')
    for row in rows:
        put('| '+' | '.join(str(x).replace('|','\\|').replace('\n',' ') for x in row)+' |')
    put()
def dist(d): table(['Значение','Количество'],[(k,fmt(v)) for k,v in d.items()])
def node(n): return f"{n['entry_id']} **{n['written']}** [{', '.join(n['readings'])}] — {'; '.join(n['meanings'])}"
def entry(item):
    put(f"- Entry **{item['entry_id']}**: "+' / '.join(f['written'] for f in item['forms']))
    for f in item['forms']: put('  - '+node(f))
    put()
def group(g,assessment=None):
    put(f"- **{len(g['written'])} written / {len(g['source_entry_ids'])} source entries / {len(g['readings'])} readings / {len(g['meanings'])} glosses**"+(f' — {assessment}' if assessment else ''))
    put('  - Written: '+ ' / '.join(g['written']))
    put('  - Entries: '+', '.join(map(str,g['source_entry_ids'])))
    put('  - Readings: '+', '.join(g['readings']))
    put('  - Deduplicated English glosses: '+'; '.join(g['meanings']))
    put()
def candidate(c):
    put('- A: '+node(c['a']))
    put('  - B: '+node(c['b']))
    put(f"  - Signals: normalized reading distance={c['reading_distance_normalized']}; written distance={c['written_distance']}; written similarity={c['written_similarity']}; gloss overlap={c['meaning_overlap']}; already in same Model C={c['already_same_Model_C']}.")
    put()

put('# JMdict / dictionary.db: диагностическое исследование\n')
put('Дата: 2026-10-13. Локальный JMdict XML: `JMdict created: 2026-09-16`. Все основные модели/counts относятся к **импортированной DB**, а source distribution отмечена отдельно. Это анализ конкретного локального snapshot, не всего актуального JMdict.\n')
put('SQLite открывалась `mode=ro` и `query_only=ON`. Импортер не запускался. SHA-256 DB/XML до и после совпали. Production code, schema, assets и версии не изменены. Предыдущие незакоммиченные изменения milestone сохранены.\n')
put('Методика: длины в Unicode code points; exact written/reading — без normalization. English gloss TRIM — удаление ASCII spaces, как SQLite TRIM; CI — дополнительно Python casefold. Meaning overlap = `|A∩B| / max(|A|,|B|)` по полным CI gloss strings, не семантическая similarity. Одинаковые gloss strings не означают одинаковые senses.\n')
heading(1,'Executive summary')
put(f"- Model A: **{fmt(m['A'])}** entities; B: **{fmt(m['B'])}**, уменьшение **{pct(m['A']-m['B'],m['A'])}**; C: **{fmt(m['C'])}**, уменьшение **{pct(m['A']-m['C'],m['A'])}**. Это counts identities, не прогноз физического удаления rows.\n- Exact-written duplicate spellings: **{fmt(s['exact_written']['count'])}**. Readings чаще различаются: у {fmt(s['exact_written']['classifications']['pairwise_disjoint_readings'])} spelling groups все entry reading sets попарно непересекающиеся.\n- Same-entry reading equality не универсальна: **{fmt(s['multi_written']['different_reading_sets'])}** multi-written entries имеют разные exact reading sets, **{fmt(s['multi_written']['at_least_one_disjoint_pair'])}** — хотя бы одну пару без общего exact reading.\n- Транзитивный Model C имеет выраженный риск: максимум **61 source entry, 48 written, 77 readings**; туда одновременно входят 男, 女, 妻, 俺, 側. Это не один очевидный lexical word.\n- Variants не преимущественно ограничены короткими словами: **{pct(s['multi_written']['max_len_ge_4'],s['multi_written']['entries'])}** multi-written entries содержат форму длиной ≥4, **{pct(s['multi_written']['max_len_ge_6'],s['multi_written']['entries'])}** — ≥6; maximum 27.\n")
heading(2,'Current DB counts')
table(['Метрика','Count'],[(label,fmt(s[key])) for label,key in [('Импортированные JMdict entries / distinct entry_id','entries'),('word_form rows','word_form'),('distinct written','written'),('distinct reading','reading'),('distinct (entry_id,written)','entry_written'),('distinct (written,reading)','written_reading'),('word_meaning rows (все English)','word_meaning'),('word_kanji rows','word_kanji')]])
dist(s['source'])
put('Importer `entry_count` не считает entries без k_ele. 177 549 — entries с written, а не все прочитанные XML entries; из них 1 879 не дали valid imported form. 43 115 полностью отфильтровано. Набор воспроизведённых из XML импортированных entry_id точно совпал с DB. 208 исходных written форм отсутствуют внутри импортированных entries (например ５); DB-derived variants не включают их.\n')
put('Текущая schema: `word_form` UNIQUE(entry_id,written,reading); `word_meaning` UNIQUE(word_form_id,sense_index,language,meaning); `word_kanji` PK(word_form_id,position). Importer сохраняет reading_order, common/priority; применяет re_restr, исключает re_nokanji и written без известного KANJIDIC character; glosses применяются только при совпадении stagk/stagr. Эти связи не восстанавливаются безопасно простым UNION всех glosses.\n')
heading(3,'Exact-written duplicates и meaning duplication')
x=s['exact_written']
put(f"{fmt(x['count'])} distinct written связывают {fmt(x['source_entry_union'])} distinct source entries и {fmt(x['entry_written_memberships'])} (entry,written) memberships. Model B устраняет {fmt(m['A']-m['B'])} identities ({pct(m['A']-m['B'],m['A'])}); word_form после простого collapse identical (written,reading) станет {fmt(s['written_reading'])}, только −{fmt(s['word_form']-s['written_reading'])} rows ({pct(s['word_form']-s['written_reading'],s['word_form'])}). Разные readings/senses всё равно надо хранить.\n")
put('Distribution number of entry_ids per written:'); dist(x['distribution'])
put('Readings/meaning exact-string classifications (meaning categories mutually exclusive):'); dist(x['classifications'])
put(f"В duplicate-written subsets: {fmt(x['english_rows_before'])} raw English gloss rows -> {fmt(x['trim_gloss_after_per_written'])} per-written TRIM unique glosses: {fmt(x['english_rows_before']-x['trim_gloss_after_per_written'])} duplicates, **{pct(x['english_rows_before']-x['trim_gloss_after_per_written'],x['english_rows_before'])}**. CI: {fmt(x['ci_gloss_after_per_written'])}, duplicates {pct(x['english_rows_before']-x['ci_gloss_after_per_written'],x['english_rows_before'])}. Это включает копии между readings/senses. Если сначала дедуплицировать каждый (entry,written), было {fmt(x['per_entry_written_unique_gloss_before'])}; собственно cross-entry повторов {fmt(x['per_entry_written_unique_gloss_before']-x['per_written_unique_gloss_after'])} ({pct(x['per_entry_written_unique_gloss_before']-x['per_written_unique_gloss_after'],x['per_entry_written_unique_gloss_before'])}). Ни один из этих counts не разрешает потерю sense provenance.\n")
put('### Обязательные примеры\n')
for w in ['五','男','前','日']:
    put('**'+w+'**\n')
    for n in e['requested_local_forms'][w]: put('- '+node(n))
    put()
put('### 40 реальных duplicate-written примеров\n')
for item in e['exact_written_duplicates']:
    put('**'+item['written']+'**\n')
    for n in item['forms']: put('- '+node(n))
    put()
put('### 20 случаев с наибольшим количеством повторов gloss rows\n')
table(['Written','Raw rows','Unique glosses','Duplicated rows'],[(item['written'],item['rows'],item['distinct_glosses'],item['rows']-item['distinct_glosses']) for item in e['duplicate_gloss_top']])
put('Полные readings/meanings каждого entry этих 20 случаев находятся также в `report.json → examples.duplicate_gloss_top`. Многие значения реально различаются: 前 — направления, прошлое, порции, criminal record; 日 — sun/day, Japan/Sunday, day counters. CI identity/overlap не измеряет смысловую тождественность.\n')
heading(4,'Multi-written JMdict entries')
put('Distribution imported distinct written per entry:'); dist(s['entry_written_distribution'])
for t in [1,2,3,5]:
    count=sum(v for k,v in s['entry_written_distribution'].items() if (int(k)==1 if t==1 else int(k)>=t))
    put(f"- {'Только 1' if t==1 else str(t)+'+'}: {fmt(count)} entries, {pct(count,s['entries'])}.")
put('\nMaximum imported forms: 17. Raw source distribution (0 means no k_ele, not a zero-form imported word):\n'); dist(s['source_written_distribution'])
source_w=s['source']['importer_processed_counter']
for t in [1,2,3,5]:
    count=sum(v for k,v in s['source_written_distribution'].items() if (int(k)==1 if t==1 else int(k)>=t))
    put(f"- Source written-bearing entries {'only 1' if t==1 else str(t)+'+'}: {fmt(count)}, {pct(count,source_w)}.")
put('\n### Запрошенные variants\n')
for w in ['飛切り','飛び切り','飛びきり','とびっ切り','今時','今どき','学びや','学び舎','学舎']:
    for n in e['requested_local_forms'][w]: put('- '+node(n))
put('\n飛切り/飛び切り/飛びきり — entry 1598870; とびっ切り — отдельный 2833447, не reading variation внутри того же entry. 今時/今どき — 1289180, но 今時 ещё есть в 2849332. 学びや/学び舎/学舎 — 1761170, и 学舎 отдельно в 1206790.\n')
put('Полные source written sets запрошенных lexical entries (включая формы вне named sample):\n')
for source_entry in e['requested_source_entries']:
    if source_entry['entry_id'] in {1598870,2833447,1289180,2849332,1761170,1206790}:
        put(f"- {source_entry['entry_id']}: "+' / '.join(source_entry['written'])+'; readings: '+', '.join(x['reading'] for x in source_entry['readings']))
put()
put('### 35 representative multi-written entries (extreme forms count; обязательные обычные примеры выше)\n')
for item in e['multi_written_entries']: entry(item)
heading(5,'Reading variation inside one entry')
u=s['multi_written']
dist({k:v for k,v in u.items() if k in ['same_reading_sets','different_reading_sets','at_least_one_disjoint_pair','differences_only_small_kana_normalized_distance_le_1','different_exact_sets_but_same_after_katakana_to_hiragana','small_changes_with_actual_normalized_distance_1','reading_max']})
put('Small-change definition: для каждой пары differing sets каждый reading из A имеет reading B на normalized distance ≤1 и наоборот. Это строгий bidirectional nearest-match proxy, не фонетическая/лексическая экспертиза. Он включает script-only различия; они показаны отдельным count. Max distinct readings =13 у импортированного multi-written entry.\n')
put('### 30 entries с хотя бы одной парой без общего exact reading\n')
for item in e['disjoint_readings']: entry(item)
put('### 30 small-change entries\n')
for item in e['small_reading_changes']: entry(item)
put('Примеры в начале включают hiragana/katakana changes. В JSON есть также reading_differences; отсутствие общего raw reading не всегда означает разные normalized readings.\n')
put('### Actual normalized single-edit examples внутри одного entry\n')
for kind,items in e['actual_small_reading_changes'].items():
    put('**'+kind+'**\n')
    for item in items[:5]:
        put('- A: '+node(item['a'])); put('  - B: '+node(item['b']))
        put('  - Normalized readings: '+item['reading_a']+' ↔ '+item['reading_b']); put()
put('### Entries с максимальным количеством readings\n')
for item in e['multi_written_most_readings'][:3]: entry(item)
put('### Длины alternative forms\n')
lengths=u['length_distribution']; table(['Code-point length','Form occurrences внутри multi-written entries'],[(str(i),fmt(lengths.get(str(i),0))) for i in range(1,6)]+[('6+',fmt(sum(v for k,v in lengths.items() if int(k)>=6)))])
table(['Entry maximum length','Entries'], [('≤2',u['max_len_le_2']),('≤3',u['max_len_le_3']),('≥4',u['max_len_ge_4']),('≥6',u['max_len_ge_6']),('Maximum',u['max_length'])])
put('У 56.14% multi-written entries есть written ≥4. Гипотеза «variants в основном только короткие» этими данными не подтверждается; короткие ≤3 есть у 43.86% по entry maximum. Counts форм здесь включают каждое (entry,written), а не global distinct written.\n')
for item in e['long_multi_written'][:8]: entry(item)
heading(6,'Same-reading cross-entry statistics')
dist(s['same_reading']['written_count_buckets'])
put(f"Maximum exact reading corresponds to {s['same_reading']['max_written']} distinct written. Different-written + different-entry candidate pairs (deduplicated across shared readings): **{fmt(s['same_reading']['cross_entry_distinct_entity_pairs'])}**.\n")
dist(s['same_reading']['overlap_distribution'])
put('109 852 / 111 094 pairs (98.88%) имеют нулевой full-gloss overlap; 109 имеют ≥0.75. Это сигнал для review, не подтверждение variant. Katakana→hiragana меняет counts; normalized-zero counts в section 9 отдельно.\n')
put('Дополнительный paraphrase proxy: token Jaccard по [a-z0-9]+ из всех CI glosses, без a/an/the/to/of/in/on/and/or/for/with/at/by/as; не semantic similarity.'); dist(s['same_reading']['token_diagnostics'])
for item in e['same_reading_token_overlap'][:8]:
    put('- A: '+node(item['a'])); put('  - B: '+node(item['b'])); put('  - Token Jaccard='+str(round(item['token_jaccard'],4))+'; exact gloss overlap=0.'); put()
for item in e['same_reading_extremes'][:6]:
    put(f"- **{item['reading']}**: {item['written_count']} written — "+' / '.join(sorted({f['written'] for f in item['forms']})))
put('\nПолные entry_id/readings/meanings extreme groups в JSON.\n')
heading(7,'Near-reading diagnostics')
n=s['near_reading']
put(f"Normalized readings: {fmt(n['normalized_reading_count'])}. Global distinct normalized reading pairs distance=1: **{fmt(n['global_distance_1_reading_pairs'])}**. Expansion across different-entry/different-written entities даёт **{fmt(n['distance_1_cross_entry_entity_pair_occurrences'])} occurrences**; это не distinct word pairs, поскольку пары могут иметь несколько reading matches.\n")
put('Distance=1 вычислен для всех readings через single-deletion signatures и exact single-edit verification. Нет полного O(N²). Distance=2 вычислен только по meaning-supported cross-entry candidates (section 9), не по всему словарю; global distance=2 count не заявляется.\n')
dist(n['edit_kinds'])
put('Long-vowel-like — грубый proxy по ー/う/お/い/え; он включает обычные kana edits, не доказывает изменение долготы. Voicing — совпадение базовой NFD kana, включая handakuten.\n')
for kind, pairs in e['near_reading_edits'].items():
    put('**'+kind+'**: '+ '; '.join(a+' ↔ '+b for a,b in pairs[:8])+'\n')
put('### Meaning-supported near-reading examples\n')
for c in e['near_high_overlap'][:20]: candidate(c)
heading(8,'Written-variation diagnostics')
dist(s['spelling_pair_classification'])
put('Counts — все неупорядоченные пары written внутри imported same-entry (90 221 pairs), не entries. Categories mutually exclusive и намеренно грубые. Kana insertion при одинаковой kanji sequence — proxy okurigana; вставленная kana может быть не okurigana. Directed kanji↔kana labels ориентированы по lexicographic pair order, а не по историческому изменению. Замены по kanji subset не гарантируют корректный alignment. Нельзя использовать эти labels для production merge.\n')
for kind,items in e['spelling_variations'].items():
    put('**'+kind+'**\n')
    for item in items[:8]: put(f"- {item['entry_id']}: {item['a']} ↔ {item['b']}")
    put()
heading(9,'Cross-entry candidate diagnostics')
put('Все counts — пары imported (entry,written), different written и different entry. Reading distance — minimum по normalized reading sets. Written similarity = `1 - Levenshtein/max(lengths)`. Meaning overlap — полные CI gloss sets. Это несколько диагностических thresholds, не production algorithm.\n')
c=s['cross_entry_candidates']['counts']
table(['Threshold','distance 0','distance 1','distance 2'],[(label,*[fmt(c.get(pattern.format(d=d),0)) for d in range(3)]) for label,pattern in [
('Meaning overlap ≥0.50','overlap_ge_050_reading_distance_{d}'),('Meaning overlap ≥0.75','overlap_ge_075_reading_distance_{d}'),
('Overlap ≥0.75 + written similarity ≥0.50','overlap_ge_075_reading_distance_{d}_written_similarity_ge_050'),
('Overlap ≥0.75 + written distance ≤1','overlap_ge_075_reading_distance_{d}_written_distance_le_1'),('Overlap =1','overlap_1_reading_distance_{d}')]])
put(f"Shared-exact-gloss inverted index covered {fmt(s['cross_entry_candidates']['shared_exact_gloss_cross_entry_pairs'])} cross-entry/different-written pairs. Counts distance=2 исчерпывающие **только внутри overlap≥0.50 scope**. Вне Model C остаются {fmt(s['cross_entry_candidates']['high_overlap_exact_outside_C'])} normalized exact-reading/high-overlap pairs и {fmt(s['cross_entry_candidates']['high_overlap_near_1_outside_C'])} near-distance-1/high-overlap pairs. Не все — variants: технические названия, школы, похожие термины и обычные синонимы дают совпадающие glosses.\n")
put('### 50 candidates (diverse entry pairs, не certified variants)\n')
for item in e['cross_entry_candidates_50']: candidate(item)
put('### 20 false-positive-looking homophones (overlap=0)\n')
for item in e['false_positive_homophones']:
    put('- A: '+node(item['a'])); put('  - B: '+node(item['b'])); put('  - Exact readings: '+', '.join(item['common_reading'])+'; full-gloss overlap=0.'); put()
put('Среди high-overlap кандидатов особенно требуют проверки пары названий разных школ/университетов: 七生養護学校/七尾養護学校; 純心女子短大/純真女子短大; 文京女子大学/文教女子大学. Совпадение English rendering не доказывает идентичность учреждения.\n')
heading(10,'Hypothetical Model A/B/C counts')
table(['Model','Identity','Entities','Reduction vs A'], [('A','(entry_id,written)',fmt(m['A']),'—'),('B','exact written',fmt(m['B']),pct(m['A']-m['B'],m['A'])),('C','connected components: same-entry OR same-written',fmt(m['C']),pct(m['A']-m['C'],m['A']))])
put('C связывает nodes (entry_id,written) через same-entry и exact-written без reading/gloss фильтра. Все links транзитивны. Это diagnostic grouping, не утверждение lexical equivalence.\n')
put('Distribution distinct written per C group:'); dist(m['C_written_distribution'])
put('Distribution distinct source entry_ids per C group:'); dist(m['C_entry_distribution'])
put('### 30 biggest/interesting C groups, all written/entries/readings/glosses\n')
for g in e['model_C_biggest_written']: group(g)
heading(11,'Transitive merge risks')
table(['Source entries per group threshold','Groups'],[(f'>{t}',fmt(m[f'multi_entry_gt_{t}'])) for t in [1,2,5,10]]+[('Maximum entries',61)])
put('Всего 2 839 / 171 585 groups (1.65%) multi-entry. Extreme graph chains связывают визуально и семантически разные объекты. Большая компонента — не только duplicate entries одного слова. Ниже субъективная оценка по написаниям/readings/glosses; она не меняет диагностический algorithm.\n')
# Recover exact linking chains from the unchanged database, not semantic guesswork.
db=sqlite3.connect((ROOT/'app/src/main/assets/dictionary.db').as_uri()+'?mode=ro',uri=True)
entries=defaultdict(set); written_entries=defaultdict(set)
for eid,w in db.execute('SELECT DISTINCT entry_id,written FROM word_form'):
    entries[eid].add(w); written_entries[w].add(eid)
db.close()
def chain(start,end):
    todo=deque([start]); previous={start:None}
    while todo:
        w=todo.popleft()
        if w==end: break
        for eid in sorted(written_entries[w]):
            for v in sorted(entries[eid]):
                if v not in previous:
                    previous[v]=(w,eid); todo.append(v)
    if end not in previous: return 'not connected'
    path=[]; w=end
    while previous[w] is not None:
        old,eid=previous[w]; path.append((old,eid,w)); w=old
    return ' → '.join(f'{a} —[{eid}]— {b}' for a,eid,b in reversed(path))
for a,b in [('男','女'),('男','俺'),('卵','身体'),('前','先')]: put(f'- Exact-link path {a} ↔ {b}: '+chain(a,b))
put()
put('Source check цепочки 男→女: entry 2742870 written 雄/男/夫/牡, reading お, включает male/man/husband; entry 2852371 written 妻/夫, reading つま, glosses my dear/dear/honey; entry 2742880 written 雌/女/妻/牝, reading め, включает female/woman/wife. Источник действительно содержит эти edges; это не ошибка union-find.\n')
for g in e['model_C_biggest_source_entries']:
    label='Подозрительно как единый lexical word: много разнородных written/readings/senses.' if len(g['source_entry_ids'])>5 else 'Непонятно без sense-level/source review; общие написания сами по себе не доказывают lexical identity.'
    group(g,label)
put('Логично на уровне source grouping: single-entry 倶利伽羅紋紋 group (17 orthographic forms, 1 reading, 2 glosses). Относительно правдоподобны 学びや/学び舎/学舎, но cross-entry がくしゃ и まなびや всё равно требуют сохранения provenance.\n')
heading(12,'Word Details impact')
wd=m['C_written_distribution']; total=m['C']; multi_count=sum(v for k,v in wd.items() if int(k)>1)
table(['Written forms per C group','Groups'],[(str(i),fmt(wd.get(str(i),0))) for i in range(1,5)]+[('5+',fmt(sum(v for k,v in wd.items() if int(k)>=5))),('Maximum',48)])
put(f"Alternative block нужен {fmt(multi_count)} groups, **{pct(multi_count,total)}**. Alternatives = written_count−1: >3 alternatives {fmt(sum(v for k,v in wd.items() if int(k)>4))}; >5 {fmt(sum(v for k,v in wd.items() if int(k)>6))}; >10 {fmt(sum(v for k,v in wd.items() if int(k)>11))}. Maximum 47 alternatives.\n")
put('Distribution readings per word group:'); dist(m['C_reading_distribution'])
put('Distribution deduplicated English gloss strings per group (not linguistic senses):'); dist(m['C_meaning_distribution'])
put('Distribution group maximum written length:'); dist(m['C_max_written_length_distribution'])
put('Displayed-form lengths внутри C groups с alternatives:'); dist(m['C_multi_written_displayed_form_lengths'])
put('Это distribution по всем possible written forms, не прогноз реально выбранного default displayed form: canonical selection новой модели пока не определена. Текущий UI compact ≤5, длинные >5; maximum 77 readings и 213 unique gloss strings. Extreme examples section 10/11 показывают необходимость bounded/expandable representations и sense/readings applicability; это observations, не UI implementation plan.\n')
heading(13,'Current runtime identity dependencies')
table(['Место','Current assumption','Impact word_id / слой изменений'],[
('DictionaryDatabase.kt: DictionaryWord/Details, getWord','entryId+written; readings/meanings selected by one source entry','SQL и Kotlin models; сохранить mapping written→reading→sense. Не только dictionary-side.'),
('WordSearch.kt / WordSearchRelevance.kt','candidate SQL/maps key (entry_id,written); earliest reading_order per spelling; entry_id tie-break','SQL identity, ranking metadata, pagination/hasMore counts и result DTOs.'),
('Explore Words SQL','5 random distinct entry_ids, one common representative per entry','Sampling identity/canonical representative меняются; dictionary+runtime.'),
('Word Details screen','remember/LaunchedEffect(entryId,written,sourceKanji); one displayed written; readings only for it','Parameters/cache keys/models/alternative availability; UI/runtime change.'),
('RelatedWordsRanker / WordQuality','distinct(entryId,written), representative same entry+reading, source priority/order','Candidate identity, representative selection, tie-break; runtime.'),
('WordTrainingCandidates / WordTrainingSelection','SQL and glossary maps (entry,written); distinct written; one primary reading/gloss','Training selection/dedup eligibility/coverage must choose actual written-specific realization.'),
('WordTrainingSession / Results','key Pair<Long,String>; lastResults, repeat sets and list keys use entry+written','Result identity semantics must be reconsidered: word-level vs displayed-form-level; Kotlin/state/UI.'),
('KanjiDbApp.kt navigation','word/{entryId}/{written}?sourceKanji=...','Route arguments, callbacks and detail return chain/runtime state.'),
('Search/Related/Results Lazy list keys','entryId:written','Stable UI keys change, cache/restoration semantics need review.'),
('Room user.db','Unicode kanji characters, list IDs; no persistent word IDs','No currently required user Room migration for word_id; dictionary rebuild alone does not update runtime contracts.')])
put('Word sessions/search result DTOs are process-owned rather than persisted word-history tables. Existing entry_id source identity is spread across SQL, model keys, sorting, navigation and Results; replacing just dictionary schema will break those contracts.\n')
heading(14,'Existing presentation-level deduplication')
table(['Mechanism','Key/conditions','Cross-entry?','After redesign'],[
('groupCommonWords / deduplicateCommonWords','same entryId+selected reading; normalized full English gloss sets overlap≥75% of larger; retained representatives only','No','Likely superseded for explicit same-entry variants; exact reading guard conflicts with broader variants. Review, do not keep twice blindly.'),
('Search groupWordSearchResults','loaded page, equal normalized primaryMeaning; ordered code-point subsequence either direction; every pair compatible','Yes; identical written qualifies','Explicit variants may replace part; it can currently group independent sources/homonyms. Preserve ranking/each-form click semantics deliberately.'),
('Search SQL / results map','GROUP BY entry,written; best matched gloss per candidate','No','Still need one display hit and ranking over applicable senses/readings, identity key changes.'),
('Word Details meanings','GROUP BY language,TRIM(meaning), across readings of selected entry,written; orderedWordMeanings distinct','No','Exact display-string dedup may remain useful; should not erase sense restrictions.'),
('Word Details constituent kanji','GROUP BY kanji id; MIN(position)','Not lexical grouping','Still useful for repeated kanji in displayed spelling.'),
('Related Words candidate SQL','GROUP BY entry,written; earliest reading; MAX common/priority; selected reading glosses','No','Candidate aggregation rewritten; ranking is independent of lexical dedup.'),
('Training gloss query','GROUP BY selected id,language,TRIM(meaning); aggregates all valid readings of displayed written','No','Display-gloss dedup can remain; applicability/model changes required.'),
('Training final distinctBy written','Keeps first ranked exact-written candidate after equivalence pass','Yes','Model B may make redundant, but form-level training behavior must remain explicit.'),
('CounterConstructions suppression','exact numeral 1..99 or decimal digits + 本/人/個/枚/匹/台/冊/回/階; one family representative','Yes','Pedagogical/display suppression, not lexical identity; normalized schema does not automatically obsolete it.'),
('distinctBy entry+written / mergeKanjiWords / chunks','Removes repeated fetches of same display identity','No','Query-result dedup remains possible; identity key changes.')])
put('Existing presentation logic уже прячет часть database repetition. 75% pass — non-transitive representative grouping; Model C deliberately transitive and much broader. Это разные semantics.\n')
heading(15,'Performance / DB-size observations')
put(f"Current DB: {fmt(s['db_bytes'])} bytes = {s['db_bytes']/1024**2:.2f} MiB; {fmt(s['db_page_count'])} pages, {s['db_freelist_count']} freelist pages. Total current word rows = {fmt(s['word_form']+s['word_meaning']+s['word_kanji'])}. `dbstat` unavailable in local Python SQLite, so precise per-table/index byte attribution is not claimed.\n")
table(['Illustrative normalized payload','Count','Caveat'],[
('word groups C',fmt(m['C']),'Diagnostic identity, not approved model'),('distinct written',fmt(s['written']),'Global written links, C partitions each exact written into one component'),
('group-reading links',fmt(m['C_group_reading_links']),'Still need written↔reading applicability'),('source sense records',fmt(s['source_senses']),'Distinct imported English-bearing (entry,sense_index)'),
('source sense/gloss records',fmt(s['source_sense_gloss']),'Dedup repeated copies across forms; preserves source/sense identity'),
('flat group unique gloss strings',fmt(m['C_group_unique_gloss_links']),'Display lower payload; not enough for lossless sense storage'),
('written-kanji-position links',fmt(m['unique_written_kanji_position_links']),'Dedup repeats across readings and source entries')])
put(f"Potential gloss storage: {fmt(s['word_meaning'])} current copies -> {fmt(s['source_sense_gloss'])} source-sense/gloss records (−{pct(s['word_meaning']-s['source_sense_gloss'],s['word_meaning'])}); kanji links {fmt(s['word_kanji'])} -> {fmt(m['unique_written_kanji_position_links'])} (−{pct(s['word_kanji']-m['unique_written_kanji_position_links'],s['word_kanji'])}). Это не окончательная schema estimate.\n")
put('Splitting word/written/reading/sense/meaning/source mapping может **увеличить суммарный count rows**, даже уменьшая duplicated text. Existing valid written-reading associations остаются примерно 246k и будут отдельными links; потребуются sense restriction/source mappings и indexes. Flat C group/gloss UNION потеряет applicability. Уменьшение identities на 25.8% не означает уменьшение DB bytes на 25.8%; требуется отдельный lossless prototype вне production DB для byte/performance сравнения. Анализ завершился примерно за '+str(r['elapsed_seconds'])+' seconds; это desktop research timing, не Android startup/query benchmark.\n')
heading(16,'Recommendation for what we should investigate next')
put('1. Сначала разобрать exact-link paths крупнейших C components и различие visual written identity / lexical entry identity. Exact-written visual merge может быть полезен, но его транзитивное распространение на все same-entry variants даёт смешанные группы.\n2. Проверить preservation matrix `written ↔ reading ↔ sense ↔ source` на реальных re_restr/stagk/stagr cases. Gloss-string dedup не может заменять sense model.\n3. Вручную разметить stratified sample cross-entry candidates: exact/near reading, high/low gloss overlap, короткие/длинные, proper names. Даже overlap=1 и reading distance=0 дают кандидаты с потенциально разными referents.\n4. Уточнить, что оценивается в Training/Results: lexical word или конкретный displayed written с конкретным reading/sense. Сейчас scoring identity и coverage tied to displayed spelling.\n5. Только после этих исследований измерить отдельный lossless normalized prototype и сверить запросы/size. Production heuristic cross-entry merge сейчас не обоснован.\n')
put('## Артефакты и неизменность\n')
put('Сохранены `jmdict_report.py`, `render_report.py`, `report.json`, `report.md` в tools/diagnostics для воспроизводимости и review. Они не подключены к build pipeline и не входят в assets. Временный run.log удалён после завершения.\n')
put('SHA-256 inputs:'); table(['Input','SHA-256'],r['input_sha256'].items())
put('Версии без изменений: app `v0.9.0`, versionCode `46`, dictionary `1`. Build/device/UI tests не запускались; для read-only диагностики они не требуются.\n')
(HERE/'report.md').write_text('\n'.join(lines),encoding='utf-8')
print('Written:',HERE/'report.md')
