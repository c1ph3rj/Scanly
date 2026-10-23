"""Builds Scanly's slim OpenCV AAR: OpenCV 5.0.0 with only the modules the app uses.

It drives OpenCV's own Android scripts (platforms/android/build_sdk.py and
build_java_shared_aar.py, which produce the official org.opencv:opencv AAR) with the same NDK
(27.2.12479018) and CMake (3.22.1) as the official 5.0.0.1 build. Only the module list and the
image codecs differ; TBB, KleidiCV (arm64), carotene, ITT, CPU baseline/dispatch and -O3 stay at
OpenCV's Android defaults, so the library computes the same results at the same speed.

Modules: core, imgproc, geometry (imgproc requires it; the app calls Geometry.getRotationMatrix2D),
flann (geometry requires it), java (bindings), and imgcodecs and videoio: OpenCV's Android Java
classes always include org.opencv.android.Utils (imports Imgcodecs) and NativeCameraView (imports
videoio), so they do not compile without both. Every codec inside imgcodecs is disabled, since the
app never encodes or decodes images through OpenCV; videoio on Android is only the system
MediaNDK backend.

Runs on Linux, macOS or Windows (Python 3.8+, JDK 17 in JAVA_HOME, internet for the
OpenCV source, KleidiCV, TBB and Gradle). Example, from the repo root:

    python scripts/opencv/build_slim_opencv.py --sdk "%LOCALAPPDATA%/Android/Sdk" --work C:/ocvb

Output: third_party/opencv/opencv-5.0.0-slim.aar, plus build-info-<abi>.txt (OpenCV's
getBuildInformation() for each ABI) and the build logs.
"""
import argparse
import hashlib
import os
import re
import shutil
import subprocess
import sys
import tarfile
import urllib.error
import urllib.request
from pathlib import Path

OPENCV_VERSION = "5.0.0"
NDK_VERSION = "27.2.12479018"
CMAKE_VERSION = "3.22.1"
MODULES = "core,flann,geometry,imgproc,imgcodecs,videoio,java"
DISABLED_FEATURES = [
    "JPEG", "PNG", "TIFF", "WEBP", "OPENJPEG", "JASPER", "OPENEXR", "AVIF",
    "IMGCODEC_GIF", "IMGCODEC_HDR", "IMGCODEC_SUNRASTER", "IMGCODEC_PXM", "IMGCODEC_PFM",
    "PROTOBUF",
]
ABIS = ["arm64-v8a", "armeabi-v7a"]
REPO_ROOT = Path(__file__).resolve().parents[2]
SCRIPT_DIR = Path(__file__).resolve().parent


def run(cmd, cwd, log_path, env):
    print("+", " ".join(str(c) for c in cmd), f"> {log_path}", flush=True)
    # Output goes straight to the file, not through a pipe: a Gradle daemon started by the build
    # inherits the handle, and a pipe would then never reach end-of-file.
    with open(log_path, "w", encoding="utf-8", errors="replace") as log:
        code = subprocess.call([str(c) for c in cmd], cwd=cwd, env=env, stdout=log, stderr=subprocess.STDOUT)
    if code != 0:
        tail = Path(log_path).read_text(encoding="utf-8", errors="replace").splitlines()[-40:]
        sys.exit("\n".join(tail) + f"\nFailed ({code}): see {log_path}")


def check_jdk(env):
    """OpenCV's Gradle projects use Kotlin 1.8.20, which only accepts JVM targets up to 17."""
    java = Path(env["JAVA_HOME"]) / "bin" / "java" if env.get("JAVA_HOME") else "java"
    version = subprocess.run([str(java), "-version"], capture_output=True, text=True, env=env).stderr
    if '"17.' not in version:
        sys.exit(f"JDK 17 is required (as in OpenCV's CI); set JAVA_HOME. Found: {version.splitlines()[:1]}")


def fetch_source(work):
    source = work / f"opencv-{OPENCV_VERSION}"
    if source.exists():
        return source
    archive = work / f"opencv-{OPENCV_VERSION}.tar.gz"
    url = f"https://github.com/opencv/opencv/archive/refs/tags/{OPENCV_VERSION}.tar.gz"
    print(f"Downloading {url}", flush=True)
    urllib.request.urlretrieve(url, archive)
    with tarfile.open(archive) as tar:
        tar.extractall(work, filter="data") if sys.version_info >= (3, 12) else tar.extractall(work)
    return source


