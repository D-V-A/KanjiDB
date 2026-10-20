import io
import json
import sqlite3
import sys
import tempfile
import shutil
import uuid
import unittest
import urllib.error
import zipfile
from pathlib import Path
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from download_kanjivg import archive_files, cached_source, digest, ensure_source
from import_kanjivg import enrich, parse_strokes, validate_path


def svg(paths=None, character="日", code="065e5", viewbox="0 0 109 109", transform=""):
    paths = paths if paths is not None else ["M10,10c0,5 0,10 0,20", "M10 10L30 10"]
    elements = "".join(f'<path id="kvg:{code}-s{i}" d="{p}"/>' for i, p in enumerate(paths, 1))
    return (f'<svg xmlns="http://www.w3.org/2000/svg" xmlns:kvg="https://kanjivg.tagaini.net/" viewBox="{viewbox}">'
            f'<g id="kvg:StrokePaths_{code}" {transform}><g id="kvg:{code}" kvg:element="{character}">{elements}</g></g>'
            f'<g id="kvg:StrokeNumbers_{code}"><path d="M0 0L1 1"/></g></svg>').encode()


class KanjiVGTest(unittest.TestCase):
    def setUp(self):
        self.cache = Path(tempfile.gettempdir()) / ("kanjivg-test-" + uuid.uuid4().hex)
        self.cache.mkdir()
        self.addCleanup(shutil.rmtree, self.cache)
        self.archive = self.cache / "kanjivg-20260714-main.zip"

    def write_archive(self, content=None, extra=None):
        with zipfile.ZipFile(self.archive, "w") as z:
            z.writestr("kanji/065e5.svg", content or svg())
            if extra:
                z.writestr(*extra)
        manifest = {"release": "r20260714", "archive": self.archive.name, "url": "https://example.invalid/main.zip",
                    "sha256": digest(self.archive)}
        (self.cache / "selected.json").write_text(json.dumps(manifest), encoding="utf-8")
        return manifest

    def database(self):
        db = sqlite3.connect(":memory:")
        db.execute("PRAGMA foreign_keys=ON")
        db.execute("CREATE TABLE kanji(id INTEGER PRIMARY KEY,character TEXT UNIQUE)")
        db.executemany("INSERT INTO kanji VALUES (?,?)", [(1,"日"),(2,"丂")])
        db.commit()
        self.addCleanup(db.close)
        return db

    def test_nested_paths_preserve_geometry_order_direction_and_exclude_numbers(self):
        paths = ["M10,10c0,5 0,10 0,20", "M10 10L30 10"]
        self.assertEqual(paths, parse_strokes(svg(paths), "日"))

    def test_source_identity_and_geometry_fail_clearly(self):
        for content in [svg(code="065e6"), svg(viewbox="0 0 110 110"), svg(transform='transform="translate(1,0)"'),
                        svg().replace(b"-s2", b"-s3"), svg(paths=[])]:
            with self.subTest(content=content), self.assertRaises(ValueError):
                parse_strokes(content,"日")

    def test_component_label_is_not_codepoint_identity(self):
        self.assertEqual(2,len(parse_strokes(svg(character="別"),"日")))

    def test_path_validation_rejects_bad_argument_count_nonfinite_and_multiple_contours(self):
        for path in ["", "L0 0", "M0 0C1 2", "M0 0L1e999 2", "M0 0M1 1L2 2", "M0 0A1 1 0 2 0 3 3"]:
            with self.subTest(path=path), self.assertRaises(ValueError):
                validate_path(path)
        self.assertEqual("M0 0c1 2 3 4 5 6",validate_path("M0 0c1 2 3 4 5 6"))

    def test_enrichment_only_existing_kanji_and_idempotent(self):
        self.write_archive(extra=("kanji/065e6.svg",svg(character="旦",code="065e6")))
        db=self.database()
        self.assertEqual((1,2),enrich(db,self.archive))
        self.assertEqual((1,2),enrich(db,self.archive))
        self.assertEqual([(1,1),(1,2)],list(db.execute("SELECT kanji_id,stroke_index FROM kanji_stroke")))
        self.assertEqual([],db.execute("PRAGMA foreign_key_check").fetchall())
        with self.assertRaises(sqlite3.IntegrityError):
            db.execute("INSERT INTO kanji_stroke VALUES (99,1,'M0 0L1 1')")

    def test_bad_source_preserves_old_strokes(self):
        self.write_archive();db=self.database();enrich(db,self.archive)
        before=list(db.execute("SELECT * FROM kanji_stroke"))
        self.write_archive(content=svg(viewbox="0 0 200 200"))
        with self.assertRaises(ValueError):enrich(db,self.archive)
        self.assertEqual(before,list(db.execute("SELECT * FROM kanji_stroke")))

    def test_valid_selected_cache_never_needs_network(self):
        self.write_archive()
        with patch("download_kanjivg.request",side_effect=AssertionError("unexpected network")):
            self.assertEqual(self.archive,ensure_source(cache=self.cache)[0])

    def test_network_failure_explicitly_reuses_only_valid_selected_cache(self):
        self.write_archive()
        with patch("download_kanjivg.request",side_effect=urllib.error.URLError("offline")):
            self.assertEqual(self.archive,ensure_source(update=True,cache=self.cache)[0])
            with self.assertRaises(RuntimeError):ensure_source(release="r20250816",cache=self.cache)

    def test_network_failure_without_cache_fails(self):
        with patch("download_kanjivg.request",side_effect=urllib.error.URLError("offline")),self.assertRaises(RuntimeError):
            ensure_source(cache=self.cache)

    def test_invalid_cache_and_variant_archive_are_not_silently_accepted(self):
        self.write_archive();self.archive.write_bytes(b"corrupted")
        with self.assertRaises(ValueError):cached_source(self.cache)
        self.write_archive(extra=("kanji/065e5-Kaisho.svg",svg()))
        with self.assertRaises(ValueError):archive_files(self.archive)

    def test_force_repairs_cache_atomically_and_checks_published_digest(self):
        manifest=self.write_archive();payload=self.archive.read_bytes();self.archive.write_bytes(b"bad")
        metadata={"tag_name":"r20260714","assets":[{"name":self.archive.name,"browser_download_url":manifest["url"],
            "digest":"sha256:"+manifest["sha256"]}]}
        with patch("download_kanjivg.request",side_effect=[io.BytesIO(json.dumps(metadata).encode()),io.BytesIO(payload)]):
            self.assertEqual(manifest["sha256"],ensure_source(force=True,release="r20260714",cache=self.cache)[1]["sha256"])
        self.assertFalse(list(self.cache.glob("*.part")))

    def test_failed_download_keeps_selected_release_and_cleans_partials(self):
        self.write_archive();original=(self.cache/"selected.json").read_bytes()
        metadata={"tag_name":"r20250816","assets":[{"name":"kanjivg-20250816-main.zip","browser_download_url":"x"}]}
        with patch("download_kanjivg.request",side_effect=[io.BytesIO(json.dumps(metadata).encode()),io.BytesIO(b"not zip")]),self.assertRaises(zipfile.BadZipFile):
            ensure_source(update=True,cache=self.cache)
        self.assertEqual(original,(self.cache/"selected.json").read_bytes())
        self.assertFalse(list(self.cache.glob("*.part")))


if __name__ == "__main__":
    unittest.main()
