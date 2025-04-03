package dev.akurbanoff.core

import kotlin.reflect.KClass

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
annotation class Component(
    val modules: Array<KClass<*>> = []
)

fun <T> componentSingleton(factory: () -> T): () -> T {
    var instance: Any? = UNINITIALIZED
    return {
        if (instance === UNINITIALIZED) {
            instance = factory()
        }
        instance as T
    }
}
