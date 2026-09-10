package dev.kolektiv.keel.typegen

import dev.kolektiv.keel.KeelType
import dev.kolektiv.keel.action.ActionBinding
import dev.kolektiv.keel.action.ActionDiscovery
import dev.kolektiv.keel.page.PageBinding
import kotlinx.serialization.KSerializer
import kotlinx.serialization.serializer
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import kotlin.reflect.full.starProjectedType

/**
 * Pages, actions, and extra serializers Typegen walks. Built from `@KeelType`
 * / `@KeelAction` on the compile output, or from the runtime registries.
 */
class TypegenContract(
    val pages: Collection<PageBinding>,
    val actions: Collection<ActionBinding>,
    val types: Collection<KSerializer<*>> = emptyList(),
)

object ClasspathContract {
    /**
     * Load `@KeelType` / `@KeelAction` from directory entries on the JVM
     * classpath (Gradle `JavaExec` runtime classpath). Jars are skipped so
     * library classes are not scanned.
     */
    fun scan(
        packages: List<String> = emptyList(),
        classLoader: ClassLoader = Thread.currentThread().contextClassLoader
            ?: ClasspathContract::class.java.classLoader,
    ): TypegenContract {
        val names = classNames(classPathDirectories(), packages)
        val pages = ArrayList<PageBinding>()
        val actions = ArrayList<ActionBinding>()
        val types = ArrayList<KSerializer<*>>()
        val seenActions = HashSet<String>()
        val seenPages = HashSet<String>()
        for (name in names) {
            val clazz = runCatching { Class.forName(name, false, classLoader) }.getOrNull() ?: continue
            val keelType = clazz.getAnnotation(KeelType::class.java)
            if (keelType != null) {
                val serializer = serializerFor(clazz) ?: continue
                types.add(serializer)
                val pageId = keelType.value
                if (pageId.isNotBlank() && seenPages.add(pageId)) {
                    pages.add(PageBinding(pageId, typegenPath(pageId), serializer))
                }
            }
            for (action in ActionDiscovery.discover(clazz)) {
                if (seenActions.add(action.id)) actions.add(action.binding)
            }
        }
        return TypegenContract(
            pages.sortedBy { it.id },
            actions.sortedBy { it.id },
            types,
        )
    }

    private fun serializerFor(clazz: Class<*>): KSerializer<*>? =
        runCatching { serializer(clazz.kotlin.starProjectedType) }.getOrNull()

    private fun typegenPath(id: String): String = "/__keel-typegen/${id.replace('.', '/')}"

    private fun classPathDirectories(): List<Path> {
        val raw = System.getProperty("java.class.path") ?: return emptyList()
        return raw.split(File.pathSeparator).map { Path.of(it) }.filter { Files.isDirectory(it) }
    }

    private fun classNames(dirs: List<Path>, packages: List<String>): List<String> {
        val prefixes = packages.map { it.replace('.', '/') + "/" }
        val names = ArrayList<String>()
        for (dir in dirs) {
            if (!Files.isDirectory(dir)) continue
            Files.walk(dir).use { stream ->
                stream.forEach { file ->
                    if (!Files.isRegularFile(file) || !file.fileName.toString().endsWith(".class")) return@forEach
                    val rel = dir.relativize(file).toString().replace('\\', '/')
                    if (rel.contains('$')) return@forEach
                    if (prefixes.isNotEmpty() && prefixes.none { rel.startsWith(it) }) return@forEach
                    names.add(rel.removeSuffix(".class").replace('/', '.'))
                }
            }
        }
        return names
    }
}
