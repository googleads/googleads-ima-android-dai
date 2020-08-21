"""
Provides a build rule for building the AdvancedExample from Blaze.
"""

load("//java/com/google/ads/interactivemedia/v3/samples:build_defs.bzl", "build_sample_package")

COMMON_DEPS = [
    "//third_party/java/android_libs/exoplayer:exoplayer2_core",
    "//third_party/java/android_libs/exoplayer:exoplayer2_dash",
    "//third_party/java/android_libs/exoplayer:exoplayer2_ext_mediasession",
    "//third_party/java/android_libs/exoplayer:exoplayer2_hls",
    "//third_party/java/android_libs/exoplayer:exoplayer2_ui",
    "//third_party/java/android_libs/guava_jdk5:collect",
    "//third_party/java/androidx/annotation",
    "//third_party/java/android/android_sdk_linux/extras/android/compatibility/v4",
    "//third_party/java/androidx/appcompat",
    "//third_party/java/androidx/constraintlayout",
    "//java/com/google/android/gmscore/integ/client/base",
    "//java/com/google/android/gmscore/integ/client/cast/framework",
    "//java/com/google/android/gmscore/integ/client/cast",
]

DEBUG_DEPS = COMMON_DEPS + [
    "//java/com/google/ads/interactivemedia/v3:sdk_lib_debug",
]

COMPILED_DEPS = COMMON_DEPS + [
    "//java/com/google/ads/interactivemedia/v3:sdk_lib",
    "//java/com/google/android/gmscore/integ/client/ads_identifier",
]

def advanced_example_package():
    build_sample_package(
        name_prefix = "advanced",
        package_name = "com.google.ads.interactivemedia.v3.samples.videoplayerapp",
        srcs = native.glob([
            "AdvancedExample/app/src/main/java/com/google/ads/interactivemedia/v3/samples/samplevideoplayer/*.java",
            "AdvancedExample/app/src/main/java/com/google/ads/interactivemedia/v3/samples/videoplayerapp/*.java",
        ]),
        compiled_deps = COMPILED_DEPS,
        debug_deps = COMPILED_DEPS,  # We can't use real debug deps here due to collisions between AdShield and GMS Core dependencies.
        manifest = "AdvancedExample/app/src/main/AndroidManifest.xml",
        resources = native.glob(["AdvancedExample/app/src/main/res/**"]),
    )
