from __future__ import annotations

import argparse
import shutil
from pathlib import Path

from ultralytics import YOLO


DEFAULT_ASSET_NAME = "document_corners_float16.tflite"


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Export a YOLO pose model to a float16 TFLite artifact for Scanly.",
    )
    parser.add_argument("--model", required=True, help="Path to the trained .pt model.")
    parser.add_argument(
        "--imgsz",
        type=int,
        default=640,
        help="Inference image size to use during export.",
    )
    parser.add_argument(
        "--output-dir",
        default="build/ml-export",
        help="Directory used by the export flow.",
    )
    parser.add_argument(
        "--copy-to-assets",
        default="app/src/main/assets/models",
        help="Copy the resolved .tflite artifact into this directory after export.",
    )
    return parser.parse_args()


def resolve_tflite_path(export_result: object) -> Path:
    export_path = Path(str(export_result))
    if export_path.is_file() and export_path.suffix == ".tflite":
        return export_path

    search_root = export_path if export_path.exists() else export_path.parent
    candidates = sorted(search_root.rglob("*.tflite"))
    if not candidates:
        raise FileNotFoundError(
            f"Could not find a .tflite artifact under {search_root.resolve()}",
        )
    return candidates[0]


def main() -> None:
    args = parse_args()
    output_dir = Path(args.output_dir)
    output_dir.mkdir(parents=True, exist_ok=True)

    model = YOLO(args.model)
    export_result = model.export(
        format="tflite",
        imgsz=args.imgsz,
        half=True,
        project=str(output_dir),
    )

    exported_model = resolve_tflite_path(export_result)
    assets_dir = Path(args.copy_to_assets)
    assets_dir.mkdir(parents=True, exist_ok=True)
    target_path = assets_dir / DEFAULT_ASSET_NAME
    shutil.copy2(exported_model, target_path)

    print(f"Exported model: {exported_model.resolve()}")
    print(f"Copied to assets: {target_path.resolve()}")


if __name__ == "__main__":
    main()
