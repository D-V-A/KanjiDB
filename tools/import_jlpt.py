import sqlite3
from pathlib import Path


BASE_DIR = Path(__file__).parent
TSV_PATH = BASE_DIR / "data" / "jlpt.tsv"
DB_PATH = (
    BASE_DIR.parent
    / "app"
    / "src"
    / "main"
    / "assets"
    / "dictionary.db"
)


def normalize_level(raw_level: str) -> int:
    """
    Accepts:
      5
      N5
      n5
    and returns:
      5
    """
    text = raw_level.strip().upper()

    if text.startswith("N"):
        text = text[1:]

    level = int(text)

    if level < 1 or level > 5:
        raise ValueError(f"Unsupported JLPT level: {raw_level}")

    return level


def main():
    if not TSV_PATH.exists():
        raise FileNotFoundError(
            f"JLPT source file not found: {TSV_PATH}\n"
            f"Expected path: tools/data/jlpt.tsv"
        )

    if not DB_PATH.exists():
        raise FileNotFoundError(
            f"Database not found: {DB_PATH}"
        )

    conn = sqlite3.connect(DB_PATH)
    cur = conn.cursor()

    cur.execute("PRAGMA foreign_keys = ON")

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

    print(f"Reading JLPT data from: {TSV_PATH}")
    print(f"Using database: {DB_PATH}")

    # Load all known kanji from DB
    kanji_by_character = {
        character: kanji_id
        for kanji_id, character in cur.execute(
            "SELECT id, character FROM kanji"
        )
    }

    print(f"Known kanji loaded from DB: {len(kanji_by_character)}")

    # Recreate JLPT table
    cur.executescript("""
    DROP TABLE IF EXISTS jlpt_kanji;

    CREATE TABLE jlpt_kanji (
        kanji_id INTEGER PRIMARY KEY,
        level INTEGER NOT NULL CHECK(level BETWEEN 1 AND 5),

        FOREIGN KEY (kanji_id)
            REFERENCES kanji(id)
            ON DELETE CASCADE
    );

    CREATE INDEX idx_jlpt_kanji_level
        ON jlpt_kanji(level);
    """)

    inserted_count = 0
    missing_count = 0
    duplicate_count = 0

    missing_characters = []
    inserted_characters = set()

    with open(TSV_PATH, "r", encoding="utf-8-sig") as f:
        for line_number, line in enumerate(f, start=1):
            line = line.strip()

            if not line:
                continue

            parts = line.split("\t")

            if len(parts) != 2:
                raise ValueError(
                    f"Invalid TSV format at line {line_number}: {line!r}"
                )

            raw_level, kanji_string = parts
            level = normalize_level(raw_level)

            # One long string of kanji, e.g.
            # 5    日一国人年大...
            for character in kanji_string.strip():
                if not character.strip():
                    continue

                if character in inserted_characters:
                    duplicate_count += 1
                    continue

                kanji_id = kanji_by_character.get(character)

                if kanji_id is None:
                    missing_count += 1
                    missing_characters.append((level, character))
                    continue

                cur.execute("""
                    INSERT INTO jlpt_kanji (
                        kanji_id,
                        level
                    )
                    VALUES (?, ?)
                """, (
                    kanji_id,
                    level
                ))

                inserted_characters.add(character)
                inserted_count += 1

    conn.commit()

    print()
    print("JLPT import finished.")
    print(f"Inserted JLPT kanji: {inserted_count}")
    print(f"Duplicates skipped:  {duplicate_count}")
    print(f"Missing in DB:        {missing_count}")

    print()
    print("Counts by JLPT level:")
    for level, count in cur.execute("""
        SELECT level, COUNT(*)
        FROM jlpt_kanji
        GROUP BY level
        ORDER BY level
    """):
        print(f"  N{level}: {count}")

    print()
    print("Sample checks:")

    for sample in ["日", "山", "水", "犬", "漢"]:
        row = cur.execute("""
            SELECT k.character, j.level
            FROM jlpt_kanji j
            JOIN kanji k
              ON k.id = j.kanji_id
            WHERE k.character = ?
        """, (sample,)).fetchone()

        if row is None:
            print(f"  {sample}: not in JLPT table")
        else:
            print(f"  {row[0]}: N{row[1]}")

    if missing_characters:
        print()
        print("First missing characters (up to 30):")
        for level, character in missing_characters[:30]:
            print(f"  N{level}: {character}")

    conn.close()


if __name__ == "__main__":
    main()