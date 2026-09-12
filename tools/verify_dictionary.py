import sqlite3
from pathlib import Path


BASE_DIR = Path(__file__).parent
DB_PATH = (
    BASE_DIR.parent
    / "app"
    / "src"
    / "main"
    / "assets"
    / "dictionary.db"
)

def fail(message):
    print(f"[FAIL] {message}")
    return False


def ok(message):
    print(f"[ OK ] {message}")

DB_PATH.parent.mkdir(
    parents=True,
    exist_ok=True
)

if not DB_PATH.exists():
    raise FileNotFoundError(
        f"Database not found: {DB_PATH}"
    )


conn = sqlite3.connect(DB_PATH)

try:
    cur = conn.cursor()

    cur.execute("PRAGMA foreign_keys = ON")

    success = True

    print(f"Checking: {DB_PATH}")
    print()

    # --------------------------------------------------------
    # SQLite integrity
    # --------------------------------------------------------

    integrity = cur.execute(
        "PRAGMA integrity_check"
    ).fetchone()[0]

    if integrity == "ok":
        ok("SQLite integrity_check")
    else:
        success = fail(
            f"integrity_check: {integrity}"
        ) and success

    foreign_key_errors = cur.execute(
        "PRAGMA foreign_key_check"
    ).fetchall()

    if not foreign_key_errors:
        ok("Foreign keys")
    else:
        success = fail(
            f"Foreign key errors: "
            f"{len(foreign_key_errors)}"
        ) and success

    # --------------------------------------------------------
    # Required tables
    # --------------------------------------------------------

    required_tables = {
        "kanji",
        "kanji_meaning",
        "kanji_reading",
        "word_form",
        "word_meaning",
        "word_kanji",
    }

    actual_tables = {
        row[0]
        for row in cur.execute("""
            SELECT name
            FROM sqlite_master
            WHERE type = 'table'
        """)
    }

    missing_tables = (
        required_tables - actual_tables
    )

    if not missing_tables:
        ok("Required tables")
    else:
        success = fail(
            f"Missing tables: "
            f"{sorted(missing_tables)}"
        ) and success

    # --------------------------------------------------------
    # Counts
    # --------------------------------------------------------

    print()
    print("Counts:")

    for table in [
        "kanji",
        "kanji_meaning",
        "kanji_reading",
        "word_form",
        "word_meaning",
        "word_kanji",
    ]:
        count = cur.execute(
            f"SELECT COUNT(*) FROM {table}"
        ).fetchone()[0]

        print(
            f"  {table:<16} {count:>10}"
        )

    # --------------------------------------------------------
    # Language-code consistency
    # --------------------------------------------------------

    kanji_languages = {
        row[0]
        for row in cur.execute("""
            SELECT DISTINCT language
            FROM kanji_meaning
        """)
    }

    word_languages = {
        row[0]
        for row in cur.execute("""
            SELECT DISTINCT language
            FROM word_meaning
        """)
    }

    print()
    print(
        "Kanji languages:",
        sorted(kanji_languages)
    )

    print(
        "Word languages:",
        sorted(word_languages)
    )

    if "eng" in kanji_languages:
        success = fail(
            "kanji_meaning still contains 'eng'"
        ) and success

    if "eng" in word_languages:
        success = fail(
            "word_meaning still contains 'eng'"
        ) and success

    if "en" in kanji_languages:
        ok("KANJIDIC English code = en")
    else:
        success = fail(
            "KANJIDIC has no 'en' meanings"
        ) and success

    if "en" in word_languages:
        ok("JMdict English code = en")
    else:
        success = fail(
            "JMdict has no 'en' meanings"
        ) and success

    # --------------------------------------------------------
    # Known kanji
    # --------------------------------------------------------

    print()
    print("Kanji samples:")

    for character in [
        "山",
        "水",
        "火",
        "日",
        "生",
    ]:

        row = cur.execute("""
            SELECT
                id,
                stroke_count,
                grade,
                frequency,
                joyo
            FROM kanji
            WHERE character = ?
        """, (character,)).fetchone()

        if row is None:
            success = fail(
                f"Missing kanji: {character}"
            ) and success
            continue

        ok(
            f"{character}: "
            f"strokes={row[1]}, "
            f"grade={row[2]}, "
            f"frequency={row[3]}, "
            f"joyo={row[4]}"
        )

    # --------------------------------------------------------
    # 火山 test
    # --------------------------------------------------------

    print()
    print("Word sample: 火山")

    kazan = cur.execute("""
        SELECT
            id,
            entry_id,
            written,
            reading,
            common,
            reading_priority,
            reading_order
        FROM word_form
        WHERE written = '火山'
        ORDER BY
            reading_priority DESC,
            reading_order ASC
    """).fetchall()

    if not kazan:
        success = fail(
            "火山 not found"
        ) and success
    else:
        for row in kazan:
            print(
                " ",
                row
            )

        ok("火山 found")

    # --------------------------------------------------------
    # Verify 火山 kanji links
    # --------------------------------------------------------

    kazan_links = cur.execute("""
        SELECT
            k.character,
            wk.position
        FROM word_form wf
        JOIN word_kanji wk
            ON wk.word_form_id = wf.id
        JOIN kanji k
            ON k.id = wk.kanji_id
        WHERE wf.written = '火山'
          AND wf.reading = 'かざん'
        ORDER BY wk.position
    """).fetchall()

    expected_kazan_links = [
        ("火", 0),
        ("山", 1),
    ]

    if kazan_links == expected_kazan_links:
        ok("火山 kanji links")
    else:
        success = fail(
            f"Unexpected 火山 links: "
            f"{kazan_links}"
        ) and success

    # --------------------------------------------------------
    # Check preferred-reading logic
    # --------------------------------------------------------

    preferred_rows = cur.execute("""
        SELECT
            wf.entry_id,
            wf.written,
            wf.reading,
            wf.reading_priority,
            wf.reading_order
        FROM word_form wf
        WHERE wf.entry_id = 2859595
          AND wf.id = (
              SELECT wf2.id
              FROM word_form wf2
              WHERE wf2.entry_id = wf.entry_id
                AND wf2.written = wf.written
              ORDER BY
                  wf2.reading_priority DESC,
                  wf2.reading_order ASC
              LIMIT 1
          )
        ORDER BY wf.written
    """).fetchall()

    print()
    print(
        "Complex entry 2859595 "
        "preferred readings:"
    )

    if preferred_rows:

        for row in preferred_rows:
            print(
                f"  {row[1]} -> {row[2]} "
                f"(priority={row[3]}, "
                f"order={row[4]})"
            )

        ok(
            "Preferred-reading query"
        )

    else:
        print(
            "  Entry not present in this JMdict "
            "version; skipped."
        )

    # --------------------------------------------------------
    # Duplicate checks
    # --------------------------------------------------------

    duplicate_forms = cur.execute("""
        SELECT COUNT(*)
        FROM (
            SELECT
                entry_id,
                written,
                reading,
                COUNT(*) AS c
            FROM word_form
            GROUP BY
                entry_id,
                written,
                reading
            HAVING c > 1
        )
    """).fetchone()[0]

    if duplicate_forms == 0:
        ok("No duplicate word forms")
    else:
        success = fail(
            f"Duplicate word forms: "
            f"{duplicate_forms}"
        ) and success

    # --------------------------------------------------------
    # Orphan checks
    # --------------------------------------------------------

    orphan_meanings = cur.execute("""
        SELECT COUNT(*)
        FROM word_meaning wm
        LEFT JOIN word_form wf
            ON wf.id = wm.word_form_id
        WHERE wf.id IS NULL
    """).fetchone()[0]

    orphan_links = cur.execute("""
        SELECT COUNT(*)
        FROM word_kanji wk
        LEFT JOIN word_form wf
            ON wf.id = wk.word_form_id
        LEFT JOIN kanji k
            ON k.id = wk.kanji_id
        WHERE wf.id IS NULL
           OR k.id IS NULL
    """).fetchone()[0]

    if orphan_meanings == 0:
        ok("No orphan word meanings")
    else:
        success = fail(
            f"Orphan meanings: "
            f"{orphan_meanings}"
        ) and success

    if orphan_links == 0:
        ok("No orphan word-kanji links")
    else:
        success = fail(
            f"Orphan word-kanji links: "
            f"{orphan_links}"
        ) and success

    # --------------------------------------------------------
    # Final result
    # --------------------------------------------------------

    print()
    print("=" * 60)

    if success:
        print("DATABASE CHECK: OK")
    else:
        print("DATABASE CHECK: FAILED")
        raise SystemExit(1)

finally:
    conn.close()