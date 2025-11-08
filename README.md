# ARW Cleanup

A tiny CLI utility that deletes Sony RAW files (`.ARW`) in a given directory that do not have a matching JPEG file (`.JPG`) with the same base name. Helpful when you shoot RAW+JPG but want to keep only the JPGs you actually selected/kept.


## What It Does
- Expects exactly one argument: the path to your image directory.
- Scans the specified directory for all regular files; add `--recursive` to also process subdirectories.
- Identifies `.ARW` and `.JPG` files case‑insensitively.
- Collects the base names (e.g. `DSC01234` from `DSC01234.ARW`).
- Deletes every `.ARW` for which there is no `.JPG` with the same base name (case‑insensitive match) in the same directory.
- Prints progress: counts found, how many will be deleted, and any deletion errors.

Notes:
- Matching is purely by file name (without extension) – no metadata inspection.
- Only the specified directory is processed by default; use `--recursive` to include subfolders.
- In recursive mode, JPG/RAW matching is performed per directory: only a `.JPG` in the same directory protects a `.ARW` there.
- Both the file extensions and the base‑name comparison are case‑insensitive.
- Recursive scans can take longer on large directory trees.


## Safety First
Deletion is irreversible. Please:
1. Test on a copy first.
2. Keep a backup if unsure.
3. Read the console output before trusting large batch deletions.


## Project Structure (Overview)
- Entry point: `src/main/kotlin/Main.kt` (main class: `de.till1993.MainKt`).
- Build tool: Gradle (wrapper included).
- Native build: GraalVM Native Image via `org.graalvm.buildtools.native` plugin.


## Usage
Options:
- `--dry-run`, `-n`: Show which `.ARW` files would be deleted without actually deleting them.
- `--recursive`, `-r`: Process subdirectories recursively.
- `--help`, `-h`: Show help message.

Examples (Windows cmd):
- Delete unmatched RAWs (current directory only):
  - `arw_cleanup "C:\Users\User\Pictures\My Images"`
- Delete unmatched RAWs recursively:
  - `arw_cleanup --recursive "C:\Users\User\Pictures\My Images"`
- Preview only (no deletion):
  - `arw_cleanup --dry-run "D:\Photos\Session 01"`
- Preview only (no deletion), recursively:
  - `arw_cleanup -n -r "D:\Photos\Session 01"`

If you run from the JAR (see below) you also pass the directory path as the single argument.
If the argument count is wrong a short help banner is printed.


## Build & Run
Two variants: regular JVM (JAR) or native executable (faster startup, easier distribution).

### 1) JVM (JAR) Build
Requirements:
- JDK 17+ (compatible with the configured Kotlin version)

Build the JAR:
```bat
gradlew.bat build
```
Resulting file (default naming by Gradle/Kotlin plugin):
```
build\libs\arw_cleanup-1.0-SNAPSHOT.jar
```
Run it:
```bat
java -jar build\libs\arw_cleanup-1.0-SNAPSHOT.jar "C:\Path\To\Your\Images"
```

### 2) Native Executable (GraalVM Native Image)
Configured in `build.gradle.kts` with:
- `imageName = arw_cleanup`
- Main class `de.till1993.MainKt`

Default Gradle task: `nativeCompile`.

Requirements (Windows):
- GraalVM JDK 17 or 21 (latest LTS recommended)
- Native Image component installed (`gu install native-image`)
- `JAVA_HOME` (or `GRAALVM_HOME`) points to the GraalVM installation and `%JAVA_HOME%\bin` is in `PATH`.

Example setup:
```bat
:: Set for this session
set JAVA_HOME=C:\Program Files\GraalVM\jdk-21
set PATH=%JAVA_HOME%\bin;%PATH%

:: Ensure native-image is installed
"%JAVA_HOME%\bin\gu.exe" install native-image
```
Build the native image:
```bat
gradlew.bat nativeCompile
```
Result:
```
build\native\nativeCompile\arw_cleanup.exe
```
Run it:
```bat
build\native\nativeCompile\arw_cleanup.exe "C:\Path\To\Your\Images"
```


## Planned / Proposed Features (Not Implemented Yet)
The following feature ideas are planned or considered for future versions:
- Exclude / Include patterns: `--exclude "*.hdr"` (and potential `--include <pattern>`) to fine-tune which files are considered.
- Report output: `--report report.json` to generate a JSON or CSV summary of actions (e.g. `--report report.csv`).
- Extended statistics / progress display: `--stats` for a final structured summary (scan time, deleted counts, per-directory breakdown).
- File logging: `--log arw_cleanup.log` to persist the output to a log file in addition to console.
- Parallel processing: `--parallel` to speed up very large directory trees (careful with I/O contention).
- Interactive confirmation: `--interactive` prompts before deleting each file (or per batch).
- Configuration file: automatic loading of defaults from `.arwcleanup.yml` in the working directory or user home.
- Quarantine mode: `--quarantine <path>` moves unmatched RAW files into a quarantine folder instead of deleting.
- Quiet / Verbose modes: `--quiet` suppresses non-essential output; `--verbose` adds extra diagnostic details.
- Support for additional RAW formats besides `.ARW`: e.g. `.CR2`, `.NEF`, `.RW2`, `.ORF`, `.DNG` (matching logic extended accordingly).

(If you need one of these sooner, contributions or issue requests are welcome.)

## Troubleshooting
| Symptom | Cause | Fix |
|---------|-------|-----|
| `JAVA_HOME is not set` | Env var missing | `set JAVA_HOME=...` (JDK or GraalVM) |
| `native-image not found` | Component not installed | `gu install native-image` using GraalVM |
| No files deleted | No unmatched `.ARW` found or wrong directory | Verify directory & file extensions |
| Access denied | Insufficient permissions | Use a writable folder / elevated shell |

## License
This project is licensed under the GNU General Public License v3.0 (GPL-3.0). See the LICENSE file for the full license text.

---
Feel free to open an issue or contribute improvements.
