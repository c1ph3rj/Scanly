#!/usr/bin/env python3
"""Generate Scanly shared-library seed data for performance testing."""

from __future__ import annotations

import argparse
import hashlib
import io
import json
import random
import sys
import time
import urllib.error
import urllib.request
import uuid
from dataclasses import dataclass
from datetime import datetime, timedelta
from pathlib import Path
from typing import Any

try:
    from PIL import Image
except ImportError:
    print("Pillow is required. Install with: pip install Pillow", file=sys.stderr)
    sys.exit(1)

SCRIPT_DIR = Path(__file__).resolve().parent
DEFAULT_SOURCES = SCRIPT_DIR / "image_sources.json"
CACHE_DIR = SCRIPT_DIR / ".cache"
CURRENT_LIBRARY_FORMAT = 1

FILTER_PRESETS = ["auto", "original", "grayscale", "black_and_white", "enhanced_color", "magic_color"]
ROTATIONS = [0, 0, 0, 0, 90, 180, 270]

GROUPS = [
    ("Work", 35),
    ("Receipts", 40),
    ("Medical", 25),
    ("Tax & Finance", 30),
    ("Contracts", 28),
    ("Invoices", 32),
    ("ID & Cards", 18),
    ("Notes", 22),
    ("Shipping", 20),
    ("School", 18),
    ("Travel", 15),
    ("Archive", 13),
]
UNGROUPED_COUNT = 24

TITLE_TEMPLATES = {
    "Work": ["Meeting Notes {date}", "Project Brief {n}", "Status Report Q{q}", "Memo - {topic}", "Team Update {date}"],
    "Receipts": ["Receipt - {store} {date}", "Purchase {store} #{n}", "Expense {date}", "Coffee Shop {date}", "Grocery Run {date}"],
    "Medical": ["Lab Results {date}", "Prescription {date}", "Visit Summary {date}", "Insurance EOB {n}", "Vaccination Record"],
    "Tax & Finance": ["W-2 {year}", "Tax Return {year}", "Bank Statement {month}", "Investment Summary Q{q}", "1099 Form {year}"],
    "Contracts": ["Lease Agreement {year}", "NDA - {topic}", "Service Contract {n}", "Employment Offer", "Vendor Agreement {date}"],
    "Invoices": ["Invoice #{n}", "Invoice Acme {date}", "Utility Bill {month}", "Vendor Invoice {n}", "Statement {date}"],
    "ID & Cards": ["Driver License", "Insurance Card", "Student ID", "Membership Card", "Passport Copy"],
    "Notes": ["Quick Notes {date}", "Ideas - {topic}", "Shopping List", "Journal Entry {date}", "Brainstorm {topic}"],
    "Shipping": ["Packing Slip #{n}", "Delivery Note {date}", "Return Label", "Waybill {n}", "Shipment Tracking {date}"],
    "School": ["Exam Paper {topic}", "Report Card {year}", "Assignment {topic}", "Transcript Copy", "Lecture Notes {date}"],
    "Travel": ["Boarding Pass {date}", "Hotel Invoice {date}", "Itinerary {month}", "Rental Agreement", "Travel Receipt {date}"],
    "Archive": ["Archive Scan {n}", "Old Document {year}", "Historical Record {date}", "Legacy File {n}", "Backup Scan {date}"],
    None: ["Scan {date}", "Document {n}", "Imported {date}", "File {n}", "Capture {date}"],
}

STORES = ["Starbucks", "Target", "Amazon", "Walmart", "Costco", "Whole Foods", "CVS", "Home Depot"]
TOPICS = ["Marketing", "Engineering", "Budget", "Planning", "Review", "Onboarding", "Compliance"]
MONTHS = ["Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"]


@dataclass
class ImageSource:
    id: str
    url: str
    category: str


@dataclass
class PreparedImage:
    source_id: str
    processed_bytes: bytes
    thumb_bytes: bytes


