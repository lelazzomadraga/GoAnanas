# Go Ananas! – Android App, Web Dashboard, and Model Training

Detection of Pineapple (*Ananas comosus*) Diseases Using Digital Image Processing and Convolutional Neural Network
BS Computer Engineering, Asia Technological School of Science and Arts

## What is in this folder

| Folder | Contents |
|---|---|
| `app/` | Android app (Kotlin, Jetpack Compose). Camera, on-phone diagnosis, history, dashboard, disease guide, reminders, and optional cloud sync. |
| `model_training/` | Python scripts that train the MobileNetV2 model and export it to TensorFlow Lite. |
| `web_dashboard/` | Web page for the farm owner and agricultural technician (Firebase). Opens in demo mode with sample data when no Firebase config is present. |
| `firebase/` | Firestore and Storage security rules and the Cloud Function that sends push notifications. |
| `.github/workflows/` | Builds the APK automatically on GitHub. |

## App features

- **Capture**: CameraX preview with a guide frame, or pick a photo from the gallery. The user selects the plant part (leaf, crown, heart, base/roots, fruit, whole plant).
- **Image processing (same as Chapter 3)**: resize to 224 × 224 → 3 × 3 Gaussian filter → CLAHE on the L channel of CIELAB → scale to [-1, 1]. Done with OpenCV on the phone.
- **Classification**: MobileNetV2 TensorFlow Lite model, six classes (Fruit Rot, Healthy, Heart Rot, Mealybug Wilt, Multiple Crown Disorder, Root Rot). Works offline. Shows the top class, confidence, all six probabilities, and the processing time.
- **Photo quality checks**: warns when the photo is blurred, too dark, or too bright, and when confidence is below 60%.
- **Recommended action and disease guide**: symptoms, cause, management, local name, and how to photograph each condition.
- **Scan history (Room database)**: photo, result, block/row, plant part, notes, status (Open, Rechecked, Resolved, Healthy). Search and filters.
- **Farm dashboard**: totals, results per class, scans per week, most affected blocks, plants waiting for a re-check.
- **Notifications**:
  - re-check reminder after 7 to 30 days depending on the disease (WorkManager);
  - monthly inspection reminder;
  - outbreak alert when 3 or more plants with the same disease are recorded in one block within 14 days;
  - push notification to the farm owner and technician when a worker uploads a diseased scan (Firebase Cloud Messaging).
- **Accounts and cloud sync (optional)**: e-mail sign-in, users grouped by farm code, scans and photos uploaded to Firebase when the phone is online.

## 1. Build the APK

### Option A – Android Studio (recommended)
1. Install Android Studio (Ladybug 2024.2 or newer).
2. *File → Open* and select this folder. Wait for Gradle sync to finish.
3. Connect an Android phone (Android 8.0 or newer, USB debugging on) and press **Run**, or use *Build → Build APK(s)*.
4. The APK is in `app/build/outputs/apk/debug/app-debug.apk`.

### Option B – GitHub (no Android Studio needed)
1. Create a GitHub repository and upload this folder.
2. Open the **Actions** tab and run **Build Go Ananas! APK**. It also runs on every push to `main`.
3. Download the APK from the **Artifacts** section of the finished run.

### Option C – command line
```
./gradlew assembleDebug        (Windows: gradlew.bat assembleDebug)
```
Requires JDK 17 and the Android SDK (`ANDROID_HOME` or `local.properties`).

## 2. Train the real model

The app currently contains a **demo model with random weights** (`app/src/main/assets`). It is there only so the app can be installed and tested; its results are not real diagnoses, and the app shows a "Demo model" banner. Replace it with the trained model:

```
cd model_training
pip install -r requirements.txt
# put the photos in dataset/all/<class_name>/ (300 per class), then:
python split_dataset.py                 # 70 / 15 / 15 split -> dataset/train, val, test
python train_model.py --copy-to-app     # trains, evaluates, and copies the model into the app
```

