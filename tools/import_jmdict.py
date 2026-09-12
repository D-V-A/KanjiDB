import sqlite3
import xml.etree.ElementTree as ET
from pathlib import Path


BASE_DIR = Path(__file__).parent
XML_PATH = BASE_DIR / "data" / "jmdict.xml"
DB_PATH = (
    BASE_DIR.parent
    / "app"
    / "src"
    / "main"
    / "assets"
    / "dictionary.db"
)
XML_LANG = "{http://www.w3.org/XML/1998/namespace}lang"


def normalize_language(language):
    """
    Normalize source-specific language codes to short codes
    used consistently by KanjiDB.
    """

    mapping = {
        "eng": "en",
        "en": "en",
        "fre": "fr",
        "fra": "fr",
        "fr": "fr",
        "ger": "de",
        "deu": "de",
        "de": "de",
        "spa": "es",
        "es": "es",
        "por": "pt",
        "pt": "pt",
        "rus": "ru",
        "ru": "ru",
        "jpn": "ja",
        "ja": "ja",
    }

    return mapping.get(language, language)


def has_priority(tags):
    """
    For KanjiDB, common is deliberately simple:

    any JMdict priority marker -> common = 1

    This controls whether a word is shown before "Show more".
    """

    return 1 if tags else 0


def reading_priority_score(tags):
    """
    KanjiDB heuristic for choosing one preferred reading
    when the same written form has several valid readings.

    Higher score = more preferred.

    This is NOT an official JMdict numerical score.
    """

    score = 0

    for tag in tags:

        if tag == "ichi1":
            score += 1000

        elif tag == "news1":
            score += 900

        elif tag == "spec1":
            score += 800

        elif tag == "gai1":
            score += 700

        elif tag == "ichi2":
            score += 400

        elif tag == "news2":
            score += 350

        elif tag == "spec2":
            score += 300

        elif tag == "gai2":
            score += 250

        elif tag.startswith("nf"):
            try:
                rank = int(tag[2:])

                # Lower nf rank = more frequent.
                score += max(
                    0,
                    500 - rank * 10
                )

            except ValueError:
                pass

    return score


if not XML_PATH.exists():
    raise FileNotFoundError(
        f"JMdict file not found: {XML_PATH}"
    )

if not DB_PATH.exists():
    raise FileNotFoundError(
        f"Database not found: {DB_PATH}. "
        "Run import_kanjidic.py first."
    )

DB_PATH.parent.mkdir(
    parents=True,
    exist_ok=True
)

conn = sqlite3.connect(DB_PATH)

