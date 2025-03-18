package dev.akurbanoff.dictor

fun interface DictorFactory<Type> {
    fun get(dictorComponent: DictorComponent): Type
}