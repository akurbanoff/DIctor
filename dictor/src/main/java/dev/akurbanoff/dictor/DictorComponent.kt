package dev.akurbanoff.dictor

class DictorComponent(
    private val modules: List<DictorModule>
) {

    constructor(vararg modules: DictorModule) : this(modules.asList())

    private val factoryHolder = FactoryHolderModule()

    operator fun <T> get(requestedType: Class<T>): T {
        val factory = factoryHolder[requestedType] ?: modules
            .firstNotNullOf { module -> module[requestedType] }
            .also { factory ->
                factoryHolder.install(requestedType, factory)
            }
        return factory.get(this)
    }
}

inline fun <reified T> DictorComponent.get() = get(T::class.java)