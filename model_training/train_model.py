"""
Go Ananas! model training script.

Trains the MobileNetV2 classifier described in Chapter 3 (Table 3.5) and exports
it to TensorFlow Lite for the Android app and the web app.

Dataset layout (one folder per class, names in lowercase with underscores):

    dataset/
      train/  fruit_rot/ healthy/ heart_rot/ mealybug_wilt/ multiple_crown_disorder/ root_rot/
      val/    (same six folders)
      test/   (same six folders)

If only dataset/all/<class>/ exists, run split_dataset.py first (70/15/15 split).

Usage:
    python train_model.py --data dataset --out output --copy-to-app

Outputs (in --out):
    go_ananas_model.keras, go_ananas_model.tflite, labels.txt, model_info.json,
    confusion_matrix.csv, classification_report.txt, training_history.csv
"""
import argparse
import csv
import json
import shutil
from datetime import date
from pathlib import Path

import cv2
import numpy as np
import tensorflow as tf

IMG = 224
CLASSES = ["fruit_rot", "healthy", "heart_rot", "mealybug_wilt", "multiple_crown_disorder", "root_rot"]
EXTS = {".jpg", ".jpeg", ".png", ".bmp", ".webp"}


def preprocess(path: str) -> np.ndarray:
    """Same steps as the Android app (ImagePreprocessor.kt) and the web app:
    resize 224x224 -> Gaussian blur 3x3 -> CLAHE on L channel of LAB -> scale to [-1, 1]."""
    bgr = cv2.imread(path, cv2.IMREAD_COLOR)
    if bgr is None:
        raise ValueError(f"Cannot read {path}")
    bgr = cv2.resize(bgr, (IMG, IMG), interpolation=cv2.INTER_AREA)
    bgr = cv2.GaussianBlur(bgr, (3, 3), 0)
    lab = cv2.cvtColor(bgr, cv2.COLOR_BGR2LAB)
    l, a, b = cv2.split(lab)
    l = cv2.createCLAHE(clipLimit=2.0, tileGridSize=(8, 8)).apply(l)
    rgb = cv2.cvtColor(cv2.merge((l, a, b)), cv2.COLOR_LAB2RGB)
    return rgb.astype(np.float32) / 127.5 - 1.0


def load_split(root: Path):
    xs, ys = [], []
    for idx, name in enumerate(CLASSES):
        folder = root / name
        if not folder.is_dir():
            raise SystemExit(f"Missing folder: {folder}")
        for f in sorted(folder.iterdir()):
            if f.suffix.lower() in EXTS:
                xs.append(preprocess(str(f)))
                ys.append(idx)
    print(f"{root.name}: {len(xs)} images")
    return np.stack(xs), np.array(ys)


def augmenter():
    # Figure 3.7: rotation, flip, zoom, brightness and contrast changes (training set only)
    return tf.keras.Sequential([
        tf.keras.layers.RandomFlip("horizontal_and_vertical"),
        tf.keras.layers.RandomRotation(0.08),          # about +/- 30 degrees
        tf.keras.layers.RandomZoom(0.15),
        tf.keras.layers.RandomBrightness(0.15, value_range=(-1.0, 1.0)),
        tf.keras.layers.RandomContrast(0.15),
    ], name="augmentation")


def build_model(weights="imagenet"):
    base = tf.keras.applications.MobileNetV2(input_shape=(IMG, IMG, 3), include_top=False, weights=weights)
    base.trainable = False
    inputs = tf.keras.Input((IMG, IMG, 3))
    x = base(inputs, training=False)
    x = tf.keras.layers.GlobalAveragePooling2D()(x)
    x = tf.keras.layers.Dense(128, activation="relu")(x)
    x = tf.keras.layers.Dropout(0.3)(x)
    outputs = tf.keras.layers.Dense(len(CLASSES), activation="softmax")(x)
    return tf.keras.Model(inputs, outputs, name="go_ananas_mobilenetv2"), base


