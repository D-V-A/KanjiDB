"""Build source-preserving word tables; no lexical/heuristic cross-entry merge."""
import sqlite3
import xml.etree.ElementTree as ET
from pathlib import Path

BASE_DIR = Path(__file__).resolve().parent
XML_PATH = BASE_DIR / 'data/jmdict.xml'
DB_PATH = BASE_DIR.parent / 'app/src/main/assets/dictionary.db'
XML_LANG = '{http://www.w3.org/XML/1998/namespace}lang'
WORD_TABLES = [
    'word_sense_form', 'word_meaning', 'word_sense', 'word_entry_written_reading',
    'word_reading', 'word_written_kanji', 'word_entry_written', 'word_written',
    'word_entry', 'word_kanji', 'word_form', 'word',
]


def normalize_language(language):
    return {'eng':'en','fre':'fr','fra':'fr','ger':'de','deu':'de','spa':'es',
            'por':'pt','rus':'ru','jpn':'ja'}.get(language, language)


def reading_priority_score(tags):
    scores = {'ichi1':1000,'news1':900,'spec1':800,'gai1':700,
              'ichi2':400,'news2':350,'spec2':300,'gai2':250}
    score = 0
    for tag in tags:
        if tag in scores:
            score += scores[tag]
        elif tag.startswith('nf'):
            try:
                score += max(0, 500-int(tag[2:])*10)
            except ValueError:
                pass
    return score


def import_dictionary(xml_path=XML_PATH, db_path=DB_PATH):
    if not xml_path.exists() or not db_path.exists():
        raise FileNotFoundError(f'Required inputs: {xml_path}, {db_path}; run KANJIDIC first')
    db = sqlite3.connect(db_path)
    try:
        db.execute('PRAGMA foreign_keys=ON')
        known = dict((c,i) for i,c in db.execute('SELECT id,character FROM kanji'))
        for table in WORD_TABLES:
            db.execute(f'DROP TABLE IF EXISTS {table}')
        db.executescript((BASE_DIR/'dictionary_word_schema.sql').read_text(encoding='utf-8'))
        written_ids = {}
        source_count = imported_count = 0
        for _, entry in ET.iterparse(xml_path, events=('end',)):
            if entry.tag != 'entry':
                continue
            source_count += 1
            eid = int(entry.findtext('ent_seq'))
            writings = [(order, k.findtext('keb'), bool(k.findall('ke_pri')))
                        for order,k in enumerate(entry.findall('k_ele')) if k.findtext('keb')]
            readings = []
            for order,r in enumerate(entry.findall('r_ele')):
                if not r.findtext('reb') or r.find('re_nokanji') is not None:
                    continue
                tags = [x.text for x in r.findall('re_pri') if x.text]
                readings.append((order,r.findtext('reb'),bool(tags),reading_priority_score(tags),
                                 {x.text for x in r.findall('re_restr')}))
            valid = [(wo,w,wc,ro,r,rc,rp) for wo,w,wc in writings if any(c in known for c in w)
                     for ro,r,rc,rp,restr in readings if not restr or w in restr]
            if not valid:
                entry.clear()
                continue
            db.execute('INSERT INTO word_entry VALUES (?)',(eid,))
            entry_writings = {}; entry_readings = {}; forms = []
            for wo,w,wc,ro,r,rc,rp in valid:
                if w not in written_ids:
                    wid = db.execute('INSERT INTO word_written(text) VALUES (?)',(w,)).lastrowid
                    written_ids[w] = wid
                    db.executemany('INSERT INTO word_written_kanji VALUES (?,?,?)',
                                   [(wid,known[c],pos) for pos,c in enumerate(w) if c in known])
                wid = written_ids[w]
                if wid not in entry_writings:
                    db.execute('INSERT INTO word_entry_written VALUES (?,?,?,?)',(eid,wid,wo,int(wc)))
                    entry_writings[wid] = w
                if ro not in entry_readings:
                    rid = db.execute('INSERT INTO word_reading(entry_id,text,reading_order,common,reading_priority) VALUES (?,?,?,?,?)',
                                     (eid,r,ro,int(rc),rp)).lastrowid
                    entry_readings[ro] = rid
                rid = entry_readings[ro]
                fid = db.execute('INSERT INTO word_entry_written_reading(entry_id,written_id,reading_id) VALUES (?,?,?)',
                                 (eid,wid,rid)).lastrowid
                forms.append((fid,w,r))
            for index,sense in enumerate(entry.findall('sense')):
                sk = {x.text for x in sense.findall('stagk')}
                sr = {x.text for x in sense.findall('stagr')}
                applicable = [fid for fid,w,r in forms if (not sk or w in sk) and (not sr or r in sr)]
                if not applicable:
                    continue
                sid = db.execute('INSERT INTO word_sense(entry_id,sense_index) VALUES (?,?)',(eid,index)).lastrowid
                db.executemany('INSERT INTO word_sense_form VALUES (?,?,?)',[(eid,sid,fid) for fid in applicable])
                db.executemany('INSERT OR IGNORE INTO word_meaning(sense_id,language,meaning,gloss_order) VALUES (?,?,?,?)',
                    [(sid,normalize_language(g.get(XML_LANG,'eng')),g.text,order)
                     for order,g in enumerate(sense.findall('gloss')) if g.text])
            imported_count += 1
            if imported_count % 10000 == 0:
                print(f'Imported source entries: {imported_count}',flush=True)
            entry.clear()
        db.commit()
        print(f'JMdict source entries read: {source_count}; imported: {imported_count}')
        for table in WORD_TABLES[:9]:
            print(f'{table}: {db.execute(f"SELECT COUNT(*) FROM {table}").fetchone()[0]}')
    finally:
        db.close()


if __name__ == '__main__':
    import_dictionary()
