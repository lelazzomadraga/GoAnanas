"""Builds an UNTRAINED demo model with the same architecture so the app can be installed
and tested before the real model is trained. The app shows a "demo model" banner when
model_info.json has "trained": false. Replace it by running train_model.py --copy-to-app."""
import json
from pathlib import Path

import tensorflow as tf

from train_model import CLASSES, IMG, build_model, to_tflite

assets = Path(__file__).resolve().parent.parent / "app" / "src" / "main" / "assets"
assets.mkdir(parents=True, exist_ok=True)
model, _ = build_model(weights=None)
to_tflite(model, assets / "go_ananas_model.tflite")
(assets / "labels.txt").write_text("\n".join(CLASSES) + "\n")
(assets / "model_info.json").write_text(json.dumps(
    {"version": "0.1-demo", "trained": False, "test_accuracy": "not yet trained",
     "input": [IMG, IMG, 3], "classes": CLASSES}, indent=2))
print("Demo model written to", assets)
