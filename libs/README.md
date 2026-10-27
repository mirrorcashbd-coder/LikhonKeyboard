# Installed libraries

Downloaded from Maven Central; `tools/build-apk-arm64.sh` dexes every `*.jar`
here into the APK.

| File | Used for |
| --- | --- |
| `okhttp-3.14.9.jar` | translation requests (timeouts, retries) |
| `okio-1.17.5.jar` | OkHttp's I/O layer |
| `gson-2.10.1.jar` | backup/restore JSON |
| `sherpa-onnx-2.0.1.jar` | offline speech engine (classes from the official AAR) |
| `kotlin-stdlib-2.1.21.jar` | runtime used by sherpa-onnx's Kotlin API |

`sherpa-onnx-2.0.1.jar` is the `classes.jar` inside
`sherpa-onnx-2.0.1.aar` from the k2-fsa GitHub release. The matching
`arm64-v8a` libraries live in `app/src/main/jniLibs/arm64-v8a/`; the build
script copies them into `lib/arm64-v8a/` inside the APK.

## Adding more

```bash
bash tools/add-lib.sh group:artifact:version
bash tools/build-apk-arm64.sh
```

Plain JARs are copied as is. AARs are unpacked and their `classes.jar` is kept;
resources inside an AAR need the aapt2 resource pipeline, and the script says so
when it sees them. AndroidX and Material Components are intentionally absent:
they need a theme rewrite and add several megabytes for widgets this keyboard
already implements itself.