Folder names must be exactly: `fruit_rot`, `healthy`, `heart_rot`, `mealybug_wilt`, `multiple_crown_disorder`, `root_rot`.

`train_model.py` follows Table 3.5 of the paper: MobileNetV2 (ImageNet) + Global Average Pooling + Dense 128 (ReLU) + Dropout 0.3 + Dense 6 (Softmax); 25 epochs at a learning rate of 0.001, then 15 epochs of fine-tuning of the last 30 layers at 0.00001; batch size 32; early stopping (patience 5). It writes the confusion matrix, classification report, and training history to `output/` for Chapter 4. The model can also be trained on Google Colab with a GPU.

After copying the model, rebuild the APK. The demo banner disappears because `model_info.json` now says `"trained": true`.

## 3. Turn on cloud sync, the web dashboard, and push notifications (optional)

Without these steps the app works fully offline.

1. Create a project at <https://console.firebase.google.com>.
2. **Authentication** → Sign-in method → enable *Email/Password*.
3. Create a **Cloud Firestore** database and a **Storage** bucket.
4. **Android app**: add an Android app with package name `ph.edu.asiatech.goananas`, download `google-services.json`, and put it in the `app/` folder. Rebuild the APK. (For the GitHub build, save the file content as a repository secret named `GOOGLE_SERVICES_JSON`.)
5. **Web app**: add a Web app, copy its config into `web_dashboard/firebase-config.js` (see `firebase-config.example.js`).
6. Install the Firebase CLI (`npm install -g firebase-tools`), then inside the `firebase/` folder:
   ```
   firebase login
   firebase use --add            (choose the project)
   cd functions && npm install && cd ..
   firebase deploy               (rules, Cloud Function, and the web dashboard on Firebase Hosting)
   ```
   Cloud Functions require the Blaze (pay-as-you-go) plan; usage for a single farm stays within the free quota. If the Function is not deployed, everything else still works, only the push notifications from other users are not sent.
7. The first outbreak query asks Firestore for a composite index; open the link shown in the Functions log once to create it.

Users who register with the **same farm code** share their scans. Farm owners and agricultural technicians can review scans, add notes, and change the status in the web dashboard.

## Database design

**On the phone (Room, `go_ananas.db`, table `scans`)**

| Field | Type | Description |
|---|---|---|
| id | INTEGER, PK | local record number |
| uuid | TEXT, unique | id shared with the cloud copy |
| imagePath | TEXT | photo saved in the app's private folder |
| plantPart | TEXT | part photographed |
| block | TEXT | farm block or row |
| predictedLabel | TEXT | class returned by the model |
| confidence | REAL | probability of the top class |
| probabilities | TEXT | six probabilities, comma-separated |
| inferenceMs | INTEGER | processing time |
| notes | TEXT | user notes |
| status | TEXT | Open, Rechecked, Resolved, Healthy |
| createdAt / recheckAt | INTEGER | time of scan / re-check due date |
| userId, synced, remoteImageUrl | TEXT / INTEGER / TEXT | cloud sync fields |

Profile and notification preferences are stored with Jetpack DataStore.

**In the cloud (Cloud Firestore)**
- `users/{uid}`: name, role, farmCode, email, fcmToken, createdAt
- `farms/{farmCode}/scans/{uuid}`: the fields above plus userName, role, predictedClass, isHealthy, recommendedAction, imageUrl, reviewNotes, updatedAt
- Photos: Cloud Storage `farms/{farmCode}/scans/{uuid}.jpg`

## Notes and limitations

- Sync goes from the phone to the cloud. A status changed in the web dashboard is not copied back to the phone.
- The app identifies only visible surface symptoms. Internal conditions such as "kalawang" cannot be detected, and the result is a screening aid, not a laboratory diagnosis.
- Minimum Android version: 8.0 (API 26). Target: Android 15 (API 35).
