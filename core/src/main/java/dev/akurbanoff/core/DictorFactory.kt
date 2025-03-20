package dev.akurbanoff.core

fun interface DictorFactory<Type> {
    fun get(dictorComponent: DictorComponent): Type
}

val UNINITIALIZED = Any()

fun <T> singleton(factory: DictorFactory<T>): DictorFactory<T> {
    var instance: Any? = UNINITIALIZED
    return DictorFactory { linker ->
        if (instance === UNINITIALIZED) {
            instance = factory.get(linker)
        }
        instance as T
    }
}