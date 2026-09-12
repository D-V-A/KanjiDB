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
    temp_destination = destination.with_suffix(
        destination.suffix + ".part"
    )

    print("Downloading:")
    print(f"  {url}")
    print(f"  -> {destination}")

    # Remove stale partial download from a previous failed attempt.
    if temp_destination.exists():
        print(
            f"Removing stale partial download: "
            f"{temp_destination.name}"
        )
        temp_destination.unlink()

    request = urllib.request.Request(
        url,
        headers={
            "User-Agent": "KanjiDB dictionary builder"
        }
    )

    try:
        with urllib.request.urlopen(request) as response:
            total_size = response.headers.get(
                "Content-Length"
            )

            if total_size is not None:
                total_size = int(total_size)

            downloaded = 0
            chunk_size = 1024 * 1024

            with open(temp_destination, "wb") as output:
                while True:
                    chunk = response.read(chunk_size)

                    if not chunk:
                        break

                    output.write(chunk)
                    downloaded += len(chunk)

                    if total_size:
                        percent = (
                            downloaded * 100 / total_size
                        )

                        print(
                            f"\r  "
                            f"{downloaded / 1024 / 1024:.1f} MB "
                            f"/ "
                            f"{total_size / 1024 / 1024:.1f} MB "
                            f"({percent:.1f}%)",
                            end="",
                            flush=True
                        )
                    else:
                        print(
                            f"\r  "
                            f"{downloaded / 1024 / 1024:.1f} MB",
                            end="",
                            flush=True
                        )

        print()

        if total_size is not None:
            actual_size = temp_destination.stat().st_size

            if actual_size != total_size:
                raise RuntimeError(
                    "Downloaded file size mismatch: "
                    f"expected {total_size} bytes, "
                    f"got {actual_size} bytes."
                )

        # Replace final archive only after the download
        # has completed successfully.
        temp_destination.replace(destination)

    except Exception:
        if temp_destination.exists():
            temp_destination.unlink()

        raise


def decompress_gzip(source, destination):
    temp_destination = destination.with_suffix(
        destination.suffix + ".part"
    )

    print("Decompressing:")
    print(f"  {source}")
    print(f"  -> {destination}")

    if temp_destination.exists():
        temp_destination.unlink()

    try:
        with gzip.open(source, "rb") as compressed:
            with open(temp_destination, "wb") as output:
                shutil.copyfileobj(
                    compressed,
                    output,
                    length=1024 * 1024
                )

        # Only expose final XML after successful decompression.
        temp_destination.replace(destination)

    except Exception:
        if temp_destination.exists():
            temp_destination.unlink()

        raise


def process_source(source):
    name = source["name"]
    url = source["url"]
    archive = source["archive"]
    output = source["output"]

    print()
    print("=" * 60)
    print(name)
    print("=" * 60)

    # If final XML is already present, do nothing.
    if output.exists():
        print("Already exists:")
        print(f"  {output}")
        print("Skipping download.")
        return

    # Download archive if it is not already available.
    if not archive.exists():
        download_file(
            url,
            archive
        )
    else:
        print("Archive already exists:")
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

    # Compressed archive is no longer needed after
    # successful extraction.
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