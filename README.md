<div align="center">

# VISION: An Intelligent Driving Agent for Enhanced Road Safety

**Offline-First Edge AI Advanced Driver-Assistance System (ADAS) for Arabic & Regional Road Environments**

[![Platform](https://img.shields.io/badge/Platform-Android%20(Jetpack%20Compose)-3DDC84?logo=android&logoColor=white)](#android-application-setup)
[![Inference](https://img.shields.io/badge/Inference-ONNX%20Runtime%20(FP32)-005CED?logo=onnx&logoColor=white)](#model-benchmarking--selection)
[![Dataset](https://img.shields.io/badge/Dataset-8%2C842%20Images%20%7C%2020%20Classes-FF6F00)](#dataset--leak-free-protocol)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

</div>

---

## Abstract

**VISION** is a real-time, privacy-preserving, **offline-first Edge AI driving agent** built for Android devices. Unlike cloud-dependent ADAS solutions that suffer from network latency and connectivity dropouts, VISION executes its entire perception and decision pipeline on-device: **CameraX** frame acquisition, **YOLOv11n** inference via **ONNX Runtime**, **Bayesian HSV + edge verification**, **Google ML Kit** zero-shot OCR for speed-limit signs, **2-of-3 frame temporal consensus**, and bilingual **SoundPool** audio alerts.

On a strictly held-out, unaugmented test split of **884 Arabic road images**, the deployed `YOLOv11n-ADAS-v2` (FP32 ONNX, `10.12 MiB`) achieves **95.82% mAP@50**, **97.17% Precision**, and **93.05% Recall** at **34.58 ms** mean CPU inference latency (~28.9 FPS)—**10.8× faster** than transformer-based `D-FINE-N`.

---

## Key Contributions

1. **Leak-Free Arabic Road Dataset (8,842 Images | 12,203 Annotations):**
   Curated and split strictly prior to augmentation (`6,191` Train / `1,767` Validation / `884` Unseen Test), with 3× low-gamma night speed-bump oversampling applied exclusively to the training split.
2. **Multi-Architecture Edge Benchmarking:**
   Systematic latency–accuracy evaluation across **YOLOv11n**, **YOLO26n (INT8)**, **D-FINE-N**, and **RT-DETRv2-S** under edge CPU constraints.
3. **Hybrid 14 + 6 = 20-Class Perception Pipeline:**
   Combines 14 CNN-trained hazard/sign classes with `SpeedSignVerifier` (Google ML Kit OCR) to recognize 6 additional zero-shot speed limits (`20, 70, 90, 110, 120, 140 km/h`) without retraining, plus `AdasSignVerifier` (Bayesian HSV/edge filtering) to suppress dusk and glare false positives.
4. **3-Tier Offline-First Android Architecture:**
   Built with Kotlin & Jetpack Compose Material 3, featuring 7 screens, Role-Based Access Control (Guest with zero disk writes, Registered Driver with local SQLite v4 trip logging, and Administrator dashboard), and strictly opt-in Firebase RTDB telemetry.

---

## System Architecture

<p align="center">
  <img src="diagrams/system_architecture.png" alt="VISION 3-Tier System Architecture" width="90%"/>
</p>

<details>
<summary><b>Additional Architecture & Pipeline Diagrams</b></summary>

- **End-to-End ML Process Flow:** [`diagrams/ml_pipeline_process_flow.png`](diagrams/ml_pipeline_process_flow.png)
- **Bayesian & OCR Verification Pipeline:** [`diagrams/pipeline_bayesian.png`](diagrams/pipeline_bayesian.png)
- **Security & Privacy Architecture:** [`diagrams/security_architecture.png`](diagrams/security_architecture.png)

</details>

---

## Model Benchmarking & Selection

All four architectures were evaluated on CPU to verify compliance with the `< 150 ms` end-to-end real-time driving safety budget:

| Model | Params / Format | Mean CPU Latency | Speedup vs. D-FINE-N | Val mAP@50 / mAP@50-95 | Decision |
| :--- | :--- | :---: | :---: | :---: | :--- |
| **YOLOv11n-ADAS-v2** | **2.59M · FP32 ONNX (`10.12 MiB`)** | **34.58 ms (~28.9 FPS)** | **10.8×** | **96.33% / 85.62%** *(95.82% Unseen Test)* | **Selected** |
| YOLO26n | 2.51M · INT8 TFLite (`2.76 MiB`) | 118.6 ms (~8.4 FPS) | 3.15× | 96.8% / 82.5% | Superseded (lower mAP@50-95 & 3.4× slower) |
| D-FINE-N (DEIM) | 3.73M · ONNX (`14.54 MB`) | 373.82 ms (2.7 FPS) | 1.0× | 97.0% / 86.4% | Rejected (exceeds 150 ms budget; 6 GridSample ops) |
| RT-DETRv2-S | 20.1M · ONNX / Ckpt | 681.45 ms (~1.5 FPS) | 0.55× | 96.7% / 86.2% | Rejected (1,735 ms mobile CPU; 20.1M params) |

### Unseen Test Set Performance (`n = 884` images, `1,278` instances)
- **mAP@50:** `95.82%`
- **Precision:** `97.17%`
- **Recall:** `93.05%`
- **mAP@50-95:** `85.62%`

---

## Dataset & Leak-Free Protocol

- **Total Images:** `8,842` (`12,203` bounding boxes)
- **Split:** `6,191` Train (70%) / `1,767` Validation (20%) / `884` Unseen Test (10%)
- **Download:** The full dataset archive (`Offline Arabic ADAS.yolov11.zip`, 529 MB) is hosted in **[GitHub Releases](../../releases)**.
- **Roboflow Export Script:** See [`dataset/Dataset.txt`](dataset/Dataset.txt) (configure your own `ROBOFLOW_API_KEY` environment variable).

---

## Repository Structure

```text
├── android-app/            # Kotlin + Jetpack Compose Android application & ONNX engine
├── training/               # Model training & ONNX export notebooks (YOLOv11, YOLO26, D-FINE, RT-DETRv2)
├── dataset/                # Dataset export script (full archive in GitHub Releases)
├── diagrams/               # System architecture, ML pipeline, Bayesian & security diagrams
├── CITATION.cff            # Academic citation metadata
├── LICENSE                 # MIT License
└── README.md               # Technical report & documentation
```

---

## Android Application Setup

1. Open `android-app/` in **Android Studio**.
2. Create `android-app/local.properties` (excluded from Git):
   ```properties
   sdk.dir=YOUR_ANDROID_SDK_PATH
   # Optional: only needed if enabling Admin Firebase RTDB telemetry sync
   CLOUD_BASE_URL=https://your-project-id-default-rtdb.firebaseio.com
   ```
3. Build and run on an Android device (`minSdk = 26`, `arm64-v8a` / `armeabi-v7a`), or install the pre-built APK from **[GitHub Releases](../../releases)**.
4. Run unit tests:
   ```bash
   cd android-app
   ./gradlew testDebugUnitTest
   ```
   *(Verified 12/12 passing JVM unit tests across `HazardCatalog`, `LocationTracker`, `Export`, and `HazardEvaluator`.)*

---

## Authors

| Name | Role |
| :--- | :--- |
| **Abdulhamid Alaboud** | **Project Manager, Lead Architect & AI/ML Engineer** *(End-to-End AI Pipeline, Multi-Model Training, Benchmarking & ONNX Edge Deployment)* |
| **Ahmed Alaglan** | Database & Backend Engineer |
| **Edris Khawaji** | Security & Cloud Telemetry Analyst |
| **Faisal Ageeli** | QA & Verification Engineer |
| **Abdullah Arishi** | Systems Architect & Data Curator |
| **Hussain Abutaleb** | UI/UX & Audio Designer |

---

## Citation

If you use this codebase, models, or dataset in your research, please cite using [`CITATION.cff`](CITATION.cff).
