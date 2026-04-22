package com.crosspaste.bootstrap

/**
 * JVM entrypoint. Runs [JvmSystemPropertiesOverride.apply] before any Compose/skiko class is
 * touched, then reflectively delegates to [com.crosspaste.CrossPaste.main].
 *
 * The reflective dispatch is deliberate: referencing `com.crosspaste.CrossPaste` directly here
 * would cause the JVM to load its companion-object `<clinit>` — which pulls in config, path, and
 * logging singletons — potentially before our system-property overrides take effect. Reflection
 * defers the class load until after [JvmSystemPropertiesOverride.apply] returns.
 */
object CrossPasteBootstrap {

    @JvmStatic
    fun main(args: Array<String>) {
        JvmSystemPropertiesOverride.apply()
        val clazz = Class.forName("com.crosspaste.CrossPaste")
        val mainMethod = clazz.getDeclaredMethod("main", Array<String>::class.java)
        mainMethod.invoke(null, args)
    }
}
