package dev.kolektiv.keel.action

import dev.kolektiv.keel.KeelAction
import kotlinx.serialization.KSerializer
import kotlinx.serialization.serializer
import java.lang.reflect.InvocationTargetException
import kotlin.reflect.KFunction
import kotlin.reflect.KParameter
import kotlin.reflect.full.callSuspendBy
import kotlin.reflect.full.extensionReceiverParameter
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.instanceParameter
import kotlin.reflect.jvm.kotlinFunction

/**
 * A `@KeelAction` function the host can run. [call] is the Kotlin function;
 * the engine deserializes [input], invokes, and serializes [output].
 */
class DiscoveredAction(
    val id: String,
    val input: KSerializer<*>,
    val output: KSerializer<*>,
    val function: KFunction<*>,
    val instance: Any?,
) {
    val binding: ActionBinding get() = ActionBinding(id, input, output)

    suspend fun invoke(input: Any, extension: Any? = null): Any {
        val args = HashMap<KParameter, Any?>()
        function.instanceParameter?.let { parameter ->
            args[parameter] = instance ?: error("@KeelAction '${function.name}' needs a host instance")
        }
        function.extensionReceiverParameter?.let { parameter ->
            args[parameter] = extension
                ?: error("@KeelAction '${function.name}' needs an extension receiver")
        }
        args[valueParameter()] = input
        val result = try {
            if (function.isSuspend) {
                function.callSuspendBy(args)
            } else {
                function.callBy(args)
            }
        } catch (error: InvocationTargetException) {
            throw error.targetException ?: error
        }
        return result ?: error("@KeelAction '$id' returned null")
    }

    private fun valueParameter(): KParameter {
        val values = function.parameters.filter { it.kind == KParameter.Kind.VALUE }
        require(values.size == 1) {
            "@KeelAction '${function.name}' must take exactly one value parameter, found ${values.size}"
        }
        return values.single()
    }
}

object ActionDiscovery {
    fun discover(host: Any): List<DiscoveredAction> {
        val instance = if (host is Class<*>) host.kotlin.objectInstance else host
        val kClass = when (host) {
            is Class<*> -> host.kotlin
            else -> host::class
        }
        return discover(kClass.java, instance)
    }

    fun discover(clazz: Class<*>, instance: Any? = clazz.kotlin.objectInstance): List<DiscoveredAction> {
        val seen = LinkedHashSet<KFunction<*>>()
        val out = ArrayList<DiscoveredAction>()
        for (method in clazz.declaredMethods) {
            val function = method.kotlinFunction ?: continue
            if (!seen.add(function)) continue
            val annotation = function.findAnnotation<KeelAction>() ?: continue
            out.add(bind(annotation, function, instance))
        }
        return out
    }

    fun registry(vararg hosts: Any): ActionRegistry {
        val registry = ActionRegistry()
        for (host in hosts) {
            for (action in discover(host)) {
                registry.register(action.binding)
            }
        }
        return registry
    }

    private fun bind(annotation: KeelAction, function: KFunction<*>, instance: Any?): DiscoveredAction {
        val values = function.parameters.filter { it.kind == KParameter.Kind.VALUE }
        require(values.size == 1) {
            "@KeelAction '${function.name}' must take exactly one value parameter"
        }
        val inputType = values.single().type
        val outputType = function.returnType
        require(!outputType.isMarkedNullable) { "@KeelAction '${function.name}' must return a non-null type" }
        val id = annotation.value.ifBlank { function.name }
        return DiscoveredAction(
            id = id,
            input = serializer(inputType),
            output = serializer(outputType),
            function = function,
            instance = instance,
        )
    }
}
