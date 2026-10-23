# OpenCV build_sdk.py config for Scanly's slim OpenCV: the two ABIs the app ships, with the same
# per-ABI settings as OpenCV 5.0.0's platforms/android/default.config.py (x86 and x86_64 dropped).
ABIs = [
    ABI("2", "armeabi-v7a", None, 21, cmake_vars=dict(ANDROID_ABI='armeabi-v7a with NEON')),
    ABI("3", "arm64-v8a",   None, 21, cmake_vars=dict(ANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES='ON')),
]
