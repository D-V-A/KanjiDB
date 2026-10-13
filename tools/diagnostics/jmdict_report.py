"""Read-only JMdict research. No importer execution, schema changes or DB writes.

Run: python -X utf8 tools/diagnostics/jmdict_report.py
Outputs: report.json / report.md in this diagnostic directory.
"""
from pathlib import Path
from collections import Counter, defaultdict
from itertools import combinations
import hashlib
import json
import re
import sqlite3
import time
import unicodedata
import xml.etree.ElementTree as ET

HERE = Path(__file__).resolve().parent
ROOT = HERE.parent.parent
DB = ROOT / 'app/src/main/assets/dictionary.db'
XML = ROOT / 'tools/data/jmdict.xml'


def digest(path):
    h = hashlib.sha256()
    with path.open('rb') as f:
        for block in iter(lambda: f.read(1024 * 1024), b''):
            h.update(block)
    return h.hexdigest()


def hira(s):
    return ''.join(chr(ord(c)-96) if 0x30a1 <= ord(c) <= 0x30f6 else c for c in s)


def distance(a, b, limit=None):
    if limit is not None and abs(len(a)-len(b)) > limit:
        return limit+1
    row = list(range(len(b)+1))
    for i, ca in enumerate(a, 1):
        nxt = [i]
        for j, cb in enumerate(b, 1):
            nxt.append(min(nxt[-1]+1, row[j]+1, row[j-1]+(ca != cb)))
        row = nxt
        if limit is not None and min(row) > limit:
            return limit+1
    return row[-1]


def one_edit(a, b):
    if abs(len(a)-len(b)) > 1 or a == b:
        return False
    if len(a) == len(b):
        return sum(x != y for x, y in zip(a, b)) == 1
    if len(a) > len(b):
        a, b = b, a
    i = 0
    while i < len(a) and a[i] == b[i]:
        i += 1
    return a[i:] == b[i+1:]


def edit_kind(a, b):
    if len(a) != len(b):
        small, large = sorted([a, b], key=len)
        i = 0
        while i < len(small) and small[i] == large[i]:
            i += 1
        added = large[i] if i < len(large) else ''
        if added == 'っ': return 'small-tsu insertion/deletion'
        if added in 'ーうおいえ': return 'long-vowel-like insertion/deletion (proxy)'
        return 'other insertion/deletion'
    changed = [(x, y) for x, y in zip(a, b) if x != y]
    if len(changed) != 1: return 'other'
    x, y = changed[0]
    base = lambda c: unicodedata.normalize('NFD', c)[0]
    if base(x) == base(y): return 'voiced/unvoiced or semi-voiced kana'
    if x in 'ーうおいえ' and y in 'ーうおいえ': return 'long-vowel-like substitution (proxy)'
    return f'other substitution'


def is_kanji(c):
    n = ord(c)
    return 0x3400 <= n <= 0x9fff or 0xf900 <= n <= 0xfaff or 0x20000 <= n <= 0x323af or c == '〇'


def is_kana(c):
    return 0x3040 <= ord(c) <= 0x30ff and unicodedata.category(c).startswith('L')


def spelling_kind(a, b):
    ka = ''.join(c for c in a if is_kanji(c))
    kb = ''.join(c for c in b if is_kanji(c))
    if ka == kb and ka:
        # Strict subsequence with only kana removed/added: crude okurigana proxy.
        def kana_insertion(short, long):
            i = 0
            for c in long:
                if i < len(short) and c == short[i]: i += 1
                elif not is_kana(c): return False
            return i == len(short)
        if len(a) != len(b) and kana_insertion(*sorted([a,b], key=len)):
            return 'added/removed kana (okurigana proxy)'
        return 'kana spelling variation, same kanji sequence'
    if len(a) == len(b):
        changes = [(x,y) for x,y in zip(a,b) if x != y]
        if len(changes) == 1 and all(is_kanji(c) for c in changes[0]):
            return 'one kanji replaced by another'
        if changes and all((is_kanji(x) and is_kana(y)) or (is_kana(x) and is_kanji(y)) for x,y in changes):
            return 'exact-position kanji/kana substitution'
    # Lexicographic pair orientation is arbitrary: direction is diagnostic, not etymology.
    if set(ka) != set(kb) and all(is_kanji(c) or is_kana(c) for c in a+b):
        if len(ka) > len(kb) and all(c in ka for c in kb): return 'kanji replaced by kana (proxy)'
        if len(kb) > len(ka) and all(c in kb for c in ka): return 'kana replaced by kanji (proxy)'
        return 'mixed kanji/kana changes'
    return 'other/unclassified'


