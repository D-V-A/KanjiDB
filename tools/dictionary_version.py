"""Shared validation for dictionary release metadata (never auto-incremented)."""
from pathlib import Path
import re

TOOLS_DIR = Path(__file__).resolve().parent
VERSION_PATH = TOOLS_DIR / "dictionary_version.txt"
ASSETS_DIR = TOOLS_DIR.parent / "app/src/main/assets"
DB_PATH = ASSETS_DIR / "dictionary.db"
ASSET_VERSION_PATH = ASSETS_DIR / "dictionary_version.txt"


def read_version(path=VERSION_PATH):
    try:
        value = path.read_text(encoding="utf-8").strip()
    except OSError as error:
        raise ValueError(f"Cannot read dictionary version: {path}: {error}") from error
    if not re.fullmatch(r"[0-9]+", value) or not 0 < int(value) <= 2147483647:
        raise ValueError(f"Invalid dictionary version in {path}: expected integer 1..2147483647")
    return int(value)
