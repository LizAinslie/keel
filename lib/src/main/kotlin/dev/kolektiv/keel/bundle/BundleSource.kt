package dev.kolektiv.keel.bundle

import dev.kolektiv.keel.KeelJson
import dev.kolektiv.keel.manifest.KeelManifest
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.FilterInputStream
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import kotlin.streams.asSequence

internal sealed interface BundleSource : AutoCloseable {
    val description: String
    fun readManifest(): KeelManifest
    fun index(): Set<String>
    fun openEntry(path: String): InputStream
    fun openArchive(): InputStream
}

internal class DirectoryBundleSource(private val dir: Path) : BundleSource {
    private val root: Path = dir.toAbsolutePath().normalize()

    init {
        require(Files.isDirectory(root)) { "bundle directory does not exist: $root" }
    }

    override val description: String get() = root.toString()

    override fun readManifest(): KeelManifest {
        val file = root.resolve("manifest.json")
        if (!Files.isRegularFile(file)) throw MissingBundleManifestException(description)
        return KeelJson.codec.decodeFromString(KeelManifest.serializer(), Files.readString(file))
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

    override fun close() = Unit
}

internal class ZipBundleSource(
    private val file: Path,
    private val deleteFileOnClose: Boolean = false,
) : BundleSource {
    init {
        require(Files.isRegularFile(file)) { "bundle file does not exist: $file" }
    }

    override val description: String get() = file.toString()

    override fun readManifest(): KeelManifest {
        ZipFile(file.toFile()).use { zip ->
            val entry = zip.getEntry("manifest.json")
                ?: throw MissingBundleManifestException(description)
            return zip.getInputStream(entry).use { stream ->
                KeelJson.codec.decodeFromString(
                    KeelManifest.serializer(),
                    stream.readBytes().decodeToString(),
                )
            }
        }
    }

    override fun index(): Set<String> {
        ZipFile(file.toFile()).use { zip ->
            return zip.entries().asSequence()
                .filter { !it.isDirectory }
                .mapNotNull { normalizeEntryPath(it.name) }
                .toSet()
        }
    }

    override fun openEntry(path: String): InputStream {
        val normalized = requireEntryPath(path)
        val zip = ZipFile(file.toFile())
        val entry = zip.getEntry(normalized)
        if (entry == null || entry.isDirectory) {
            zip.close()
            throw NoSuchElementException("missing entry '$normalized'")
        }
        val stream = zip.getInputStream(entry)
        return object : FilterInputStream(stream) {
            override fun close() {
                try {
                    super.close()
                } finally {
                    zip.close()
                }
            }
        }
    }

    override fun openArchive(): InputStream = Files.newInputStream(file)

    override fun close() {
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