def sha256_bytes(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def encode_json(value: Any) -> bytes:
    return json.dumps(value, separators=(",", ":"), ensure_ascii=False).encode("utf-8")


def manifest_checksum(manifest: dict[str, Any]) -> str:
    return sha256_bytes(encode_json(manifest))


def pad_revision(value: int) -> str:
    return str(value).zfill(12)


def load_sources(path: Path) -> list[ImageSource]:
    payload = json.loads(path.read_text(encoding="utf-8"))
    return [ImageSource(**entry) for entry in payload]


def download_bytes(url: str, timeout: int = 60) -> bytes:
    request = urllib.request.Request(
        url,
        headers={"User-Agent": "ScanlyPerformanceSeed/1.0"},
    )
    with urllib.request.urlopen(request, timeout=timeout) as response:
        return response.read()


def to_jpeg_bytes(data: bytes, max_width: int, quality: int) -> bytes:
    image = Image.open(io.BytesIO(data))
    if image.mode not in ("RGB", "L"):
        image = image.convert("RGB")
    width, height = image.size
    if width > max_width:
        new_height = max(1, int(height * (max_width / width)))
        image = image.resize((max_width, new_height), Image.Resampling.LANCZOS)
    buffer = io.BytesIO()
    image.save(buffer, format="JPEG", quality=quality, optimize=True)
    return buffer.getvalue()


def prepare_source_image(source: ImageSource, cache_dir: Path) -> PreparedImage | None:
    cache_dir.mkdir(parents=True, exist_ok=True)
    processed_cache = cache_dir / f"{source.id}-processed.jpg"
    thumb_cache = cache_dir / f"{source.id}-thumb.jpg"

    if processed_cache.exists() and thumb_cache.exists():
        return PreparedImage(
            source_id=source.id,
            processed_bytes=processed_cache.read_bytes(),
            thumb_bytes=thumb_cache.read_bytes(),
        )

    fallback_url = f"https://picsum.photos/seed/{source.id}/1600/2200"
    for attempt_url in (source.url, fallback_url):
        try:
            raw = download_bytes(attempt_url)
            processed = to_jpeg_bytes(raw, max_width=random.randint(1200, 2000), quality=88)
            thumb = to_jpeg_bytes(processed, max_width=256, quality=82)
            processed_cache.write_bytes(processed)
            thumb_cache.write_bytes(thumb)
            return PreparedImage(
                source_id=source.id,
                processed_bytes=processed,
                thumb_bytes=thumb,
            )
        except (urllib.error.URLError, urllib.error.HTTPError, OSError, Image.UnidentifiedImageError) as error:
            print(f"  warn: {source.id} failed ({attempt_url}): {error}")

    return None


def page_count_for_index(index: int, total: int) -> int:
    ratio = index / max(total, 1)
    if ratio < 0.40:
        return 1
    if ratio < 0.70:
        return random.choice([2, 2, 3])
    if ratio < 0.90:
        return random.choice([4, 5, 5, 6])
    return random.choice([7, 8, 8, 9, 10])


def random_title(group_name: str | None, timestamp_ms: int) -> str:
    date = datetime.fromtimestamp(timestamp_ms / 1000)
    templates = TITLE_TEMPLATES[group_name]
    template = random.choice(templates)
    return (
        template.format(
            date=date.strftime("%d-%b-%Y"),
            n=random.randint(100, 9999),
            q=random.randint(1, 4),
            year=date.year,
            month=random.choice(MONTHS),
            store=random.choice(STORES),
            topic=random.choice(TOPICS),
        )[:200]
    )


def asset_ref(relative_path: str, revision: int, file_path: Path) -> dict[str, Any]:
    return {
        "relativePath": relative_path,
        "revision": revision,
        "byteCount": file_path.stat().st_size,
        "sha256": sha256_file(file_path),
        "mimeType": "image/jpeg",
    }


def write_manifest(path: Path, manifest: dict[str, Any]) -> str:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(encode_json(manifest))
    return sha256_bytes(encode_json(manifest))


def build_groups(start_ms: int, rng: random.Random) -> list[dict[str, Any]]:
    groups = []
    for title, _ in GROUPS:
        group_id = str(uuid.uuid4())
        created = start_ms + rng.randint(0, 30 * 24 * 60 * 60 * 1000)
        manifest = {
            "formatVersion": CURRENT_LIBRARY_FORMAT,
            "id": group_id,
            "revision": 1,
            "title": title,
            "createdAtMillis": created,
            "updatedAtMillis": created,
        }
        groups.append(
            {
                "id": group_id,
                "title": title,
                "manifest": manifest,
            }
        )
    return groups


def assign_documents(
    document_count: int,
    groups: list[dict[str, Any]],
    start_ms: int,
    rng: random.Random,
) -> list[dict[str, Any]]:
    assignments: list[tuple[str | None, str]] = []
    for group in groups:
        count = next(count for title, count in GROUPS if title == group["title"])
        assignments.extend([(group["id"], group["title"])] * count)
    assignments.extend([(None, "")] * UNGROUPED_COUNT)

    if len(assignments) > document_count:
        assignments = assignments[:document_count]
    while len(assignments) < document_count:
        group = rng.choice(groups)
        assignments.append((group["id"], group["title"]))

    docs = []
    for index, (group_id, group_title) in enumerate(assignments):
        timestamp = start_ms + rng.randint(0, 548 * 24 * 60 * 60 * 1000)
        docs.append(
            {
                "id": str(uuid.uuid4()),
                "group_id": group_id,
                "group_title": group_title or None,
                "title": random_title(group_title or None, timestamp),
                "page_count": page_count_for_index(index, document_count),
                "created_at": timestamp,
                "updated_at": timestamp + rng.randint(0, 7 * 24 * 60 * 60 * 1000),
                "filter": rng.choice(FILTER_PRESETS),
            }
        )
    return docs


def write_document_assets(
    output_dir: Path,
    document: dict[str, Any],
    prepared_images: list[PreparedImage],
    rng: random.Random,
) -> dict[str, Any]:
    doc_id = document["id"]
    revision = 1
    pages = []
    doc_dir = output_dir / "documents" / doc_id
    raw_dir = doc_dir / "raw"
    processed_dir = doc_dir / "processed"
    thumbs_dir = doc_dir / "thumbs"
    raw_dir.mkdir(parents=True, exist_ok=True)
    processed_dir.mkdir(parents=True, exist_ok=True)
    thumbs_dir.mkdir(parents=True, exist_ok=True)

    for page_index in range(document["page_count"]):
        page_id = str(uuid.uuid4())
        operation_id = str(uuid.uuid4())
        image = rng.choice(prepared_images)
        raw_path = raw_dir / f"{page_id}-{operation_id}.jpg"
        processed_path = processed_dir / f"{page_id}-r{revision}.jpg"
        thumb_path = thumbs_dir / f"{page_id}-r{revision}.jpg"

        raw_path.write_bytes(image.processed_bytes)
        processed_path.write_bytes(image.processed_bytes)
        thumb_path.write_bytes(image.thumb_bytes)

        page_created = document["created_at"] + page_index * rng.randint(1000, 60000)
        page_updated = document["updated_at"]
        page = {
            "id": page_id,
            "pageIndex": page_index,
            "rawAsset": asset_ref(
                f"documents/{doc_id}/raw/{page_id}-{operation_id}.jpg",
                revision,
                raw_path,
            ),
            "processedAsset": asset_ref(
                f"documents/{doc_id}/processed/{page_id}-r{revision}.jpg",
                revision,
                processed_path,
            ),
            "thumbnailAsset": asset_ref(
                f"documents/{doc_id}/thumbs/{page_id}-r{revision}.jpg",
                revision,
                thumb_path,
            ),
            "rotationDegrees": rng.choice(ROTATIONS),
            "filterPreset": document["filter"],
            "processingState": "processed",
            "createdAtMillis": page_created,
            "updatedAtMillis": page_updated,
        }
        pages.append(page)

    manifest: dict[str, Any] = {
        "formatVersion": CURRENT_LIBRARY_FORMAT,
        "id": doc_id,
        "revision": revision,
        "title": document["title"],
        "preferredFilterPreset": document["filter"],
        "createdAtMillis": document["created_at"],
        "updatedAtMillis": document["updated_at"],
        "pages": pages,
    }
    if document["group_id"]:
        manifest["groupId"] = document["group_id"]

    manifest_path = doc_dir / "manifests" / f"document-r{pad_revision(revision)}.json"
    checksum = write_manifest(manifest_path, manifest)
    return {
        "id": doc_id,
        "revision": revision,
        "checksum": checksum,
        "page_count": len(pages),
    }


def load_existing_catalog(path: Path | None) -> dict[str, Any] | None:
    if path is None or not path.exists():
        return None
    return json.loads(path.read_text(encoding="utf-8"))


def load_existing_marker(path: Path | None) -> dict[str, Any] | None:
    if path is None or not path.exists():
        return None
    return json.loads(path.read_text(encoding="utf-8"))


def generate_seed_pack(
    output_dir: Path,
    document_count: int,
    existing_catalog_path: Path | None,
    existing_marker_path: Path | None,
    sources_path: Path,
    seed: int,
) -> dict[str, Any]:
    rng = random.Random(seed)
    output_dir.mkdir(parents=True, exist_ok=True)

    existing_catalog = load_existing_catalog(existing_catalog_path)
    existing_marker = load_existing_marker(existing_marker_path)
    library_id = (
        existing_catalog["libraryId"]
        if existing_catalog
        else existing_marker["libraryId"]
        if existing_marker
        else str(uuid.uuid4())
    )
    previous_generation = existing_catalog["generation"] if existing_catalog else 0

    print(f"Preparing image cache from {sources_path.name}...")
    sources = load_sources(sources_path)
    prepared_images: list[PreparedImage] = []
    for index, source in enumerate(sources, start=1):
        print(f"  [{index}/{len(sources)}] {source.id}")
        prepared = prepare_source_image(source, CACHE_DIR)
        if prepared:
            prepared_images.append(prepared)
    if not prepared_images:
        raise RuntimeError("No images could be downloaded.")

    print(f"Prepared {len(prepared_images)} source images.")

    now_ms = int(time.time() * 1000)
    start_ms = now_ms - int(timedelta(days=548).total_seconds() * 1000)

    groups = build_groups(start_ms, rng)
    group_records = []
    for group in groups:
        manifest_path = output_dir / "groups" / group["id"] / f"group-r{pad_revision(1)}.json"
        checksum = write_manifest(manifest_path, group["manifest"])
        group_records.append({"id": group["id"], "revision": 1, "checksum": checksum})

    documents = assign_documents(document_count, groups, start_ms, rng)
    document_records = []
    total_pages = 0
    print(f"Generating {document_count} documents...")
    for index, document in enumerate(documents, start=1):
        record = write_document_assets(output_dir, document, prepared_images, rng)
        document_records.append(
            {"id": record["id"], "revision": record["revision"], "checksum": record["checksum"]}
        )
        total_pages += record["page_count"]
        if index % 25 == 0 or index == document_count:
            print(f"  documents: {index}/{document_count} ({total_pages} pages)")

    merged_documents = (existing_catalog["documents"] if existing_catalog else []) + document_records
    merged_groups = (existing_catalog["groups"] if existing_catalog else []) + group_records
    new_generation = previous_generation + 1

    catalog = {
        "formatVersion": CURRENT_LIBRARY_FORMAT,
        "libraryId": library_id,
        "generation": new_generation,
        "documents": merged_documents,
        "groups": merged_groups,
        "tombstones": existing_catalog["tombstones"] if existing_catalog else [],
    }
    catalog_path = output_dir / "catalog" / f"catalog-r{pad_revision(new_generation)}.json"
    write_manifest(catalog_path, catalog)

    summary = {
        "libraryId": library_id,
        "generation": new_generation,
        "newDocuments": len(document_records),
        "newGroups": len(group_records),
        "newPages": total_pages,
        "totalDocuments": len(merged_documents),
        "totalGroups": len(merged_groups),
        "catalogPath": str(catalog_path.relative_to(output_dir)).replace("\\", "/"),
    }
    (output_dir / "seed-summary.json").write_text(
        json.dumps(summary, indent=2),
        encoding="utf-8",
    )
    return summary


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Generate Scanly performance seed data.")
    parser.add_argument("--output-dir", type=Path, required=True)
    parser.add_argument("--document-count", type=int, default=280)
    parser.add_argument("--existing-catalog", type=Path, default=None)
    parser.add_argument("--existing-marker", type=Path, default=None)
    parser.add_argument("--sources", type=Path, default=DEFAULT_SOURCES)
    parser.add_argument("--seed", type=int, default=42)
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    summary = generate_seed_pack(
        output_dir=args.output_dir,
        document_count=args.document_count,
        existing_catalog_path=args.existing_catalog,
        existing_marker_path=args.existing_marker,
        sources_path=args.sources,
        seed=args.seed,
    )
    print(json.dumps(summary, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())