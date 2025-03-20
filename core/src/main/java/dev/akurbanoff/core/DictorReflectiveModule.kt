package dev.akurbanoff.core

import javax.inject.Singleton

class DictorReflectiveModule: DictorModule {
    override fun <Type> get(type: Class<Type>): DictorFactory<Type> {
        val reflectiveFactory = DictorReflectiveFactory(type)

        return if(type.isAnnotationPresent(Singleton::class.java)) {
            singleton(reflectiveFactory)
        } else {
            reflectiveFactory
        }
    }
}