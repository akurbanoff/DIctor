package dev.akurbanoff.core

import java.lang.reflect.Constructor
import javax.inject.Inject

class DictorReflectiveFactory<Type>(
    requestedType: Class<Type>
): DictorFactory<Type> {
    private val injectConstructor = requestedType.constructors.single {
        it.isAnnotationPresent(Inject::class.java)
    } as Constructor<Type>

    override fun get(dictorComponent: DictorComponent): Type {
        val params = injectConstructor.parameterTypes.map {
            dictorComponent[it]
        }.toTypedArray()

        return injectConstructor.newInstance(*params)
    }

}