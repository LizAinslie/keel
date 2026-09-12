package dev.kolektiv.keel.bundle

import java.nio.file.Path

/**
 * Where a [FrontendBundle] was loaded from, enough to open a fresh instance
 * of the same pack. File and directory origins can be watched and reopened
 * by the host; resource origins can be reopened but not fingerprinted.
 */
sealed interface BundleOrigin {
    val description: String

    data class File(val path: Path) : BundleOrigin {
        override val description: String get() = path.toString()
    }

    data class Directory(val path: Path) : BundleOrigin {
        override val description: String get() = path.toString()
    }

    data class Resource(val name: String, val classLoader: ClassLoader) : BundleOrigin {
        override val description: String get() = "resource:$name"
    }
}
