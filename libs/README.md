# Installed libraries

Downloaded from Maven Central; `tools/build-apk-arm64.sh` dexes every `*.jar`
here into the APK.

| File | Used for |
| --- | --- |
| `okhttp-3.14.9.jar` | translation requests (timeouts, retries) |
| `okio-1.17.5.jar` | OkHttp's I/O layer |
| `gson-2.10.1.jar` | backup/restore JSON |

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
