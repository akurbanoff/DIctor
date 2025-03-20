package dev.akurbanoff.dictor_processor

import com.google.devtools.ksp.KspExperimental
import com.google.devtools.ksp.isAnnotationPresent
import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSVisitorVoid
import com.google.devtools.ksp.validate
import javax.inject.Inject
import javax.inject.Singleton

class InjectProcessorProvider: SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor =
        InjectProcessor(environment.codeGenerator)
}

class InjectProcessor(
    val codeGenerator: CodeGenerator
): SymbolProcessor {
    override fun process(resolver: Resolver): List<KSAnnotated> {
        val annotatedSymbol = resolver.getSymbolsWithAnnotation(Inject::class.java.name)
        val unprocessedSymbols = annotatedSymbol.filter { !it.validate() }.toList()

        annotatedSymbol
            .filter {
                it is KSFunctionDeclaration && it.validate()
            }
            .forEach { it.accept(InjectConstructorVisitor(), Unit) }

        return unprocessedSymbols
    }

    inner class InjectConstructorVisitor: KSVisitorVoid() {
        @OptIn(KspExperimental::class)
        override fun visitFunctionDeclaration(function: KSFunctionDeclaration, data: Unit) {
            val injectedClass = function.parentDeclaration as KSClassDeclaration
            val injectedClassSimpleName = injectedClass.simpleName.asString()
            val packageName = injectedClass.containingFile!!.packageName.asString()
            val className = "${injectedClassSimpleName}_Factory"

            codeGenerator.createNewFile(
                Dependencies(true, function.containingFile!!), packageName, className
            ).use { stream ->
                val ktFile = stream.writer()
                ktFile.appendLine("package $packageName")
                ktFile.appendLine()
                ktFile.appendLine("import dev.akurbanoff.core.DictorFactory")
                ktFile.appendLine("import dev.akurbanoff.core.DictorComponent")

                if(injectedClass.isAnnotationPresent(Singleton::class)) {
                    ktFile.appendLine("import dev.akurbanoff.core.singleton")
                }

                if(function.parameters.isNotEmpty()) {
                    ktFile.appendLine("import dev.akurbanoff.core.get")
                }

                ktFile.appendLine()
                ktFile.appendLine("class $className : DictorFactory<$injectedClassSimpleName> {")

                val constructorInvocation = "${injectedClassSimpleName}(" + function.parameters.joinToString(", ") {
                    "dictorComponent.get()"
                } + ")"

                if (injectedClass.isAnnotationPresent(Singleton::class)) {
                    val linkerParameter = if(function.parameters.isNotEmpty()) "dictorComponent ->" else ""

                    ktFile.appendLine("\tprivate val singletonFactory = singleton { $linkerParameter")
                    ktFile.appendLine("\t\t$constructorInvocation")
                    ktFile.appendLine("\t}")
                    ktFile.appendLine()
                    ktFile.appendLine(
                        "\toverride fun get(dictorComponent: DictorComponent) = singletonFactory.get(dictorComponent)"
                    )
                } else {
                    ktFile.appendLine(
                        "\toverride fun get(dictorComponent: DictorComponent) = $constructorInvocation"
                    )
                }
                ktFile.appendLine("}")
                ktFile.close()
            }
        }
    }
}