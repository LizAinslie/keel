package dev.kolektiv.keel

/**
 * Marks a `@Serializable` payload that Typegen should emit.
 *
 * When [value] is a page id (`harbor.home`), the type is also entered in the
 * generated pages map. Leave it empty for action I/O and nested data.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@MustBeDocumented
annotation class KeelType(val value: String = "")

/**
 * Marks a host function as a server action. The function must take **one**
 * `@Serializable` value parameter and return one `@Serializable` value.
 * It may be an extension on `ApplicationCall` so cookies/session are `this`.
 *
 * [value] is the action id packs POST (`harbor.setName`). Empty uses the
 * function name.
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
@MustBeDocumented
annotation class KeelAction(val value: String = "")
