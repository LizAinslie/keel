package dev.kolektiv.keel.bundle

import dev.kolektiv.keel.Keel
import dev.kolektiv.keel.manifest.KeelManifest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicBoolean

/**
 * A frontend pack loaded from a `.feb` zip, a classpath resource, or an
 * exploded directory. Construction reads `manifest.json` and indexes entries.
 * Zip-slip paths are dropped from the index and rejected by [openEntry].
 */
class FrontendBundle private constructor(
    private val source: BundleSource,
) : AutoCloseable {
    val manifest: KeelManifest = source.readManifest()
    val id: String get() = manifest.id
    val version: String get() = manifest.version

    /** Where this bundle came from; [open] can build a fresh instance of it. */
    val origin: BundleOrigin get() = source.origin

    /**
     * SHA-256 hex over the manifest bytes and the sorted entry metadata
     * (name, size, crc for zip entries / mtime for directory files). Stable
     * for identical pack content; changes when a pack is rebuilt. Computed
     * lazily once per bundle.
     */
    val contentHash: String by lazy(LazyThreadSafetyMode.SYNCHRONIZED) { computeContentHash() }

    private val entries: Set<String> = source.index()
    private val closed = AtomicBoolean(false)

    /** Stream of the original `.feb` bytes (directory sources zip on the fly). */
    fun openArchive(): InputStream = source.openArchive()

    /** Lookup a page the pack implements. */
    suspend fun page(id: String): PageImplementation = withContext(Dispatchers.IO) {
        val entry = manifest.page(id) ?: throw UnknownPageInBundleException(id, this@FrontendBundle.id)
        PageImplementation(
            pageId = id,
            module = entry.module,
            css = entry.css,
            layout = entry.layout,
            head = entry.head,
        )
    }

    fun openEntry(path: String): InputStream {
        val normalized = requireEntryPath(path)
        if (normalized !in entries) throw NoSuchElementException("missing entry '$normalized'")
        return source.openEntry(normalized)
    }

    fun contains(path: String): Boolean {
        val normalized = normalizeEntryPath(path) ?: return false
        return normalized in entries
    }

    internal fun entryMeta(path: String): EntryMeta {
        val normalized = requireEntryPath(path)
        if (normalized !in entries) throw NoSuchElementException("missing entry '$normalized'")
        return source.entryMeta(normalized)
    }

    /** Strong ETag from CRC (zip) or size+mtime (directory). */
    fun etagFor(path: String): String {
        val meta = entryMeta(path)
        val tag = if (meta.crc > 0L) {
            "${java.lang.Long.toUnsignedString(meta.crc, 16)}-${meta.size}"
        } else {
            "${meta.size}-${meta.lastModified}"
        }
        return "\"$tag\""
    }

    private fun computeContentHash(): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(source.readManifestBytes())
        digest.update(0)
        for (name in entries.sorted()) {
            val meta = source.entryMeta(name)
            digest.update(name.toByteArray(Charsets.UTF_8))
            digest.update(0)
            digest.update(meta.size.toString().toByteArray(Charsets.UTF_8))
            digest.update(0)
            if (meta.crc != 0L) {
                digest.update('z'.code.toByte())
                digest.update(meta.crc.toString().toByteArray(Charsets.UTF_8))
            } else {
                digest.update('d'.code.toByte())
                digest.update(meta.lastModified.toString().toByteArray(Charsets.UTF_8))
            }
            digest.update(0)
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    override fun close() {
        if (closed.compareAndSet(false, true)) {
            source.close()
        }
    }

    companion object {
        /** Open the pack at [origin], the same way it was originally loaded. */
        fun open(origin: BundleOrigin): FrontendBundle = when (origin) {
            is BundleOrigin.File -> fromFile(origin.path)
            is BundleOrigin.Directory -> fromDirectory(origin.path)
            is BundleOrigin.Resource -> fromResource(origin.name, origin.classLoader)
        }

        fun fromResource(
            name: String,
            classLoader: ClassLoader = Thread.currentThread().contextClassLoader
                ?: FrontendBundle::class.java.classLoader,
        ): FrontendBundle {
            val resource = name.trimStart('/')
            val stream = classLoader.getResourceAsStream(resource)
                ?: throw MissingBundleResourceException(resource)
            val temp = Files.createTempFile("keel-bundle-", ".${Keel.BUNDLE_EXTENSION}")
            stream.use { Files.copy(it, temp, StandardCopyOption.REPLACE_EXISTING) }
            temp.toFile().deleteOnExit()
            return FrontendBundle(
                ZipBundleSource(
                    temp,
                    BundleOrigin.Resource(resource, classLoader),
                    deleteFileOnClose = true,
                ),
            )
        }

        fun fromFile(file: Path): FrontendBundle {
            val path = file.toAbsolutePath().normalize()
            require(Files.isRegularFile(path)) { "bundle file does not exist: $path" }
            return FrontendBundle(ZipBundleSource(path, BundleOrigin.File(path)))
        }

        fun fromDirectory(dir: Path): FrontendBundle {
            val path = dir.toAbsolutePath().normalize()
            require(Files.isDirectory(path)) { "bundle directory does not exist: $path" }
            return FrontendBundle(DirectoryBundleSource(path))
        }
    }
}
