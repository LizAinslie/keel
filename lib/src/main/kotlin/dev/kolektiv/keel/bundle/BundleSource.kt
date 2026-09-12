package dev.kolektiv.keel.bundle

import dev.kolektiv.keel.KeelJson
import dev.kolektiv.keel.manifest.KeelManifest
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import kotlin.streams.asSequence

internal data class EntryMeta(
    val size: Long,
    val crc: Long,
    val lastModified: Long,
)

internal sealed interface BundleSource : AutoCloseable {
    val description: String
    val origin: BundleOrigin
    fun readManifest(): KeelManifest
    fun readManifestBytes(): ByteArray
    fun index(): Set<String>
    fun openEntry(path: String): InputStream
    fun openArchive(): InputStream
    fun entryMeta(path: String): EntryMeta
}

internal class DirectoryBundleSource(private val dir: Path) : BundleSource {
    private val root: Path = dir.toAbsolutePath().normalize()

    init {
        require(Files.isDirectory(root)) { "bundle directory does not exist: $root" }
    }

    override val description: String get() = root.toString()
    override val origin: BundleOrigin get() = BundleOrigin.Directory(root)

    override fun readManifest(): KeelManifest {
        val file = root.resolve("manifest.json")
        if (!Files.isRegularFile(file)) throw MissingBundleManifestException(description)
        return KeelJson.codec.decodeFromString(KeelManifest.serializer(), Files.readString(file))
    }

    override fun readManifestBytes(): ByteArray {
        val file = root.resolve("manifest.json")
        if (!Files.isRegularFile(file)) throw MissingBundleManifestException(description)
        return Files.readAllBytes(file)
    }

    override fun index(): Set<String> {
        if (!Files.isDirectory(root)) return emptySet()
        Files.walk(root).use { walk ->
            return walk.asSequence()
                .filter { Files.isRegularFile(it) }
                .map { root.relativize(it).toString().replace('\\', '/') }
                .mapNotNull { normalizeEntryPath(it) }
                .toSet()
        }
    }

    override fun openEntry(path: String): InputStream {
        val normalized = requireEntryPath(path)
        val resolved = root.resolve(normalized).normalize()
        if (!resolved.startsWith(root)) throw UnsafeBundleEntryException(path)
        if (!Files.isRegularFile(resolved)) throw NoSuchElementException("missing entry '$normalized'")
        return Files.newInputStream(resolved)
    }

    override fun openArchive(): InputStream = zipIndexed(this)

    override fun entryMeta(path: String): EntryMeta {
        val normalized = requireEntryPath(path)
        val resolved = root.resolve(normalized).normalize()
        if (!resolved.startsWith(root)) throw UnsafeBundleEntryException(path)
        if (!Files.isRegularFile(resolved)) throw NoSuchElementException("missing entry '$normalized'")
        return EntryMeta(
            size = Files.size(resolved),
            crc = 0L,
            lastModified = Files.getLastModifiedTime(resolved).toMillis(),
        )
    }

    override fun close() = Unit
}

internal class ZipBundleSource(
    private val file: Path,
    override val origin: BundleOrigin,
    private val deleteFileOnClose: Boolean = false,
) : BundleSource {
    private val lock = Any()
    private var zip: ZipFile? = null

    init {
        require(Files.isRegularFile(file)) { "bundle file does not exist: $file" }
    }

    override val description: String get() = file.toString()

    private fun zipFile(): ZipFile = synchronized(lock) {
        zip ?: ZipFile(file.toFile()).also { zip = it }
    }

    override fun readManifest(): KeelManifest {
        val zip = zipFile()
        val entry = zip.getEntry("manifest.json")
            ?: throw MissingBundleManifestException(description)
        return zip.getInputStream(entry).use { stream ->
            KeelJson.codec.decodeFromString(
                KeelManifest.serializer(),
                stream.readBytes().decodeToString(),
            )
        }
    }

    override fun readManifestBytes(): ByteArray {
        val zip = zipFile()
        val entry = zip.getEntry("manifest.json")
            ?: throw MissingBundleManifestException(description)
        return zip.getInputStream(entry).use { it.readBytes() }
    }

    override fun index(): Set<String> {
        val zip = zipFile()
        return zip.entries().asSequence()
            .filter { !it.isDirectory }
            .mapNotNull { normalizeEntryPath(it.name) }
            .toSet()
    }

    override fun openEntry(path: String): InputStream {
        val normalized = requireEntryPath(path)
        val zip = zipFile()
        val entry = zip.getEntry(normalized)
        if (entry == null || entry.isDirectory) {
            throw NoSuchElementException("missing entry '$normalized'")
        }
        return zip.getInputStream(entry)
    }

    override fun entryMeta(path: String): EntryMeta {
        val normalized = requireEntryPath(path)
        val entry = zipFile().getEntry(normalized)
        if (entry == null || entry.isDirectory) {
            throw NoSuchElementException("missing entry '$normalized'")
        }
        val modified = runCatching { entry.lastModifiedTime.toMillis() }.getOrDefault(0L)
        return EntryMeta(size = entry.size, crc = entry.crc, lastModified = modified)
    }

    override fun openArchive(): InputStream = Files.newInputStream(file)

    override fun close() {
        synchronized(lock) {
            zip?.close()
            zip = null
        }
        if (deleteFileOnClose) {
            Files.deleteIfExists(file)
        }
    }
}

private fun zipIndexed(source: BundleSource): InputStream {
    val bytes = ByteArrayOutputStream()
    ZipOutputStream(bytes).use { zos ->
        for (relative in source.index().sorted()) {
            zos.putNextEntry(ZipEntry(relative))
            source.openEntry(relative).use { it.copyTo(zos) }
            zos.closeEntry()
        }
    }
    return ByteArrayInputStream(bytes.toByteArray())
}
