package dev.akurbanoff.dictor

class FactoryHolderModule: DictorModule {
    private val factories = mutableMapOf<Class<out Any?>, DictorFactory<out Any?>>()
    val UNINIT = Any()

    override operator fun <Type> get(type: Class<Type>): DictorFactory<Type>? {
        return factories[type] as DictorFactory<Type>?
    }

    fun <Type> install(
        type: Class<Type>,
        factory: DictorFactory<Type>
    ) {
        factories[type] = factory
    }

    fun <Type> singleton(factory: DictorFactory<Type>): DictorFactory<Type> {
        var instance: Any? = UNINIT
        return DictorFactory { linker ->
            if(instance == UNINIT) {
                instance = factory.get(linker)
            }
            instance as Type
        }
    }
}

inline fun <reified Type> FactoryHolderModule.install(
    noinline factory: DictorComponent.() -> Type
) = install(Type::class.java, factory)

inline fun <reified Type> FactoryHolderModule.installSingleton(
    noinline factory: DictorComponent.() -> Type
) = install(Type::class.java, singleton(factory))