try:
    cur = conn.cursor()

    cur.execute("PRAGMA foreign_keys = ON")

    # --------------------------------------------------------
    # Check KANJIDIC stage
    # --------------------------------------------------------

    kanji_table = cur.execute("""
        SELECT name
        FROM sqlite_master
        WHERE type = 'table'
          AND name = 'kanji'
    """).fetchone()

    if kanji_table is None:
        raise RuntimeError(
            "Table 'kanji' not found. "
            "Run import_kanjidic.py first."
        )

    # --------------------------------------------------------
    # Rebuild only the JMdict part
    # --------------------------------------------------------

    cur.executescript("""
    DROP TABLE IF EXISTS word_kanji;
    DROP TABLE IF EXISTS word_meaning;
    DROP TABLE IF EXISTS word_form;

    CREATE TABLE word_form (
        id INTEGER PRIMARY KEY AUTOINCREMENT,

        entry_id INTEGER NOT NULL,

        written TEXT NOT NULL,
        reading TEXT NOT NULL,

        common INTEGER NOT NULL DEFAULT 0
            CHECK (common IN (0, 1)),

        reading_priority INTEGER NOT NULL DEFAULT 0,

        -- Original r_ele order inside the JMdict entry.
        -- Used only as deterministic fallback when priority ties.
        reading_order INTEGER NOT NULL,

        UNIQUE (
            entry_id,
            written,
            reading
        )
    );

    CREATE TABLE word_meaning (
        id INTEGER PRIMARY KEY AUTOINCREMENT,

        word_form_id INTEGER NOT NULL,
        sense_index INTEGER NOT NULL,

        language TEXT NOT NULL,
        meaning TEXT NOT NULL,

        FOREIGN KEY (word_form_id)
            REFERENCES word_form(id)
            ON DELETE CASCADE,

        UNIQUE (
            word_form_id,
            sense_index,
            language,
            meaning
        )
    );

    CREATE TABLE word_kanji (
        word_form_id INTEGER NOT NULL,
        kanji_id INTEGER NOT NULL,
        position INTEGER NOT NULL,

        FOREIGN KEY (word_form_id)
            REFERENCES word_form(id)
            ON DELETE CASCADE,

        FOREIGN KEY (kanji_id)
            REFERENCES kanji(id),

        PRIMARY KEY (
            word_form_id,
            position
        )
    );

    CREATE INDEX idx_word_form_written
        ON word_form(written);

    CREATE INDEX idx_word_form_reading
        ON word_form(reading);

    CREATE INDEX idx_word_form_common
        ON word_form(common);

    CREATE INDEX idx_word_form_entry_written
        ON word_form(entry_id, written);

    CREATE INDEX idx_word_form_preferred_reading
        ON word_form(
            entry_id,
            written,
            reading_priority DESC,
            reading_order ASC
        );

    CREATE INDEX idx_word_meaning_form
        ON word_meaning(word_form_id);

    CREATE INDEX idx_word_meaning_language
        ON word_meaning(language);

    CREATE INDEX idx_word_kanji_kanji
        ON word_kanji(kanji_id);
    """)

    # --------------------------------------------------------
    # Load KANJIDIC character -> ID lookup into RAM
    # --------------------------------------------------------

    kanji_by_character = {
        character: kanji_id
        for kanji_id, character in cur.execute("""
            SELECT id, character
            FROM kanji
        """)
    }

    print(
        f"Known kanji loaded: "
        f"{len(kanji_by_character)}"
    )

    entry_count = 0
    form_count = 0
    meaning_count = 0
    kanji_link_count = 0

    # --------------------------------------------------------
    # Stream JMdict
    # --------------------------------------------------------

    for event, entry in ET.iterparse(
        XML_PATH,
        events=("end",)
    ):

        if entry.tag != "entry":
            continue

        entry_id_text = entry.findtext("ent_seq")

        if entry_id_text is None:
            entry.clear()
            continue

        entry_id = int(entry_id_text)

        # ----------------------------------------------------
        # Written forms
        # ----------------------------------------------------

        writings = []

        for k_ele in entry.findall("k_ele"):

            written = k_ele.findtext("keb")

            if not written:
                continue

            priorities = [
                pri.text
                for pri in k_ele.findall("ke_pri")
                if pri.text
            ]

            writings.append({
                "written": written,
                "priority": priorities,
            })

        # KanjiDB currently ignores kana-only JMdict entries.
        if not writings:
            entry.clear()
            continue

        # ----------------------------------------------------
        # Readings
        # ----------------------------------------------------

        readings = []

        for reading_order, r_ele in enumerate(
            entry.findall("r_ele")
        ):

            reading = r_ele.findtext("reb")

            if not reading:
                continue

            restrictions = [
                restriction.text
                for restriction
                in r_ele.findall("re_restr")
                if restriction.text
            ]

            priorities = [
                pri.text
                for pri in r_ele.findall("re_pri")
                if pri.text
            ]

            no_kanji = (
                r_ele.find("re_nokanji")
                is not None
            )

            readings.append({
                "reading": reading,
                "restrictions": restrictions,
                "priority": priorities,
                "no_kanji": no_kanji,
                "order": reading_order,
            })

        # ----------------------------------------------------
        # Sense blocks
        # ----------------------------------------------------

        senses = []

        for sense_index, sense in enumerate(
            entry.findall("sense")
        ):

            restricted_writings = [
                item.text
                for item in sense.findall("stagk")
                if item.text
            ]

            restricted_readings = [
                item.text
                for item in sense.findall("stagr")
                if item.text
            ]

            glosses = []

            for gloss in sense.findall("gloss"):

                if not gloss.text:
                    continue

                source_language = gloss.get(
                    XML_LANG,
                    "eng"
                )

                language = normalize_language(
                    source_language
                )

                glosses.append({
                    "language": language,
                    "meaning": gloss.text,
                })

            senses.append({
                "index": sense_index,
                "writings": restricted_writings,
                "readings": restricted_readings,
                "glosses": glosses,
            })

        # ----------------------------------------------------
        # Generate only valid
        # written <-> reading combinations
        # ----------------------------------------------------

        for writing in writings:

            written = writing["written"]

            contained_kanji = []

            for position, character in enumerate(written):

                kanji_id = kanji_by_character.get(
                    character
                )

                if kanji_id is not None:
                    contained_kanji.append(
                        (position, kanji_id)
                    )

            # Ignore written forms without any known kanji.
            if not contained_kanji:
                continue

            for reading_info in readings:

                reading = reading_info["reading"]

                if reading_info["no_kanji"]:
                    continue

                restrictions = reading_info[
                    "restrictions"
                ]

                # re_restr:
                # reading applies only to listed written forms.
                if (
                    restrictions
                    and written not in restrictions
                ):
                    continue

                common = has_priority(
                    writing["priority"]
                    + reading_info["priority"]
                )

                reading_priority = (
                    reading_priority_score(
                        reading_info["priority"]
                    )
                )

                cur.execute("""
                    INSERT OR IGNORE INTO word_form (
                        entry_id,
                        written,
                        reading,
                        common,
                        reading_priority,
                        reading_order
                    )
                    VALUES (?, ?, ?, ?, ?, ?)
                """, (
                    entry_id,
                    written,
                    reading,
                    common,
                    reading_priority,
                    reading_info["order"]
                ))

                row = cur.execute("""
                    SELECT id
                    FROM word_form
                    WHERE entry_id = ?
                      AND written = ?
                      AND reading = ?
                """, (
                    entry_id,
                    written,
                    reading
                )).fetchone()

                if row is None:
                    raise RuntimeError(
                        "Failed to retrieve word_form: "
                        f"{entry_id}, "
                        f"{written}, "
                        f"{reading}"
                    )

                word_form_id = row[0]

                form_count += 1

                # --------------------------------------------
                # Link written form to every kanji it contains
                # --------------------------------------------

                for position, kanji_id in contained_kanji:

                    cur.execute("""
                        INSERT OR IGNORE INTO word_kanji (
                            word_form_id,
                            kanji_id,
                            position
                        )
                        VALUES (?, ?, ?)
                    """, (
                        word_form_id,
                        kanji_id,
                        position
                    ))

                    if cur.rowcount > 0:
                        kanji_link_count += 1

                # --------------------------------------------
                # Meanings respecting stagk / stagr
                # --------------------------------------------

                for sense in senses:

                    if (
                        sense["writings"]
                        and written
                        not in sense["writings"]
                    ):
                        continue

                    if (
                        sense["readings"]
                        and reading
                        not in sense["readings"]
                    ):
                        continue

                    for gloss in sense["glosses"]:

                        cur.execute("""
                            INSERT OR IGNORE
                            INTO word_meaning (
                                word_form_id,
                                sense_index,
                                language,
                                meaning
                            )
                            VALUES (?, ?, ?, ?)
                        """, (
                            word_form_id,
                            sense["index"],
                            gloss["language"],
                            gloss["meaning"]
                        ))

                        if cur.rowcount > 0:
                            meaning_count += 1

        entry_count += 1

        if entry_count % 10000 == 0:
            print(
                f"Entries processed: {entry_count}"
            )

        entry.clear()

    conn.commit()

    # --------------------------------------------------------
    # Summary
    # --------------------------------------------------------

    actual_form_count = cur.execute("""
        SELECT COUNT(*)
        FROM word_form
    """).fetchone()[0]

    actual_meaning_count = cur.execute("""
        SELECT COUNT(*)
        FROM word_meaning
    """).fetchone()[0]

    actual_link_count = cur.execute("""
        SELECT COUNT(*)
        FROM word_kanji
    """).fetchone()[0]

    print()
    print("JMdict import finished.")
    print(f"Entries processed: {entry_count}")
    print(f"Word forms: {actual_form_count}")
    print(f"Meanings: {actual_meaning_count}")
    print(f"Kanji links: {actual_link_count}")

    # --------------------------------------------------------
    # Basic example
    # --------------------------------------------------------

    print("\nExample: 火山")

    rows = cur.execute("""
        SELECT
            id,
            written,
            reading,
            common,
            reading_priority,
            reading_order
        FROM word_form
        WHERE written = ?
        ORDER BY
            reading_priority DESC,
            reading_order ASC
    """, ("火山",)).fetchall()

    for row in rows:

        (
            word_form_id,
            written,
            reading,
            common,
            reading_priority,
            reading_order
        ) = row

        print()

        print(
            f"{written} [{reading}] "
            f"common={common}, "
            f"priority={reading_priority}, "
            f"order={reading_order}"
        )

        meanings = cur.execute("""
            SELECT
                language,
                meaning
            FROM word_meaning
            WHERE word_form_id = ?
            ORDER BY
                sense_index,
                id
        """, (word_form_id,)).fetchall()

        for language, meaning in meanings:
            print(
                f"  {language}: {meaning}"
            )

finally:
    conn.close()