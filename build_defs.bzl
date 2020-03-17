"""
Helper methods for generating build rules for Sample apps.
"""

load("//tools/build_defs/android:rules.bzl", "android_binary", "android_library")

def build_sample_package(name_prefix, srcs, package_name, manifest, resources, debug_deps, compiled_deps):
    android_library(
        name = name_prefix + "_lib_debug",
        srcs = srcs,
        custom_package = package_name,
        manifest = manifest,
        resource_files = resources,
        deps = debug_deps,
    )

    android_library(
        name = name_prefix + "_lib_compiled",
        srcs = srcs,
        custom_package = package_name,
        manifest = manifest,
        resource_files = resources,
        deps = compiled_deps,
    )

    android_binary(
        name = name_prefix + "_app",
        manifest = manifest,
        manifest_merger = "android",
        multidex = "native",
        visibility = ["//visibility:public"],
        deps = [
            ":" + name_prefix + "_lib_debug",
        ],
    )
