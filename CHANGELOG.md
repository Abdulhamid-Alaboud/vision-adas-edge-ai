# Changelog

All notable changes to **VISION ADAS** are documented in this file.

## [v1.0.0] — 2026-11-19 (Public Demo)

### Added
- **On-Device Perception Engine (`YoloOnnxEngine`):** Deployed `YOLOv11n-ADAS-v2` FP32 ONNX (`10.12 MiB`) via ONNX Runtime Android `1.18.0` (`34.58 ms` mean Colab CPU latency, `95.82%` unseen test mAP@50).
- **Hybrid 20-Class Verification Pipeline:**
  - `SpeedSignVerifier`: Google ML Kit on-device OCR extending 6 trained speed limits to 6 additional zero-shot speed limits (`20, 70, 90, 110, 120, 140 km/h`).
  - `AdasSignVerifier`: Bayesian HSV and edge consistency checks to reduce dusk and glare false alarms.
  - `HazardEvaluator`: 2-of-3 frame temporal consensus filter.
- **Android Application (`android-app/`):** 7 Jetpack Compose Material 3 screens (`Auth`, `Dashboard/HUD`, `Settings`, `History`, `Analytics`, `Admin Dashboard`) with Role-Based Access Control (`Guest`, `Driver`, `Admin`), local SQLite v4 storage, 40 bilingual AR/EN SoundPool audio alerts, and strictly opt-in Firebase RTDB telemetry (`CLOUD_BASE_URL` externalized via `local.properties`).
- **Multi-Model Benchmark & Training Notebooks (`training/`):** Reproducible training and evaluation notebooks for `YOLOv11n-ADAS-v2`, `YOLO26n`, `D-FINE-N`, and `RT-DETRv2-S`.
- **Release Assets (`v1.0.0`):** Compiled `app-debug.apk`, leak-free 8,842-image dataset archive (`Offline.Arabic.ADAS.yolov11.zip`), and `SHA256SUMS.txt`.
