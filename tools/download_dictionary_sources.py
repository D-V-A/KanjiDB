import gzip
import shutil
import urllib.request
from pathlib import Path


BASE_DIR = Path(__file__).parent
DATA_DIR = BASE_DIR / "data"

SOURCES = [
    {
        "name": "KANJIDIC2",
        "url": "https://www.edrdg.org/pub/Nihongo/kanjidic2.xml.gz",
        "archive": DATA_DIR / "kanjidic2.xml.gz",
        "output": DATA_DIR / "kanjidic2.xml",
    },
    {
        "name": "JMdict",
        "url": "https://www.edrdg.org/pub/Nihongo/JMdict_e.gz",
        "archive": DATA_DIR / "JMdict_e.gz",
        "output": DATA_DIR / "jmdict.xml",
    },
]


def download_file(url, destination):
    print(f"Downloading:")
    print(f"  {url}")
    print(f"  -> {destination}")

    request = urllib.request.Request(
        url,
        headers={
            "User-Agent": "KanjiDB dictionary builder"
        }
    )

    with urllib.request.urlopen(request) as response:
        total_size = response.headers.get("Content-Length")

        if total_size is not None:
            total_size = int(total_size)

        downloaded = 0
        chunk_size = 1024 * 1024

        with open(destination, "wb") as output:
            while True:
                chunk = response.read(chunk_size)

                if not chunk:
                    break

                output.write(chunk)
                downloaded += len(chunk)

                if total_size:
                    percent = downloaded * 100 / total_size

                    print(
                        f"\r  {downloaded / 1024 / 1024:.1f} MB "
                        f"/ {total_size / 1024 / 1024:.1f} MB "
                        f"({percent:.1f}%)",
                        end="",
                        flush=True
                    )
                else:
                    print(
                        f"\r  {downloaded / 1024 / 1024:.1f} MB",
                        end="",
                        flush=True
                    )

    print()


def decompress_gzip(source, destination):
    print(f"Decompressing:")
    print(f"  {source}")
    print(f"  -> {destination}")

    with gzip.open(source, "rb") as compressed:
        with open(destination, "wb") as output:
            shutil.copyfileobj(
                compressed,
                output,
                length=1024 * 1024
            )


def process_source(source):
    name = source["name"]
    url = source["url"]
    archive = source["archive"]
    output = source["output"]

    print()
    print("=" * 60)
    print(name)
    print("=" * 60)

    # If the final XML already exists, leave it alone.
    if output.exists():
        print(f"Already exists:")
        print(f"  {output}")
        print("Skipping download.")
        return

    # Download compressed source if needed.
    if not archive.exists():
        download_file(
            url,
            archive
        )
    else:
        print(f"Archive already exists:")
        print(f"  {archive}")
        print("Skipping download.")

    # Extract XML.
    decompress_gzip(
        archive,
        output
    )

    if not output.exists():
        raise RuntimeError(
            f"Failed to create {output}"
        )

    print(
        f"Created: {output} "
        f"({output.stat().st_size / 1024 / 1024:.1f} MB)"
    )

    # The .gz is no longer needed once extraction succeeded.
    archive.unlink()

    print(
        f"Removed temporary archive: {archive.name}"
    )


def main():
    DATA_DIR.mkdir(
        parents=True,
        exist_ok=True
    )

    print("KanjiDB dictionary source downloader")
    print(f"Destination: {DATA_DIR}")

    for source in SOURCES:
        process_source(source)

    print()
    print("=" * 60)
    print("DONE")
    print("=" * 60)

    print()
    print("Dictionary source files are ready:")

    for source in SOURCES:
        print(
            f"  {source['output']}"
        )


if __name__ == "__main__":
    main()