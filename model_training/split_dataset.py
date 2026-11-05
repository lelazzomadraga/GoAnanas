"""Split dataset/all/<class>/ into dataset/train, dataset/val, dataset/test (70/15/15).
With 300 photos per class this gives 210 / 45 / 45 per class (270 test images in total)."""
import argparse
import random
import shutil
from pathlib import Path

ap = argparse.ArgumentParser()
ap.add_argument("--src", default="dataset/all")
ap.add_argument("--dst", default="dataset")
ap.add_argument("--seed", type=int, default=42)
args = ap.parse_args()

random.seed(args.seed)
for cls in sorted(p for p in Path(args.src).iterdir() if p.is_dir()):
    files = sorted(f for f in cls.iterdir() if f.is_file())
    random.shuffle(files)
    n = len(files)
    n_tr, n_va = round(n * 0.70), round(n * 0.15)
    parts = {"train": files[:n_tr], "val": files[n_tr:n_tr + n_va], "test": files[n_tr + n_va:]}
    for split, items in parts.items():
        d = Path(args.dst) / split / cls.name
        d.mkdir(parents=True, exist_ok=True)
        for f in items:
            shutil.copy2(f, d / f.name)
    print(f"{cls.name}: train {len(parts['train'])}, val {len(parts['val'])}, test {len(parts['test'])}")
