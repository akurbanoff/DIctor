package dev.akurbanoff.dictor

import org.junit.Test
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

class Test {
    @Test
    fun app() {
        val factoryHolderModule = FactoryHolderModule()
        val reflectiveModule = DictorReflectiveModule()

        factoryHolderModule.install {
            Engine()
        }

        factoryHolderModule.install {
            Wheels()
        }

        factoryHolderModule.installSingleton {
            Factory()
        }

        factoryHolderModule.install {
            Info(get())
        }

        factoryHolderModule.install {
            Auto(get(), get(), get())
        }

        val component = DictorComponent(reflectiveModule)
        val auto = component.get<Auto>()
        val factory = component.get<Factory>()
        val factory1 = component.get<Factory>()

        println(auto.getName())
        println(factory.a)
        println(factory1.a)
    }

    class Auto @Inject constructor(
        private val engine: Engine,
        private val wheels: Wheels,
        private val info: Info
    ) {
        fun getName(): String {
            return "Auto"
        }
    }

    class Engine @Inject constructor()
    class Wheels @Inject constructor()

    @Singleton
    class Factory @Inject constructor() {
        var a = 1
        init {
            a = Random.nextInt()
        }
    }

    class Info @Inject constructor(private val factory: Factory)
}