# Export / effects fixes (ZIP revision)

This file records the export/effects work included in the supplied Capscut-Pro-fixed ZIP.

## Root causes addressed
1. Video orientation: bitmap/OES texture orientation is normalized so exported frames remain upright.
2. Native engine wiring: CMake/externalNativeBuild is configured for the native GLES engine.
3. Native GLES safety: quad geometry, buffer state, alpha/blend handling, EGL-context ownership, layer IDs and matrix validation are covered by native tests.
4. Export pipeline: overlay texture targets, renderer/context lifetime and aspect-preserving frame sizing are handled explicitly.
5. Blank-frame protection: BlankFrameGuard detects suspicious initial encoder frames and can fall back/retry instead of silently producing a blank export.

## Verification
- Native GLES tests are included under native-tests.
- Kotlin/Android verification must run on a machine/CI runner with Android SDK:
  ./gradlew testDebugUnitTest
  ./gradlew assembleDebug
  ./gradlew assembleRelease
- Real-device MediaCodec/Muxer and performance validation remain device-dependent.

## Effects engine
The project contains a native GLSL effects layer plus Kotlin effect routing, including blur, bloom, vignette, color effects, distortion, RGB split, glitch, VHS/CRT, film grain, shake/zoom, rotate, edge/sharpen, chroma key, 3D transform, flash, halftone, light leak and lens flare.

The effect-chain packer has unit coverage for native IDs, parameter layout, keyframe limits and name routing.
