package dev.akurbanoff.core

interface DictorModule {
    operator fun <Type> get(type: Class<Type>): DictorFactory<Type>?
}