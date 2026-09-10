package dev.kolektiv.keel.bundle

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.net.URLClassLoader
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText

class FrontendBundleTest {

    @TempDir
    lateinit var temp: Path

    @Test
    fun `fromDirectory reads manifest and pages`() {
        val dir = writePack("harbor")
        FrontendBundle.fromDirectory(dir).use { bundle ->
            assertEquals("harbor", bundle.id)
            assertEquals("0.1.0", bundle.version)
            val page = runBlocking { bundle.page("home") }
            assertEquals("pages/home.js", page.module)
            assertEquals(listOf("assets/styles.css"), page.css)
            assertTrue(bundle.contains("bootstrap.js"))
            val etag = bundle.etagFor("bootstrap.js")
            assertTrue(etag.startsWith("\"") && etag.endsWith("\""))
            val bootstrap = bundle.openEntry("bootstrap.js").use { it.readBytes().decodeToString() }
            assertEquals("export {}", bootstrap)
            assertThrows(UnknownPageInBundleException::class.java) {
                runBlocking { bundle.page("missing") }
            }
        }
    }

    @Test
    fun `fromFile zip and fromResource share the same entries`() {
        val dir = writePack("harbor")
        val zip = temp.resolve("harbor.feb")
        zipDirectory(dir, zip)
        FrontendBundle.fromFile(zip).use { fromZip ->
            assertEquals("harbor", fromZip.id)
            val page = runBlocking { fromZip.page("home") }
            assertEquals("pages/home.js", page.module)
            val names = zipEntryNames(fromZip.openArchive())
            assertTrue("manifest.json" in names)
            assertEquals("manifest.json", names.first { it == "manifest.json" })
        }
        val loader = URLClassLoader(arrayOf(temp.toUri().toURL()), null)
        FrontendBundle.fromResource("harbor.feb", loader).use { fromResource ->
            assertEquals("harbor", fromResource.id)
            assertTrue(fromResource.contains("pages/home.js"))
            val page = runBlocking { fromResource.page("home") }
            assertEquals("pages/home.js", page.module)
        }
    }

    @Test
    fun `zip-slip entries are rejected`() {
        val zip = temp.resolve("evil.feb")
        ZipOutputStream(Files.newOutputStream(zip)).use { zos ->
            zos.putNextEntry(ZipEntry("manifest.json"))
            zos.write(manifestJson("harbor").toByteArray())
            zos.closeEntry()
            zos.putNextEntry(ZipEntry("../evil.js"))
            zos.write("evil".toByteArray())
            zos.closeEntry()
            zos.putNextEntry(ZipEntry("pages/../../outside.js"))
            zos.write("nope".toByteArray())
            zos.closeEntry()
        }
        FrontendBundle.fromFile(zip).use { bundle ->
            assertFalse(bundle.contains("../evil.js"))
            assertFalse(bundle.contains("pages/../../outside.js"))
            assertThrows(UnsafeBundleEntryException::class.java) {
                bundle.openEntry("../evil.js")
            }
            assertThrows(UnsafeBundleEntryException::class.java) {
                bundle.openEntry("pages/../../outside.js")
            }
        }
    }

    @Test
    fun `directory traversal is rejected`() {
        val dir = writePack("harbor")
        val secret = temp.resolve("secret.txt")
        secret.writeText("classified")
        FrontendBundle.fromDirectory(dir).use { bundle ->
            assertThrows(UnsafeBundleEntryException::class.java) {
                bundle.openEntry("../secret.txt")
            }
            assertFalse(bundle.contains("../secret.txt"))
        }
    }

    @Test
    fun `missing manifest fails at construction`() {
        val dir = temp.resolve("empty").createDirectories()
        assertThrows(MissingBundleManifestException::class.java) {
            FrontendBundle.fromDirectory(dir)
        }
    }

    @Test
    fun `directory openArchive has manifest json at zip root`() {
        val dir = writePack("harbor")
        FrontendBundle.fromDirectory(dir).use { bundle ->
            val names = zipEntryNames(bundle.openArchive())
            assertTrue("manifest.json" in names)
            assertTrue(names.none { it.startsWith("/") })
        }
    }

    private fun writePack(id: String): Path {
        val dir = temp.resolve(id).createDirectories()
        dir.resolve("manifest.json").writeText(manifestJson(id))
        dir.resolve("bootstrap.js").writeText("export {}")
        dir.resolve("pages").createDirectories()
        dir.resolve("pages/home.js").writeText("export async function mount() {}")
        dir.resolve("assets").createDirectories()
        dir.resolve("assets/styles.css").writeText("body{}")
        return dir
    }

    private fun manifestJson(id: String): String = """
        {
          "format": "keel/1",
          "id": "$id",
          "version": "0.1.0",
          "framework": "svelte",
          "host": "#__keel_root",
          "pages": {
            "home": { "module": "pages/home.js", "css": ["assets/styles.css"] }
          }
        }
    """.trimIndent()
}

internal fun zipDirectory(dir: Path, zip: Path) {
    ZipOutputStream(Files.newOutputStream(zip)).use { zos ->
        Files.walk(dir).use { walk ->
            walk.filter { Files.isRegularFile(it) }.forEach { file ->
                val name = dir.relativize(file).toString().replace('\\', '/')
                zos.putNextEntry(ZipEntry(name))
                Files.copy(file, zos)
                zos.closeEntry()
            }
        }
    }
}

internal fun zipEntryNames(stream: java.io.InputStream): List<String> {
    val names = mutableListOf<String>()
    ZipInputStream(stream).use { zis ->
        while (true) {
            val entry = zis.nextEntry ?: break
            names.add(entry.name)
            zis.closeEntry()
        }
    }
    return names
}
