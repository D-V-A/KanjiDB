"""Verify dictionary integrity, release metadata and lossless imported applicability."""
import sqlite3
import xml.etree.ElementTree as ET
from collections import defaultdict
from dictionary_version import DB_PATH, ASSET_VERSION_PATH, TOOLS_DIR, read_version
from import_jmdict import normalize_language, reading_priority_score, XML_LANG


def verify_source(db):
    actual_forms = defaultdict(set)
    actual_meanings = defaultdict(set)
    for row in db.execute('''SELECT f.entry_id,w.text,r.text,ew.written_order,r.reading_order,
            ew.common,r.common,r.reading_priority FROM word_entry_written_reading f
            JOIN word_written w ON w.id=f.written_id JOIN word_reading r ON r.id=f.reading_id
            JOIN word_entry_written ew ON ew.entry_id=f.entry_id AND ew.written_id=f.written_id'''):
        actual_forms[row[0]].add(row[1:])
    for row in db.execute('''SELECT f.entry_id,w.text,r.text,s.sense_index,m.language,m.meaning,m.gloss_order
            FROM word_sense_form sf JOIN word_sense s ON s.id=sf.sense_id
            JOIN word_meaning m ON m.sense_id=s.id JOIN word_entry_written_reading f ON f.id=sf.form_id
            JOIN word_written w ON w.id=f.written_id JOIN word_reading r ON r.id=f.reading_id'''):
        actual_meanings[row[0]].add(row[1:])
    known = {c for (c,) in db.execute('SELECT character FROM kanji')}
    entries = 0
    for _,e in ET.iterparse(TOOLS_DIR/'data/jmdict.xml', events=('end',)):
        if e.tag!='entry': continue
        eid=int(e.findtext('ent_seq')); expected_forms=set(); expected_glosses=set()
        for wi,k in enumerate(e.findall('k_ele')):
            w=k.findtext('keb')
            if not w or not any(c in known for c in w): continue
            for ri,r in enumerate(e.findall('r_ele')):
                reading=r.findtext('reb'); restrictions=[x.text for x in r.findall('re_restr')]
                if not reading or r.find('re_nokanji') is not None or (restrictions and w not in restrictions): continue
                tags=[x.text for x in r.findall('re_pri') if x.text]
                expected_forms.add((w,reading,wi,ri,int(bool(k.findall('ke_pri'))),int(bool(tags)),reading_priority_score(tags)))
                for si,s in enumerate(e.findall('sense')):
                    sk=[x.text for x in s.findall('stagk')]; sr=[x.text for x in s.findall('stagr')]
                    if (sk and w not in sk) or (sr and reading not in sr): continue
                    seen=set()
                    for gi,g in enumerate(s.findall('gloss')):
                        if not g.text: continue
                        language=normalize_language(g.get(XML_LANG,'eng'))
                        if (language,g.text) in seen: continue
                        seen.add((language,g.text))
                        expected_glosses.add((w,reading,si,language,g.text,gi))
        assert actual_forms.pop(eid,set())==expected_forms, f'Form/metadata/re_restr mismatch for entry {eid}'
        assert actual_meanings.pop(eid,set())==expected_glosses, f'Sense/gloss/stagk/stagr mismatch for entry {eid}'
        if expected_forms: entries+=1
        e.clear()
    assert not actual_forms and not actual_meanings, 'Unexpected source entries'
    assert db.execute('SELECT COUNT(*) FROM word_entry').fetchone()[0]==entries
    print(f'[ OK ] XML applicability and metadata parity: {entries} distinct source entries')


def main():
    if not DB_PATH.exists(): raise FileNotFoundError(DB_PATH)
    db=sqlite3.connect(DB_PATH.resolve().as_uri()+'?mode=ro',uri=True)
    try:
        version=read_version()
        assert db.execute('PRAGMA user_version').fetchone()[0]==read_version(ASSET_VERSION_PATH)==version
        assert db.execute('PRAGMA integrity_check').fetchall()==[('ok',)]
        assert not db.execute('PRAGMA foreign_key_check').fetchall()
        print(f'[ OK ] SQLite integrity, foreign keys, source/DB/sidecar version {version}')
        tables=['kanji','kanji_meaning','kanji_reading','jlpt_kanji','word_entry','word_written',
                'word_entry_written','word_reading','word_entry_written_reading','word_sense','word_meaning',
                'word_sense_form','word_written_kanji']
        for table in tables:
            count=db.execute(f'SELECT COUNT(*) FROM {table}').fetchone()[0]
            assert count>0, f'Empty table {table}'
            print(f'  {table}: {count}')
        for c,values in [('山',(3,1,131,1)),('水',(4,1,223,1)),('火',(4,1,574,1)),('日',(4,1,1,1)),('生',(5,1,29,1))]:
            row=db.execute('SELECT stroke_count,grade,frequency,joyo FROM kanji WHERE character=?',(c,)).fetchone()
            assert row==values, f'Kanji sample metadata {c}: {row}'
        for table in ['kanji_meaning','word_meaning']:
            languages={lang for (lang,) in db.execute(f'SELECT DISTINCT language FROM {table}')}
            assert 'en' in languages and not languages.intersection({'eng','fre','fra','ger','deu','spa','por','rus','jpn'}), f'Language normalization: {table}'
        expected={}
        for line in (TOOLS_DIR/'data/jlpt.tsv').read_text(encoding='utf-8-sig').splitlines():
            if not line.strip(): continue
            level,characters=line.strip().split('\t')
            for c in characters:
                if c.strip():
                    assert c not in expected, f'Duplicate kanji in JLPT source: {c}'
                    expected[c]=int(level.upper().removeprefix('N'))
        expected['分']=5
        actual=dict(db.execute('SELECT k.character,j.level FROM jlpt_kanji j JOIN kanji k ON k.id=j.kanji_id'))
        known={c for (c,) in db.execute('SELECT character FROM kanji')}
        assert actual=={c:level for c,level in expected.items() if c in known}, 'JLPT source/override mismatch'
        assert set(actual.values())=={1,2,3,4,5}
        print('[ OK ] KANJIDIC samples; JLPT source + existing override')
        verify_source(db)
        # Kanji links correspond only to exact spelling, retaining repeated positions.
        kid=dict(db.execute('SELECT character,id FROM kanji'))
        links=defaultdict(list)
        for wid,k,pos in db.execute('SELECT written_id,kanji_id,position FROM word_written_kanji ORDER BY position'):
            links[wid].append((k,pos))
        for wid,w in db.execute('SELECT id,text FROM word_written'):
            assert links[wid]==[(kid[c],pos) for pos,c in enumerate(w) if c in kid], f'Written kanji mismatch {w}'
        print('[ OK ] Exact-written kanji positions; no transitive lexical groups')
        print('DATABASE CHECK: OK')
    finally:
        db.close()


if __name__=='__main__':
    try:
        main()
    except Exception as error:
        print(f'DATABASE CHECK: FAILED: {error}')
        raise SystemExit(1)