def prefetch_kleidicv(source):
    """Puts KleidiCV (OpenCV's arm64 acceleration HAL) into OpenCV's download cache.

    CMake 3.22's own downloader fails the TLS handshake with gitlab.arm.com on some hosts; without
    the archive build_sdk.py stops, because the official arm64 build always has KleidiCV.
    """
    recipe = (source / "hal/kleidicv/kleidicv.cmake").read_text(encoding="utf-8")
    commit = re.search(r'KLEIDICV_SRC_COMMIT "([^"]+)"', recipe).group(1)
    md5 = re.search(r'KLEIDICV_SRC_HASH "([0-9a-f]{32})"', recipe).group(1)
    target = source / ".cache" / "kleidicv" / f"{md5}-kleidicv-{commit}.tar.gz"
    if target.exists() and hashlib.md5(target.read_bytes()).hexdigest() == md5:
        return
    url = f"https://gitlab.arm.com/kleidi/kleidicv/-/archive/{commit}/kleidicv-{commit}.tar.gz"
    print(f"Downloading {url}", flush=True)
    try:
        data = urllib.request.urlopen(url).read()
    except urllib.error.URLError as error:
        # Python's certificate store can lack a root the OS trusts; curl uses the OS store.
        print(f"Python download failed ({error.reason}); retrying with curl", flush=True)
        data = subprocess.run(["curl", "-fsSL", url], check=True, capture_output=True).stdout
    if hashlib.md5(data).hexdigest() != md5:
        sys.exit(f"KleidiCV {commit} archive does not match OpenCV's pinned MD5 {md5}.")
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_bytes(data)


def build_info(library):
    """The getBuildInformation() text embedded in the library."""
    data = library.read_bytes()
    start = data.find(b"General configuration for OpenCV")
    return data[start:data.find(b"\0", start)].decode("utf-8", "replace")


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--sdk", required=True, help="Android SDK with ndk/%s and cmake/%s" % (NDK_VERSION, CMAKE_VERSION))
    parser.add_argument("--work", required=True, help="Scratch directory (keep the path short on Windows)")
    parser.add_argument("--out", default=str(REPO_ROOT / "third_party" / "opencv"))
    args = parser.parse_args()

    sdk = Path(args.sdk).resolve()
    ndk = sdk / "ndk" / NDK_VERSION
    cmake_home = sdk / "cmake" / CMAKE_VERSION
    for required in (ndk, cmake_home):
        if not required.is_dir():
            sys.exit(f"Missing {required}; install it with the SDK manager.")
    work = Path(args.work).resolve()
    out = Path(args.out).resolve()
    work.mkdir(parents=True, exist_ok=True)
    out.mkdir(parents=True, exist_ok=True)

    source = fetch_source(work)
    prefetch_kleidicv(source)
    build = work / "build"
    env = dict(os.environ)
    # The SDK's CMake and Ninja first on PATH, as in OpenCV's CI image.
    env["PATH"] = str(cmake_home / "bin") + os.pathsep + env["PATH"]
    env["ANDROID_HOME"] = env["ANDROID_SDK"] = str(sdk)
    env["ANDROID_NDK"] = env["ANDROID_NDK_HOME"] = str(ndk)
    env["GRADLE_OPTS"] = (env.get("GRADLE_OPTS", "") + " -Dorg.gradle.daemon=false").strip()
    check_jdk(env)

    disable_args = [arg for feature in DISABLED_FEATURES for arg in ("--disable", feature)]
    run(
        [sys.executable, source / "platforms/android/build_sdk.py",
         "--config", SCRIPT_DIR / "slim.config.py",
         "--sdk_path", sdk, "--ndk_path", ndk,
         "--modules_list", MODULES,
         "--no_samples_build", "--no_ccache",
         *disable_args, build, source],
        cwd=work, log_path=out / "build-sdk.log", env=env,
    )

    # build_java_shared_aar.py starts Gradle as "./gradlew", which Windows cannot execute; there
    # it is started through gradlew.bat instead. Nothing else about the packaging changes.
    aar_script = source / "platforms/android/build_java_shared_aar.py"
    if os.name == "nt":
        launcher = work / "build_java_shared_aar_windows.py"
        launcher.write_text(
            "import os, runpy, subprocess, sys\n"
            "_run = subprocess.run\n"
            "def run(cmd, *a, **kw):\n"
            "    if isinstance(cmd, list) and cmd and cmd[0] == './gradlew':\n"
            "        cmd = [os.path.abspath(os.path.join(kw.get('cwd', '.'), 'gradlew.bat'))] + cmd[1:]\n"
            "    return _run(cmd, *a, **kw)\n"
            "subprocess.run = run\n"
            f"sys.argv[0] = {str(aar_script)!r}\n"
            f"runpy.run_path({str(aar_script)!r}, run_name='__main__')\n",
            encoding="utf-8",
        )
        aar_script = launcher
    run(
        [sys.executable, aar_script,
         # Forward slashes: the script writes these into local.properties unescaped.
         f"--ndk_location={ndk.as_posix()}", f"--cmake_location={cmake_home.as_posix()}",
         build / "OpenCV-android-sdk"],
        cwd=work, log_path=out / "build-aar.log", env=env,
    )

    shutil.copyfile(work / "outputs" / f"opencv_java_shared_{OPENCV_VERSION}.aar",
                    out / f"opencv-{OPENCV_VERSION}-slim.aar")
    for abi in ABIS:
        library = build / "OpenCV-android-sdk/sdk/native/libs" / abi / "libopencv_java5.so"
        (out / f"build-info-{abi}.txt").write_text(build_info(library), encoding="utf-8")
    print(f"Built {out / f'opencv-{OPENCV_VERSION}-slim.aar'}")


if __name__ == "__main__":
    main()
