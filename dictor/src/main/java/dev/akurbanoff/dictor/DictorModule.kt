package dev.akurbanoff.dictor

interface DictorModule {
    operator fun <Type> get(type: Class<Type>): DictorFactory<Type>?
}