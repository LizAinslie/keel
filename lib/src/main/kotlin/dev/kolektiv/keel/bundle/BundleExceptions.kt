package dev.kolektiv.keel.bundle

import dev.kolektiv.keel.page.UnknownPageException

class UnknownPageInBundleException(
    id: String,
    val bundleId: String,
) : UnknownPageException(id, "unknown page '$id' in bundle '$bundleId'")

class MissingBundleManifestException(source: String) :
    IllegalStateException("missing manifest.json in $source")

class UnsafeBundleEntryException(path: String) :
    IllegalArgumentException("illegal bundle entry path: $path")

class MissingBundleResourceException(name: String) :
    IllegalStateException("bundle resource not found: $name")
