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

JLPT_PATH = BASE_DIR / "data" / "jlpt.tsv"


def fail(message):
    print(f"[FAIL] {message}")
    return False


def ok(message):
    print(f"[ OK ] {message}")


def finish(success):
    print()
    print("=" * 60)

    if success:
        print("DATABASE CHECK: OK")
        return

    print("DATABASE CHECK: FAILED")
    raise SystemExit(1)


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
        "jlpt_kanji",
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

        # The rest of the verification depends on the schema.
        # Do not trigger misleading OperationalError exceptions.
        finish(success)

    # --------------------------------------------------------
    # Counts
    # --------------------------------------------------------

    print()
    print("Counts:")

    counts = {}

    for table in [
        "kanji",
        "kanji_meaning",
        "kanji_reading",
        "word_form",
        "word_meaning",
        "word_kanji",
        "jlpt_kanji",
    ]:
        count = cur.execute(
            f"SELECT COUNT(*) FROM {table}"
        ).fetchone()[0]

        counts[table] = count

        print(
            f"  {table:<16} {count:>10}"
        )

    # Basic sanity checks.
    if counts["kanji"] > 0:
        ok("Kanji table is not empty")
    else:
        success = fail(
            "Kanji table is empty"
        ) and success

    if counts["word_form"] > 0:
        ok("Word table is not empty")
    else:
        success = fail(
            "Word table is empty"
        ) and success

    if counts["jlpt_kanji"] > 0:
        ok("JLPT table is not empty")
    else:
        success = fail(
            "JLPT table is empty"
        ) and success

    # --------------------------------------------------------
    # JLPT data
    # --------------------------------------------------------

    print()
    print("JLPT levels:")

    jlpt_level_counts = dict(
        cur.execute("""
            SELECT level, COUNT(*)
            FROM jlpt_kanji
            GROUP BY level
            ORDER BY level
        """).fetchall()
    )

    for level in range(1, 6):
        print(
            f"  N{level}: "
            f"{jlpt_level_counts.get(level, 0)}"
        )

    invalid_jlpt_levels = cur.execute("""
        SELECT COUNT(*)
        FROM jlpt_kanji
        WHERE level NOT BETWEEN 1 AND 5
    """).fetchone()[0]

    if invalid_jlpt_levels == 0:
        ok("JLPT levels are within N1-N5")
    else:
        success = fail(
            f"Invalid JLPT levels: "
            f"{invalid_jlpt_levels}"
        ) and success

    missing_jlpt_levels = [
        level
        for level in range(1, 6)
        if jlpt_level_counts.get(level, 0) == 0
    ]

    if not missing_jlpt_levels:
        ok("All JLPT levels N1-N5 are present")
    else:
        success = fail(
            "Missing JLPT levels: "
            f"{missing_jlpt_levels}"
        ) and success

    # Compare the imported table with the downloaded source.
    # This avoids hard-coding counts that could change if the
    # upstream community classification is updated.
    if not JLPT_PATH.exists():
        success = fail(
            f"JLPT source file not found: {JLPT_PATH}"
        ) and success
    else:
        source_jlpt = {}
        source_duplicates = []
        source_format_errors = []

        with open(
            JLPT_PATH,
            "r",
            encoding="utf-8-sig"
        ) as source_file:
            for line_number, line in enumerate(
                source_file,
                start=1
            ):
                line = line.strip()

                if not line:
                    continue

                parts = line.split("\t")

                if len(parts) != 2:
                    source_format_errors.append(
                        (line_number, line)
                    )
                    continue

                raw_level, characters = parts

                try:
                    level = int(
                        raw_level.strip()
                        .upper()
                        .removeprefix("N")
                    )
                except ValueError:
                    source_format_errors.append(
                        (line_number, line)
                    )
                    continue

                if level not in range(1, 6):
                    source_format_errors.append(
                        (line_number, line)
                    )
                    continue

                for character in characters.strip():
                    previous_level = source_jlpt.get(
                        character
                    )

                    if previous_level is not None:
                        source_duplicates.append(
                            (
                                character,
                                previous_level,
                                level,
                            )
                        )
                        continue

                    source_jlpt[character] = level

        if source_format_errors:
            success = fail(
                "Invalid jlpt.tsv rows: "
                f"{source_format_errors[:5]}"
            ) and success
        else:
            ok("JLPT source format")

        if source_duplicates:
            success = fail(
                "Duplicate kanji in jlpt.tsv: "
                f"{source_duplicates[:10]}"
            ) and success
        else:
            ok("No duplicate kanji in JLPT source")

        db_jlpt = dict(
            cur.execute("""
                SELECT k.character, j.level
                FROM jlpt_kanji j
                JOIN kanji k
                    ON k.id = j.kanji_id
            """).fetchall()
        )

        missing_from_db = sorted(
            set(source_jlpt) - set(db_jlpt)
        )

        extra_in_db = sorted(
            set(db_jlpt) - set(source_jlpt)
        )

        wrong_levels = sorted(
            (
                character,
                source_jlpt[character],
                db_jlpt[character],
            )
            for character in (
                set(source_jlpt) & set(db_jlpt)
            )
            if source_jlpt[character]
            != db_jlpt[character]
        )

        if (
            not missing_from_db
            and not extra_in_db
            and not wrong_levels
        ):
            ok(
                "JLPT table exactly matches "
                "jlpt.tsv"
            )
        else:
            if missing_from_db:
                success = fail(
                    "JLPT kanji missing from DB: "
                    f"{missing_from_db[:20]}"
                ) and success

            if extra_in_db:
                success = fail(
                    "Extra JLPT kanji in DB: "
                    f"{extra_in_db[:20]}"
                ) and success

            if wrong_levels:
                success = fail(
                    "JLPT level mismatches "
                    "(character, source, DB): "
                    f"{wrong_levels[:20]}"
                ) and success

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

    invalid_old_codes = {
        "eng",
        "fre",
        "fra",
        "ger",
        "deu",
        "spa",
        "por",
        "rus",
        "jpn",
    }

    invalid_kanji_codes = (
        kanji_languages & invalid_old_codes
    )

    invalid_word_codes = (
        word_languages & invalid_old_codes
    )

    if invalid_kanji_codes:
        success = fail(
            "kanji_meaning contains non-normalized "
            f"language codes: "
            f"{sorted(invalid_kanji_codes)}"
        ) and success
    else:
        ok("KANJIDIC language codes normalized")

    if invalid_word_codes:
        success = fail(
            "word_meaning contains non-normalized "
            f"language codes: "
            f"{sorted(invalid_word_codes)}"
        ) and success
    else:
        ok("JMdict language codes normalized")

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
    # Verify 火山 meanings
    # --------------------------------------------------------

    kazan_meanings = cur.execute("""
        SELECT
            wm.language,
            wm.meaning
        FROM word_form wf
        JOIN word_meaning wm
            ON wm.word_form_id = wf.id
        WHERE wf.written = '火山'
          AND wf.reading = 'かざん'
        ORDER BY
            wm.sense_index,
            wm.id
    """).fetchall()

    if any(
        language == "en"
        and meaning.lower() == "volcano"
        for language, meaning in kazan_meanings
    ):
        ok("火山 English meaning")
    else:
        success = fail(
            f"Unexpected 火山 meanings: "
            f"{kazan_meanings}"
        ) and success

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
                  wf2.reading_order ASC,
                  wf2.id ASC
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

    duplicate_kanji_meanings = cur.execute("""
        SELECT COUNT(*)
        FROM (
            SELECT
                kanji_id,
                language,
                meaning,
                COUNT(*) AS c
            FROM kanji_meaning
            GROUP BY
                kanji_id,
                language,
                meaning
            HAVING c > 1
        )
    """).fetchone()[0]

    if duplicate_kanji_meanings == 0:
        ok("No duplicate kanji meanings")
    else:
        success = fail(
            "Duplicate kanji meanings: "
            f"{duplicate_kanji_meanings}"
        ) and success

    duplicate_kanji_readings = cur.execute("""
        SELECT COUNT(*)
        FROM (
            SELECT
                kanji_id,
                type,
                reading,
                COUNT(*) AS c
            FROM kanji_reading
            GROUP BY
                kanji_id,
                type,
                reading
            HAVING c > 1
        )
    """).fetchone()[0]

    if duplicate_kanji_readings == 0:
        ok("No duplicate kanji readings")
    else:
        success = fail(
            "Duplicate kanji readings: "
            f"{duplicate_kanji_readings}"
        ) and success

    # --------------------------------------------------------
    # Orphan checks
    # --------------------------------------------------------

    orphan_kanji_meanings = cur.execute("""
        SELECT COUNT(*)
        FROM kanji_meaning km
        LEFT JOIN kanji k
            ON k.id = km.kanji_id
        WHERE k.id IS NULL
    """).fetchone()[0]

    orphan_kanji_readings = cur.execute("""
        SELECT COUNT(*)
        FROM kanji_reading kr
        LEFT JOIN kanji k
            ON k.id = kr.kanji_id
        WHERE k.id IS NULL
    """).fetchone()[0]

    orphan_word_meanings = cur.execute("""
        SELECT COUNT(*)
        FROM word_meaning wm
        LEFT JOIN word_form wf
            ON wf.id = wm.word_form_id
        WHERE wf.id IS NULL
    """).fetchone()[0]

    orphan_word_links = cur.execute("""
        SELECT COUNT(*)
        FROM word_kanji wk
        LEFT JOIN word_form wf
            ON wf.id = wk.word_form_id
        LEFT JOIN kanji k
            ON k.id = wk.kanji_id
        WHERE wf.id IS NULL
           OR k.id IS NULL
    """).fetchone()[0]

    orphan_jlpt_links = cur.execute("""
        SELECT COUNT(*)
        FROM jlpt_kanji j
        LEFT JOIN kanji k
            ON k.id = j.kanji_id
        WHERE k.id IS NULL
    """).fetchone()[0]

    if orphan_kanji_meanings == 0:
        ok("No orphan kanji meanings")
    else:
        success = fail(
            "Orphan kanji meanings: "
            f"{orphan_kanji_meanings}"
        ) and success

    if orphan_kanji_readings == 0:
        ok("No orphan kanji readings")
    else:
        success = fail(
            "Orphan kanji readings: "
            f"{orphan_kanji_readings}"
        ) and success

    if orphan_word_meanings == 0:
        ok("No orphan word meanings")
    else:
        success = fail(
            "Orphan word meanings: "
            f"{orphan_word_meanings}"
        ) and success

    if orphan_word_links == 0:
        ok("No orphan word-kanji links")
    else:
        success = fail(
            "Orphan word-kanji links: "
            f"{orphan_word_links}"
        ) and success

    if orphan_jlpt_links == 0:
        ok("No orphan JLPT-kanji links")
    else:
        success = fail(
            "Orphan JLPT-kanji links: "
            f"{orphan_jlpt_links}"
        ) and success

    # --------------------------------------------------------
    # Final result
    # --------------------------------------------------------

    finish(success)

finally:
    conn.close()