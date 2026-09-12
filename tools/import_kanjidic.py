import sqlite3
import xml.etree.ElementTree as ET
from pathlib import Path


BASE_DIR = Path(__file__).parent
XML_PATH = BASE_DIR / "data" / "kanjidic2.xml"
DB_PATH = (
    BASE_DIR.parent
    / "app"
    / "src"
    / "main"
    / "assets"
    / "dictionary.db"
)

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


if not XML_PATH.exists():
    raise FileNotFoundError(
        f"KANJIDIC2 file not found: {XML_PATH}"
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
    # Full rebuild:
    #
    # KANJIDIC is the first stage of our dictionary pipeline,
    # so rebuilding it also removes JMdict-derived tables.
    # --------------------------------------------------------

    cur.executescript("""
    DROP TABLE IF EXISTS word_kanji;
    DROP TABLE IF EXISTS word_meaning;
    DROP TABLE IF EXISTS word_form;

    -- Legacy test tables, if they still exist.
    DROP TABLE IF EXISTS word_reading;
    DROP TABLE IF EXISTS word;

    DROP TABLE IF EXISTS kanji_reading;
    DROP TABLE IF EXISTS kanji_meaning;
    DROP TABLE IF EXISTS kanji;

    CREATE TABLE kanji (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        character TEXT NOT NULL UNIQUE,
        stroke_count INTEGER,
        grade INTEGER,
        frequency INTEGER,
        joyo INTEGER NOT NULL DEFAULT 0
            CHECK (joyo IN (0, 1))
    );

    CREATE TABLE kanji_meaning (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        kanji_id INTEGER NOT NULL,
        language TEXT NOT NULL,
        meaning TEXT NOT NULL,

        FOREIGN KEY (kanji_id)
            REFERENCES kanji(id)
            ON DELETE CASCADE,

        UNIQUE (kanji_id, language, meaning)
    );

    CREATE TABLE kanji_reading (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        kanji_id INTEGER NOT NULL,
        type TEXT NOT NULL
            CHECK (type IN ('on', 'kun')),
        reading TEXT NOT NULL,

        FOREIGN KEY (kanji_id)
            REFERENCES kanji(id)
            ON DELETE CASCADE,

        UNIQUE (kanji_id, type, reading)
    );

    CREATE INDEX idx_kanji_meaning_kanji
        ON kanji_meaning(kanji_id);

    CREATE INDEX idx_kanji_meaning_language
        ON kanji_meaning(language);

    CREATE INDEX idx_kanji_reading_kanji
        ON kanji_reading(kanji_id);

    CREATE INDEX idx_kanji_reading_type
        ON kanji_reading(type);

    CREATE INDEX idx_kanji_frequency
        ON kanji(frequency);
    """)

    tree = ET.parse(XML_PATH)
    root = tree.getroot()

    kanji_count = 0
    meaning_count = 0
    reading_count = 0

    # --------------------------------------------------------
    # Import characters
    # --------------------------------------------------------

    for character in root.findall("character"):

        literal = character.findtext("literal")

        if not literal:
            continue

        misc = character.find("misc")

        stroke_count = None
        grade = None
        frequency = None
        joyo = 0

        if misc is not None:

            # KANJIDIC2 can technically contain more than one
            # stroke_count. For MVP we use the first one.
            stroke_count_text = misc.findtext("stroke_count")
            grade_text = misc.findtext("grade")
            frequency_text = misc.findtext("freq")

            if stroke_count_text:
                stroke_count = int(stroke_count_text)

            if grade_text:
                grade = int(grade_text)

            if frequency_text:
                frequency = int(frequency_text)

            # KANJIDIC2:
            # grades 1-6 = school-grade Jōyō kanji
            # grade 8    = remaining Jōyō kanji
            if grade is not None:
                joyo = 1 if (1 <= grade <= 6 or grade == 8) else 0

        cur.execute("""
            INSERT INTO kanji (
                character,
                stroke_count,
                grade,
                frequency,
                joyo
            )
            VALUES (?, ?, ?, ?, ?)
        """, (
            literal,
            stroke_count,
            grade,
            frequency,
            joyo
        ))

        kanji_id = cur.lastrowid
        kanji_count += 1

        reading_meaning = character.find("reading_meaning")

        if reading_meaning is None:
            continue

        rmgroup = reading_meaning.find("rmgroup")

        if rmgroup is None:
            continue

        # ----------------------------------------------------
        # Japanese readings only
        # ----------------------------------------------------

        for reading in rmgroup.findall("reading"):

            reading_type = reading.get("r_type")

            if reading_type == "ja_on":
                normalized_type = "on"

            elif reading_type == "ja_kun":
                normalized_type = "kun"

            else:
                continue

            text = reading.text

            if not text:
                continue

            cur.execute("""
                INSERT OR IGNORE INTO kanji_reading (
                    kanji_id,
                    type,
                    reading
                )
                VALUES (?, ?, ?)
            """, (
                kanji_id,
                normalized_type,
                text
            ))

            if cur.rowcount > 0:
                reading_count += 1

        # ----------------------------------------------------
        # Meanings
        #
        # Missing m_lang means English in KANJIDIC2.
        # ----------------------------------------------------

        for meaning in rmgroup.findall("meaning"):

            text = meaning.text

            if not text:
                continue

            language = normalize_language(
                meaning.get("m_lang", "en")
            )

            cur.execute("""
                INSERT OR IGNORE INTO kanji_meaning (
                    kanji_id,
                    language,
                    meaning
                )
                VALUES (?, ?, ?)
            """, (
                kanji_id,
                language,
                text
            ))

            if cur.rowcount > 0:
                meaning_count += 1

    conn.commit()

    # --------------------------------------------------------
    # Summary
    # --------------------------------------------------------

    print(f"Created database: {DB_PATH}")
    print(f"Kanji imported: {kanji_count}")
    print(f"Meanings imported: {meaning_count}")
    print(f"Readings imported: {reading_count}")

    print("\nExample: 山")

    row = cur.execute("""
        SELECT
            id,
            character,
            stroke_count,
            grade,
            frequency,
            joyo
        FROM kanji
        WHERE character = ?
    """, ("山",)).fetchone()

    print(row)

    if row:
        kanji_id = row[0]

        print("\nMeanings:")

        for meaning in cur.execute("""
            SELECT language, meaning
            FROM kanji_meaning
            WHERE kanji_id = ?
            ORDER BY language, id
        """, (kanji_id,)):
            print(meaning)

        print("\nReadings:")

        for reading in cur.execute("""
            SELECT type, reading
            FROM kanji_reading
            WHERE kanji_id = ?
            ORDER BY type, id
        """, (kanji_id,)):
            print(reading)

finally:
    conn.close()