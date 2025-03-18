package dev.akurbanoff.dictor

import javax.inject.Singleton

class DictorReflectiveModule: DictorModule {
    val UNINIT = Any()

    fun <Type> singleton(factory: DictorFactory<Type>): DictorFactory<Type> {
        var instance: Any? = UNINIT
        return DictorFactory { linker ->
            if(instance == UNINIT) {
                instance = factory.get(linker)
            }
            instance as Type
        }
    }

    override fun <Type> get(type: Class<Type>): DictorFactory<Type> {
        val reflectiveFactory = DictorReflectiveFactory(type)

        return if(type.isAnnotationPresent(Singleton::class.java)) {
            singleton(reflectiveFactory)
        } else {
            reflectiveFactory
        }
    }
}