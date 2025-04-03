package dev.akurbanoff.core

class InjectProcessorModule : DictorModule {
    override fun <T> get(requestedType: Class<T>) = try {
        val factoryClass = Class.forName("${requestedType.name}_Factory")
        factoryClass.getDeclaredConstructor().newInstance()
    } catch (notFound: ClassNotFoundException) {
        null
    } as DictorFactory<T>?
}