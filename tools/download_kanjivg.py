"""Select and cache an official KanjiVG main release; never refresh implicitly."""
import argparse
import hashlib
import json
import os
import re
import shutil
import sys
import urllib.error
import urllib.request
import zipfile
import xml.etree.ElementTree as ET
from pathlib import Path

CACHE = Path(__file__).resolve().parent / "data" / "kanjivg"
API = "https://api.github.com/repos/KanjiVG/kanjivg/releases"
CANONICAL = re.compile(r"[0-9a-f]{5,6}\.svg")


def request(url):
    return urllib.request.urlopen(urllib.request.Request(url, headers={
        "User-Agent": "KanjiDB dictionary builder", "Accept": "application/vnd.github+json",
    }), timeout=60)


def archive_files(path):
    with zipfile.ZipFile(path) as archive:
        bad = archive.testzip()
        if bad:
            raise ValueError(f"Corrupt KanjiVG archive member: {bad}")
        files = {}
        for info in archive.infolist():
            name = Path(info.filename).name
            if not name.endswith(".svg"):
                continue
            if not CANONICAL.fullmatch(name):
                raise ValueError(f"Expected main archive, found noncanonical SVG: {name}")
            if name in files or info.file_size == 0:
                raise ValueError(f"Duplicate/empty KanjiVG SVG: {name}")
            files[name] = info.filename
        if "065e5.svg" not in files:
            raise ValueError("KanjiVG main archive does not contain canonical 日 (065e5.svg)")
        root = ET.fromstring(archive.read(files["065e5.svg"]))
        if root.tag != "{http://www.w3.org/2000/svg}svg" or not any(
            node.get("id") == "kvg:StrokePaths_065e5" for node in root.iter()
        ):
            raise ValueError("KanjiVG archive contains no valid canonical 日 SVG")
        return files


def digest(path):
    with path.open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()


def cached_source(cache=CACHE):
    manifest = json.loads((cache / "selected.json").read_text(encoding="utf-8"))
    if not re.fullmatch(r"r\d{8}", manifest["release"]):
        raise ValueError("Invalid cached KanjiVG release tag")
    expected = f"kanjivg-{manifest['release'][1:]}-main.zip"
    if manifest["archive"] != expected:
        raise ValueError("Invalid cached KanjiVG main archive name")
    archive = cache / expected
    if digest(archive) != manifest["sha256"]:
        raise ValueError(f"KanjiVG cache checksum mismatch: {archive}")
    archive_files(archive)
    return archive, manifest


def ensure_source(update=False, force=False, release=None, cache=CACHE):
    selected = None
    if (cache / "selected.json").exists() and not force:
        # A malformed selected cache is an error, not permission to pick arbitrary old sources.
        selected = cached_source(cache)
        if not update and not force and (release is None or release == selected[1]["release"]):
            print(f"KanjiVG cached release: {selected[1]['release']} sha256={selected[1]['sha256']}")
            return selected
    if release is not None and not re.fullmatch(r"r\d{8}", release):
        raise ValueError("KanjiVG release must be a tag such as r20260714")
    try:
        with request(f"{API}/tags/{release}" if release else f"{API}/latest") as response:
            metadata = json.load(response)
        tag = metadata["tag_name"]
        if not re.fullmatch(r"r\d{8}", tag):
            raise ValueError(f"Unsupported KanjiVG release tag: {tag}")
        name = f"kanjivg-{tag[1:]}-main.zip"
        assets = [a for a in metadata["assets"] if a["name"] == name]
        if len(assets) != 1:
            raise ValueError(f"Official release {tag} has no unique main archive")
        asset = assets[0]
        cache.mkdir(parents=True, exist_ok=True)
        destination = cache / name
        temporary = cache / (name + ".part")
        try:
            if force or not destination.exists():
                print(f"Downloading KanjiVG {tag}: {asset['browser_download_url']}", flush=True)
                with request(asset["browser_download_url"]) as response, temporary.open("wb") as output:
                    shutil.copyfileobj(response, output)
                archive_files(temporary)
                actual = digest(temporary)
                published = asset.get("digest")
                if published and published != f"sha256:{actual}":
                    raise ValueError("KanjiVG release asset checksum mismatch")
                os.replace(temporary, destination)
            archive_files(destination)
            sha = digest(destination)
            if asset.get("digest") and asset["digest"] != f"sha256:{sha}":
                raise ValueError("Cached archive differs from official release asset")
            manifest = {"release": tag, "archive": name, "url": asset["browser_download_url"], "sha256": sha}
            tmp_manifest = cache / "selected.json.part"
            try:
                tmp_manifest.write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")
                os.replace(tmp_manifest, cache / "selected.json")
            finally:
                tmp_manifest.unlink(missing_ok=True)
        finally:
            temporary.unlink(missing_ok=True)
        print(f"KanjiVG selected release: {tag} sha256={sha}", flush=True)
        return destination, manifest
    except (urllib.error.URLError, TimeoutError) as error:
        if selected and not force and (release is None or release == selected[1]["release"]):
            print(f"KanjiVG network unavailable ({error}); explicitly reusing verified cached release {selected[1]['release']}")
            return selected
        raise RuntimeError("Cannot download official KanjiVG main release and no usable selected cache is available. "
                           "Run python tools/download_kanjivg.py with network access.") from error


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--update", action="store_true", help="Select latest official release")
    parser.add_argument("--force", action="store_true", help="Redownload the selected release")
    parser.add_argument("--release", help="Select an exact release tag for reproducibility")
    args = parser.parse_args()
    try:
        release = args.release
        if args.force and not args.update and release is None and (CACHE / "selected.json").exists():
            release = json.loads((CACHE / "selected.json").read_text(encoding="utf-8"))["release"]
        ensure_source(args.update, args.force, release)
        return 0
    except (OSError, ValueError, KeyError, RuntimeError, ET.ParseError, zipfile.BadZipFile) as error:
        print(f"KANJIVG SOURCE FAILED: {error}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
