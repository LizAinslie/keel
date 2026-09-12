package dev.kolektiv.keel.bundle

import dev.kolektiv.keel.Keel
import dev.kolektiv.keel.manifest.KeelManifest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
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

    override fun close() {
        if (closed.compareAndSet(false, true)) {
            source.close()
        }
    }

    companion object {
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
            return FrontendBundle(ZipBundleSource(temp, deleteFileOnClose = true))
        }

        fun fromFile(file: Path): FrontendBundle {
            val path = file.toAbsolutePath().normalize()
            require(Files.isRegularFile(path)) { "bundle file does not exist: $path" }
            return FrontendBundle(ZipBundleSource(path))
        }

        fun fromDirectory(dir: Path): FrontendBundle {
            val path = dir.toAbsolutePath().normalize()
            require(Files.isDirectory(path)) { "bundle directory does not exist: $path" }
            return FrontendBundle(DirectoryBundleSource(path))
        }
    }
}