def overlap(a, b):
    return len(a & b)/max(len(a),len(b)) if a and b else 0.0


def histogram(items):
    return dict(sorted(Counter(items).items()))


def log(s):
    print(s, flush=True)


def main():
    started = time.monotonic()
    input_hashes = {str(p.relative_to(ROOT)): digest(p) for p in [DB, XML]}
    db = sqlite3.connect(DB.as_uri()+'?mode=ro', uri=True)
    db.execute('PRAGMA query_only = ON')
    stats = {'db_bytes': DB.stat().st_size, 'db_user_version': db.execute('PRAGMA user_version').fetchone()[0]}
    stats['schema'] = dict(db.execute("SELECT name,sql FROM sqlite_master WHERE type='table' AND name LIKE 'word_%'"))
    for table in ['word_form','word_meaning','word_kanji']:
        stats[table] = db.execute(f'SELECT COUNT(*) FROM {table}').fetchone()[0]
    log('Loading current word entities and gloss provenance')
    nodes = []
    key_index = {}
    form_node = {}
    form_reading = {}
    by_entry = defaultdict(set)
    by_written = defaultdict(set)
    exact_reading = defaultdict(set)
    normalized_reading = defaultdict(set)
    for fid, eid, w, r in db.execute('SELECT id,entry_id,written,reading FROM word_form ORDER BY entry_id,written,reading_order,id'):
        key = (eid,w)
        if key not in key_index:
            idx = len(nodes); key_index[key] = idx
            nodes.append({'entry':eid,'written':w,'readings':set(),'glosses':set(),'ci':set(),'raw_glosses':[],'senses':set()})
            by_entry[eid].add(idx); by_written[w].add(idx)
        idx = key_index[key]
        nodes[idx]['readings'].add(r)
        form_node[fid] = idx; form_reading[fid] = r
        exact_reading[r].add(idx); normalized_reading[hira(r)].add(idx)
    sense_gloss = set(); valid_sense = set(); reading_gloss = set()
    for fid, sense, gloss in db.execute("SELECT word_form_id,sense_index,meaning FROM word_meaning WHERE language='en'"):
        idx = form_node[fid]; n = nodes[idx]
        n['raw_glosses'].append(gloss)
        n['glosses'].add(gloss.strip(' ')); n['ci'].add(gloss.strip(' ').casefold()); n['senses'].add(sense)
        sense_gloss.add((n['entry'],sense,gloss.strip(' ')))
        valid_sense.add((n['entry'],sense))
        reading_gloss.add((idx,form_reading[fid],sense,gloss.strip(' ')))
    for n in nodes:
        n['tokens']=set(re.findall(r"[a-z0-9]+",' '.join(n['ci']))) - {'a','an','the','to','of','in','on','and','or','for','with','at','by','as'}
    stats.update(entries=len(by_entry), entry_written=len(nodes), written=len(by_written), reading=len(exact_reading),
                 written_reading=len({(n['written'],r) for n in nodes for r in n['readings']}),
                 source_sense_gloss=len(sense_gloss), source_senses=len(valid_sense), english_gloss_rows=sum(len(n['raw_glosses']) for n in nodes))

    def node_view(idx):
        n=nodes[idx]
        return {'entry_id':n['entry'],'written':n['written'],'readings':sorted(n['readings']), 'meanings':sorted(n['glosses'])}
    def entry_view(eid):
        return {'entry_id':eid,'forms':[node_view(i) for i in sorted(by_entry[eid],key=lambda i:nodes[i]['written'])]}

    log('Reading source JMdict (source variants include forms excluded by the importer)')
    source = Counter(); source_multi = Counter(); special_source=[]
    targets={'五','男','前','日','飛切り','飛び切り','飛びきり','とびっ切り','今時','今どき','学びや','学び舎','学舎'}
    known = {r[0] for r in db.execute('SELECT character FROM kanji')}
    reproduced=set(); lost_written=0
    for _, e in ET.iterparse(XML, events=('end',)):
        if e.tag != 'entry': continue
        source['all_entries']+=1
        eid=int(e.findtext('ent_seq')); ws=[x.text for x in e.findall('k_ele/keb') if x.text]
        source_multi[len(set(ws))]+=1
        if ws: source['importer_processed_counter']+=1
        else: source['no_k_ele']+=1
        source_readings=[]; valid_forms=[]
        for reading_element in e.findall('r_ele'):
            r=reading_element.findtext('reb'); restrict={x.text for x in reading_element.findall('re_restr')}
            nok=reading_element.find('re_nokanji') is not None
            source_readings.append({'reading':r,'restrict':sorted(restrict),'no_kanji':nok})
            if r and not nok:
                valid_forms.extend((w,r) for w in ws if (not restrict or w in restrict) and any(c in known for c in w))
        if valid_forms: reproduced.add(eid)
        else:
            source['fully_filtered']+=1
            if ws: source['processed_but_no_imported_forms']+=1
        if eid in by_entry:
            lost_written += len(set(ws)-{nodes[i]['written'] for i in by_entry[eid]})
            if e.findall('r_ele/re_restr'): source['imported_entries_with_re_restr']+=1
            if e.findall('sense/stagk'): source['imported_entries_with_stagk']+=1
            if e.findall('sense/stagr'): source['imported_entries_with_stagr']+=1
        if targets.intersection(ws):
            special_source.append({'entry_id':eid,'written':ws,'readings':source_readings,
                                   'meanings':[g.text for g in e.findall('sense/gloss') if g.text]})
        e.clear()
    assert reproduced == set(by_entry), 'Source/DB imported entry set differs'
    stats['source']=dict(source); stats['source_written_distribution']=dict(sorted(source_multi.items())); stats['source_forms_missing_within_imported_entries']=lost_written

    log('Exact-written duplicates, multi-written entries, Model C components')
    duplicates={w: ids for w,ids in by_written.items() if len(ids)>1}
    duplicate_stats=Counter(); gloss_before=gloss_after=ci_after=0; cross_unique_before=cross_unique_after=0
    duplicate_rank=[]
    for w, ids in duplicates.items():
        ns=[nodes[i] for i in ids]; rs=[n['readings'] for n in ns]; gs=[n['ci'] for n in ns]
        if all(r==rs[0] for r in rs): duplicate_stats['identical_reading_sets']+=1
        elif any(a & b for a,b in combinations(rs,2)): duplicate_stats['some_reading_overlap']+=1
        else: duplicate_stats['pairwise_disjoint_readings']+=1
        scores=[overlap(a,b) for a,b in combinations(gs,2)]
        if all(g==gs[0] and g for g in gs): duplicate_stats['identical_meaning_sets_ci']+=1
        elif max(scores,default=0)>=.75: duplicate_stats['at_least_one_meaning_pair_overlap_ge_075']+=1
        elif max(scores,default=0)==0: duplicate_stats['no_shared_exact_gloss']+=1
        else: duplicate_stats['partial_meaning_overlap_below_075']+=1
        raw=sum(len(n['raw_glosses']) for n in ns); unique=len(set().union(*(n['glosses'] for n in ns)))
        gloss_before+=raw; gloss_after+=unique; ci_after+=len(set().union(*gs))
        cross_unique_before+=sum(len(n['glosses']) for n in ns); cross_unique_after+=unique
        duplicate_rank.append((raw-unique,w,raw,unique))
    stats['exact_written']={'count':len(duplicates),'source_entry_union':len({nodes[i]['entry'] for ids in duplicates.values() for i in ids}),
        'entry_written_memberships':sum(len(x) for x in duplicates.values()),'distribution':histogram(len(x) for x in duplicates.values()),
        'classifications':dict(duplicate_stats),'english_rows_before':gloss_before,'trim_gloss_after_per_written':gloss_after,
        'ci_gloss_after_per_written':ci_after,'per_entry_written_unique_gloss_before':cross_unique_before,'per_written_unique_gloss_after':cross_unique_after}
    stats['entry_written_distribution']=histogram(len(x) for x in by_entry.values())
    multi=[eid for eid,ids in by_entry.items() if len(ids)>1]
    multi_stats=Counter(); lengths=Counter(); max_lengths=[]; reading_diff_examples=[]; disjoint_examples=[]; small_examples=[]
    spelling_counts=Counter(); spelling_examples=defaultdict(list); actual_small_examples=defaultdict(list)
    for eid in multi:
        ids=sorted(by_entry[eid]); ns=[nodes[i] for i in ids]; sets=[n['readings'] for n in ns]
        if all(r==sets[0] for r in sets): multi_stats['same_reading_sets']+=1
        else:
            multi_stats['different_reading_sets']+=1
            if len(reading_diff_examples)<30: reading_diff_examples.append(eid)
        normalized_sets=[{hira(r) for r in readings} for readings in sets]
        if not all(r==sets[0] for r in sets) and all(r==normalized_sets[0] for r in normalized_sets):
            multi_stats['different_exact_sets_but_same_after_katakana_to_hiragana']+=1
        disjoint=[(a,b) for a,b in combinations(sets,2) if not a & b]
        if disjoint:
            multi_stats['at_least_one_disjoint_pair']+=1
            if len(disjoint_examples)<30: disjoint_examples.append(eid)
        # Every pair with differing sets must have bidirectional nearest normalized distance <=1.
        differing=[(a,b) for a,b in combinations(sets,2) if a != b]
        small=bool(differing) and all(all(min(distance(hira(r),hira(s),1) for s in b)<=1 for r in a) and
                  all(min(distance(hira(r),hira(s),1) for s in a)<=1 for r in b) for a,b in differing)
        if small:
            multi_stats['differences_only_small_kana_normalized_distance_le_1']+=1
            if len(small_examples)<30: small_examples.append(eid)
            if not all(r==normalized_sets[0] for r in normalized_sets):
                multi_stats['small_changes_with_actual_normalized_distance_1']+=1
        for na,nb in combinations(ns,2):
            if na['readings'] & nb['readings']: continue
            if {hira(r) for r in na['readings']} & {hira(r) for r in nb['readings']}: continue
            matches=[(hira(ra),hira(rb)) for ra in na['readings'] for rb in nb['readings'] if one_edit(hira(ra),hira(rb))]
            for ra,rb in matches:
                kind=edit_kind(ra,rb)
                if len(actual_small_examples[kind])<8:
                    actual_small_examples[kind].append({'a':node_view(key_index[(eid,na['written'])]),'b':node_view(key_index[(eid,nb['written'])]),'reading_a':ra,'reading_b':rb})
        for n in ns: lengths[len(n['written'])]+=1
        max_lengths.append(max(len(n['written']) for n in ns))
        for a,b in combinations(sorted(n['written'] for n in ns),2):
            kind=spelling_kind(a,b); spelling_counts[kind]+=1
            if len(spelling_examples[kind])<12: spelling_examples[kind].append({'entry_id':eid,'a':a,'b':b})
    stats['multi_written']={**dict(multi_stats),'entries':len(multi),'reading_max':max(len(set().union(*(nodes[i]['readings'] for i in by_entry[e]))) for e in multi),
        'length_distribution':dict(sorted(lengths.items())),'max_length_distribution':histogram(max_lengths),
        'max_length':max(max_lengths),'max_len_le_2':sum(x<=2 for x in max_lengths),'max_len_le_3':sum(x<=3 for x in max_lengths),
        'max_len_ge_4':sum(x>=4 for x in max_lengths),'max_len_ge_6':sum(x>=6 for x in max_lengths)}
    stats['spelling_pair_classification']=dict(spelling_counts)
    parent=list(range(len(nodes)))
    def find(i):
        while parent[i]!=i:
            parent[i]=parent[parent[i]]; i=parent[i]
        return i
    def union(ids):
        ids=list(ids); root=find(ids[0])
        for i in ids[1:]: parent[find(i)]=root
    for ids in by_entry.values(): union(ids)
    for ids in by_written.values(): union(ids)
    components=defaultdict(list)
    for i in range(len(nodes)): components[find(i)].append(i)
    group_views=[]; group_senses=0; group_reading_links=0; group_gloss_links=0; written_kanji_links=0
    for ids in components.values():
        ns=[nodes[i] for i in ids]
        ws=sorted({n['written'] for n in ns}); es=sorted({n['entry'] for n in ns}); rs=sorted(set().union(*(n['readings'] for n in ns))); gs=sorted(set().union(*(n['glosses'] for n in ns)))
        group_views.append({'written':ws,'source_entry_ids':es,'readings':rs,'meanings':gs})
        group_reading_links+=len(rs); group_gloss_links+=len(gs)
    written_kanji_links=sum(sum(c in known for c in w) for w in by_written)
    stats['models']={'A':len(nodes),'B':len(by_written),'C':len(components),'C_written_distribution':histogram(len(g['written']) for g in group_views),
        'C_entry_distribution':histogram(len(g['source_entry_ids']) for g in group_views),'C_reading_distribution':histogram(len(g['readings']) for g in group_views),
        'C_meaning_distribution':histogram(len(g['meanings']) for g in group_views),
        'C_max_written_length_distribution':histogram(max(map(len,g['written'])) for g in group_views),
        'C_multi_written_displayed_form_lengths':histogram(len(w) for g in group_views if len(g['written'])>1 for w in g['written']),
        'C_group_reading_links':group_reading_links,'C_group_unique_gloss_links':group_gloss_links,'unique_written_kanji_position_links':written_kanji_links,
        'multi_entry_gt_1':sum(len(g['source_entry_ids'])>1 for g in group_views),'multi_entry_gt_2':sum(len(g['source_entry_ids'])>2 for g in group_views),
        'multi_entry_gt_5':sum(len(g['source_entry_ids'])>5 for g in group_views),'multi_entry_gt_10':sum(len(g['source_entry_ids'])>10 for g in group_views)}

    log('Same-exact-reading cross-entry pairs')
    same_pairs=set(); same_reading_dist=Counter(); extremes=[]
    for r,ids in exact_reading.items():
        ws={nodes[i]['written'] for i in ids}; count=len(ws)
        bucket='1' if count==1 else '2' if count==2 else '3-5' if count<=5 else '6-10' if count<=10 else '>10'
        same_reading_dist[bucket]+=1
        extremes.append((count,r))
        for a,b in combinations(sorted(ids),2):
            if nodes[a]['entry']!=nodes[b]['entry'] and nodes[a]['written']!=nodes[b]['written']:
                same_pairs.add((a,b))
    same_overlap=Counter(); same_high=[]; false_candidates=[]; token_counts=Counter(); token_examples=[]
    for a,b in same_pairs:
        ov=overlap(nodes[a]['ci'],nodes[b]['ci'])
        ta,tb=nodes[a]['tokens'],nodes[b]['tokens']
        tj=len(ta & tb)/len(ta | tb) if ta or tb else 0
        if tj>=.8:
            token_counts['token_jaccard_ge_080']+=1
            if ov==0:
                token_counts['token_jaccard_ge_080_but_exact_gloss_overlap_0']+=1
                if len(token_examples)<20: token_examples.append({'a':node_view(a),'b':node_view(b),'token_jaccard':tj})
        same_overlap['0' if ov==0 else '(0,.5)' if ov<.5 else '[.5,.75)' if ov<.75 else '[.75,1)' if ov<1 else '1']+=1
        if ov>=.75: same_high.append((a,b,ov))
        elif ov==0 and len(false_candidates)<100: false_candidates.append((a,b))
    stats['same_reading']={'written_count_buckets':dict(same_reading_dist),'max_written':max(x[0] for x in extremes),
                          'cross_entry_distinct_entity_pairs':len(same_pairs),'overlap_distribution':dict(same_overlap),'token_diagnostics':dict(token_counts)}

    log('Global normalized-reading distance=1 via single-deletion index (not all-pairs)')
    readings=sorted(normalized_reading); rid={r:i for i,r in enumerate(readings)}
    deletion_index=defaultdict(list)
    near_rpairs=set()
    for i,r in enumerate(readings):
        for shortened in {r[:j]+r[j+1:] for j in range(len(r))}:
            if shortened in rid: near_rpairs.add(tuple(sorted((i,rid[shortened]))))
            for j in deletion_index[shortened]:
                if one_edit(r,readings[j]): near_rpairs.add((j,i))
            deletion_index[shortened].append(i)
    near_kinds=Counter(); near_examples=defaultdict(list); near_cross_pair_occurrences=0
    for a,b in near_rpairs:
        ra,rb=readings[a],readings[b]; kind=edit_kind(ra,rb); near_kinds[kind]+=1
        if len(near_examples[kind])<15: near_examples[kind].append([ra,rb])
        aa=normalized_reading[ra]; bb=normalized_reading[rb]
        # Occurrences count reading-pair x entity-pair, not deduplicated across all readings.
        near_cross_pair_occurrences+=sum(nodes[x]['entry']!=nodes[y]['entry'] and nodes[x]['written']!=nodes[y]['written'] for x in aa for y in bb)
    stats['near_reading']={'normalized_reading_count':len(readings),'global_distance_1_reading_pairs':len(near_rpairs),
        'distance_1_cross_entry_entity_pair_occurrences':near_cross_pair_occurrences,'edit_kinds':dict(near_kinds)}
    del deletion_index

    log('Shared-gloss inverted index: meaning-supported distance=1/2 cross-entry candidates')
    gloss_index=defaultdict(list)
    for i,n in enumerate(nodes):
        for g in n['ci']: gloss_index[g].append(i)
    shared_counts=Counter()
    for ids in gloss_index.values():
        for a,b in combinations(ids,2):
            if nodes[a]['entry']!=nodes[b]['entry'] and nodes[a]['written']!=nodes[b]['written']:
                shared_counts[(a,b)]+=1
    candidates=[]; candidate_counts=Counter(); high_near=[]
    for (a,b), common in shared_counts.items():
        ov=common/max(len(nodes[a]['ci']),len(nodes[b]['ci']))
        if ov<.5: continue
        ra={hira(r) for r in nodes[a]['readings']}; rb={hira(r) for r in nodes[b]['readings']}
        rd=0 if ra & rb else min(distance(x,y,2) for x in ra for y in rb)
        if rd>2: continue
        wa,wb=nodes[a]['written'],nodes[b]['written']; wd=distance(wa,wb); similarity=1-wd/max(len(wa),len(wb))
        if ov>=.5: candidate_counts[f'overlap_ge_050_reading_distance_{rd}']+=1
        if ov>=.75:
            candidate_counts[f'overlap_ge_075_reading_distance_{rd}']+=1
            if similarity>=.5: candidate_counts[f'overlap_ge_075_reading_distance_{rd}_written_similarity_ge_050']+=1
            if wd<=1: candidate_counts[f'overlap_ge_075_reading_distance_{rd}_written_distance_le_1']+=1
            if ov==1: candidate_counts[f'overlap_1_reading_distance_{rd}']+=1
        candidates.append({'a':node_view(a),'b':node_view(b),'reading_distance_normalized':rd,'written_distance':wd,'written_similarity':round(similarity,4),'meaning_overlap':round(ov,4),
                           'already_same_Model_C':find(a)==find(b)})
        if ov>=.75 and rd in (1,2): high_near.append(candidates[-1])
    stats['cross_entry_candidates']={'counts':dict(candidate_counts),'shared_exact_gloss_cross_entry_pairs':len(shared_counts),
        'scope':'All cross-entry different-written entity pairs with >=.5 exact casefolded-gloss-set overlap; reading distance 0..2. Distance=2 is exhaustive only within this meaning-supported scope.',
        'high_overlap_near_1_outside_C':sum(c['meaning_overlap']>=.75 and c['reading_distance_normalized']==1 and not c['already_same_Model_C'] for c in candidates),
        'high_overlap_exact_outside_C':sum(c['meaning_overlap']>=.75 and c['reading_distance_normalized']==0 and not c['already_same_Model_C'] for c in candidates)}
    # Deterministic balanced selection: near, exact similar, exact dissimilar.
    candidates.sort(key=lambda c:(c['already_same_Model_C'],-c['meaning_overlap'],-c['written_similarity'],c['a']['entry_id'],c['a']['written'],c['b']['entry_id']))
    chosen=[]; seen=set(); seen_entry_pairs=set()
    # Include requested lexical examples before ranking the wider sample.
    for c in candidates:
        if c['a']['written']=='飛びきり' and c['b']['written']=='とびっ切り':
            chosen.append(c); seen_entry_pairs.add((c['a']['entry_id'],c['b']['entry_id']))
    for pool in [
        [c for c in candidates if c['reading_distance_normalized']==1 and c['meaning_overlap']>=.75],
        [c for c in candidates if c['reading_distance_normalized']==0 and c['written_similarity']>=.5 and c['meaning_overlap']>=.75],
        [c for c in candidates if c['reading_distance_normalized']==2 and c['meaning_overlap']>=.75],
        candidates]:
        take=15 if len(chosen)<45 else 50-len(chosen)
        for c in pool:
            key=(c['a']['entry_id'],c['a']['written'],c['b']['entry_id'],c['b']['written'])
            epair=(c['a']['entry_id'],c['b']['entry_id'])
            if key in seen or epair in seen_entry_pairs: continue
            chosen.append(c); seen.add(key); seen_entry_pairs.add(epair); take-=1
            if take<=0 or len(chosen)>=50: break
        if len(chosen)>=50: break
    false_candidates.sort(key=lambda pair:(-max(len(exact_reading[r]) for r in nodes[pair[0]]['readings'] & nodes[pair[1]]['readings']),nodes[pair[0]]['written']))
    examples={
        'requested_source_entries':special_source,
        'requested_local_forms':{w:[node_view(i) for i in sorted(by_written.get(w,[]))] for w in sorted(targets)},
        'exact_written_duplicates':[{'written':w,'forms':[node_view(i) for i in sorted(ids)]} for w,ids in sorted(duplicates.items(),key=lambda x:(-len(x[1]),x[0]))[:40]],
        'duplicate_gloss_top':[{'written':w,'rows':raw,'distinct_glosses':unique,'forms':[node_view(i) for i in sorted(duplicates[w])]} for _,w,raw,unique in sorted(duplicate_rank,reverse=True)[:20]],
        'multi_written_entries':[entry_view(e) for e in sorted(multi,key=lambda e:(-len(by_entry[e]),e))[:35]],
        'reading_differences':[entry_view(e) for e in reading_diff_examples],
        'disjoint_readings':[entry_view(e) for e in disjoint_examples],
        'small_reading_changes':[entry_view(e) for e in small_examples],
        'actual_small_reading_changes':dict(actual_small_examples),
        'same_reading_token_overlap':token_examples,
        'multi_written_most_readings':[entry_view(e) for e in sorted(multi,key=lambda e:-len(set().union(*(nodes[i]['readings'] for i in by_entry[e]))))[:8]],
        'long_multi_written':[entry_view(e) for e in sorted(multi,key=lambda e:(-max(len(nodes[i]['written']) for i in by_entry[e]),e))[:15]],
        'same_reading_extremes':[{'reading':r,'written_count':count,'forms':[node_view(i) for i in sorted(exact_reading[r])]} for count,r in sorted(extremes,reverse=True)[:10]],
        'near_reading_edits':dict(near_examples),'near_high_overlap':sorted(high_near,key=lambda c:(c['reading_distance_normalized'],-c['written_similarity']))[:40],
        'spelling_variations':dict(spelling_examples),'cross_entry_candidates_50':chosen,
        'false_positive_homophones':[{'a':node_view(a),'b':node_view(b),'common_reading':sorted(nodes[a]['readings'] & nodes[b]['readings']),'meaning_overlap':0} for a,b in false_candidates[:20]],
        'model_C_biggest_written':sorted(group_views,key=lambda g:(-len(g['written']),-len(g['source_entry_ids']),g['source_entry_ids']))[:30],
        'model_C_biggest_source_entries':sorted(group_views,key=lambda g:(-len(g['source_entry_ids']),-len(g['written']),g['source_entry_ids']))[:30],
        'model_C_most_readings':sorted(group_views,key=lambda g:(-len(g['readings']),g['source_entry_ids']))[:10],
        'model_C_most_meanings':sorted(group_views,key=lambda g:(-len(g['meanings']),g['source_entry_ids']))[:10]}
    stats['db_page_count']=db.execute('PRAGMA page_count').fetchone()[0]; stats['db_freelist_count']=db.execute('PRAGMA freelist_count').fetchone()[0]
    try:
        stats['object_bytes']=dict(db.execute('SELECT name,SUM(pgsize) FROM dbstat GROUP BY name'))
    except sqlite3.OperationalError:
        stats['object_bytes']='dbstat unavailable'
    db.close()
    assert input_hashes == {str(p.relative_to(ROOT)): digest(p) for p in [DB,XML]}, 'Research changed input files'
    result={'input_sha256':input_hashes,'unchanged_inputs_verified':True,'stats':stats,'examples':examples,'elapsed_seconds':round(time.monotonic()-started,2)}
    (HERE/'report.json').write_text(json.dumps(result,ensure_ascii=False,indent=2),encoding='utf-8')
    log(json.dumps(stats,ensure_ascii=False,indent=2))
    log(f"Report JSON written; elapsed {result['elapsed_seconds']}s")


if __name__=='__main__':
    main()