def to_tflite(model, path: Path, quantize: bool = False):
    """Float32 export (about 9 MB) keeps every operator at version 1, so it runs on the
    TensorFlow Lite 2.16 runtime in the app. Set quantize=True for a smaller file (about 3 MB)
    only if the app's TensorFlow Lite library is upgraded to match the training version."""
    conv = tf.lite.TFLiteConverter.from_keras_model(model)
    if quantize:
        conv.optimizations = [tf.lite.Optimize.DEFAULT]
    path.write_bytes(conv.convert())


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--data", default="dataset")
    ap.add_argument("--out", default="output")
    ap.add_argument("--epochs", type=int, default=25)
    ap.add_argument("--fine-tune-epochs", type=int, default=15)
    ap.add_argument("--batch", type=int, default=32)
    ap.add_argument("--version", default=f"1.0-{date.today():%Y%m%d}")
    ap.add_argument("--copy-to-app", action="store_true", help="copy the model into app/src/main/assets")
    args = ap.parse_args()

    data, out = Path(args.data), Path(args.out)
    out.mkdir(parents=True, exist_ok=True)
    x_tr, y_tr = load_split(data / "train")
    x_va, y_va = load_split(data / "val")
    x_te, y_te = load_split(data / "test")

    aug = augmenter()
    train_ds = (tf.data.Dataset.from_tensor_slices((x_tr, y_tr)).shuffle(len(x_tr), seed=42)
                .batch(args.batch).map(lambda x, y: (aug(x, training=True), y)).prefetch(2))
    val_ds = tf.data.Dataset.from_tensor_slices((x_va, y_va)).batch(args.batch)

    model, base = build_model()
    stop = tf.keras.callbacks.EarlyStopping(monitor="val_loss", patience=5, restore_best_weights=True)

    # Stage 1: train the new classifier head (learning rate 0.001)
    model.compile(tf.keras.optimizers.Adam(1e-3), "sparse_categorical_crossentropy", metrics=["accuracy"])
    h1 = model.fit(train_ds, validation_data=val_ds, epochs=args.epochs, callbacks=[stop])

    # Stage 2: fine-tune the last 30 layers of MobileNetV2 (learning rate 0.00001)
    base.trainable = True
    for layer in base.layers[:-30]:
        layer.trainable = False
    model.compile(tf.keras.optimizers.Adam(1e-5), "sparse_categorical_crossentropy", metrics=["accuracy"])
    h2 = model.fit(train_ds, validation_data=val_ds, epochs=args.fine_tune_epochs, callbacks=[stop])

    # History
    with open(out / "training_history.csv", "w", newline="") as f:
        w = csv.writer(f)
        w.writerow(["stage", "epoch", "accuracy", "loss", "val_accuracy", "val_loss"])
        for stage, h in (("head", h1), ("fine_tune", h2)):
            for i in range(len(h.history["loss"])):
                w.writerow([stage, i + 1] + [round(h.history[k][i], 4) for k in ("accuracy", "loss", "val_accuracy", "val_loss")])

    # Evaluation on the test set
    pred = model.predict(x_te, batch_size=args.batch).argmax(1)
    cm = np.zeros((len(CLASSES), len(CLASSES)), dtype=int)
    for t, p in zip(y_te, pred):
        cm[t, p] += 1
    acc = float((pred == y_te).mean())
    with open(out / "confusion_matrix.csv", "w", newline="") as f:
        w = csv.writer(f)
        w.writerow(["actual \\ predicted"] + CLASSES)
        for i, name in enumerate(CLASSES):
            w.writerow([name] + cm[i].tolist())
    lines = [f"{'class':<26}{'precision':>10}{'recall':>10}{'f1':>10}{'support':>10}"]
    for i, name in enumerate(CLASSES):
        tp = cm[i, i]
        prec = tp / cm[:, i].sum() if cm[:, i].sum() else 0
        rec = tp / cm[i].sum() if cm[i].sum() else 0
        f1 = 2 * prec * rec / (prec + rec) if prec + rec else 0
        lines.append(f"{name:<26}{prec:>10.4f}{rec:>10.4f}{f1:>10.4f}{cm[i].sum():>10}")
    lines.append(f"\nOverall accuracy: {acc:.4f} ({(pred == y_te).sum()} of {len(y_te)})")
    (out / "classification_report.txt").write_text("\n".join(lines))
    print("\n".join(lines))

    # Export
    model.save(out / "go_ananas_model.keras")
    to_tflite(model, out / "go_ananas_model.tflite")
    (out / "labels.txt").write_text("\n".join(CLASSES) + "\n")
    info = {"version": args.version, "trained": True, "test_accuracy": f"{acc * 100:.2f}%",
            "input": [IMG, IMG, 3], "classes": CLASSES}
    (out / "model_info.json").write_text(json.dumps(info, indent=2))

    if args.copy_to_app:
        assets = Path(__file__).resolve().parent.parent / "app" / "src" / "main" / "assets"
        assets.mkdir(parents=True, exist_ok=True)
        for name in ("go_ananas_model.tflite", "labels.txt", "model_info.json"):
            shutil.copy(out / name, assets / name)
        print(f"Copied model files to {assets}")


if __name__ == "__main__":
    main()
