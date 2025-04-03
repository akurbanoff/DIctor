package dev.akurbanoff.dictor_processor

import com.google.devtools.ksp.KspExperimental
import com.google.devtools.ksp.getConstructors
import com.google.devtools.ksp.getDeclaredFunctions
import com.google.devtools.ksp.getDeclaredProperties
import com.google.devtools.ksp.isAnnotationPresent
import com.google.devtools.ksp.processing.*
import com.google.devtools.ksp.symbol.*
import com.google.devtools.ksp.validate
import dev.akurbanoff.core.Binds
import dev.akurbanoff.core.Component
import java.io.OutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.reflect.KClass

class ComponentProcessorProvider: SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor {
        return ComponentProcessor(environment.codeGenerator)
    }
}

data class EntryPoint(val property: KSPropertyDeclaration, val resolvedPropertyType: KSDeclaration)

data class ComponentFactory(
    val type: KSClassDeclaration,
    val constructorParameters: List<KSDeclaration>,
    val isSingleton: Boolean
)

class ComponentModel(
    val packageName: String,
    val imports: Set<String>,
    val className: String,
    val componentInterfaceName: String,
    val factories: List<ComponentFactory>,
    val binds: Map<KSDeclaration, KSDeclaration>,
    val entryPoints: List<EntryPoint>
)

class ComponentProcessor(
    private val codeGenerator: CodeGenerator
): SymbolProcessor {

    override fun process(resolver: Resolver): List<KSAnnotated> {
        val annotatedSymbol = resolver.getSymbolsWithAnnotation(Component::class.java.name)
        val unprocessedSymbols = annotatedSymbol.filter { !it.validate() }.toList()

        annotatedSymbol
            .filterIsInstance<KSClassDeclaration>()
            .filter { it.validate() && it.classKind == ClassKind.INTERFACE }
            .forEach { it.accept(ComponentVisitor(), Unit) }

        return unprocessedSymbols
    }

    inner class ComponentVisitor: KSVisitorVoid() {
        override fun visitClassDeclaration(classDeclaration: KSClassDeclaration, data: Unit) {
            val componentInterfaceName = classDeclaration.containingFile!!.packageName.asString()
            val packageName = classDeclaration.containingFile!!.packageName.asString()
            val className = "Generated$componentInterfaceName"

            val componentAnnotation = classDeclaration.annotations.single {
                it isInstance Component::class
            }

            val entryPoints = readEntryPoints(classDeclaration)

            val entryPointTypes = entryPoints.map { it.resolvedPropertyType }

            val binds = readBinds(componentAnnotation)

            val bindProvidedTypes = binds.values

            val factories = traverseDependencyGraph(entryPointTypes + bindProvidedTypes)

            val importDeclarations = entryPointTypes + bindProvidedTypes + factories.map { it.type }

            val actualImports = importDeclarations
                .filter { it.packageName != classDeclaration.packageName }
                .map { it.qualifiedName!!.asString() }.toSet() +
                    if(factories.any { it.isSingleton }) {
                        setOf("dev.akurbanoff.core.componentSingleton")
                    } else emptySet()

            val model = ComponentModel(
                packageName = packageName,
                imports = actualImports,
                className = className,
                componentInterfaceName = componentInterfaceName,
                factories = factories,
                binds = binds,
                entryPoints = entryPoints
            )

            codeGenerator.createNewFile(
                Dependencies(true, classDeclaration.containingFile!!), packageName, className
            ).use { file ->
                generateComponent(model, file)
            }
        }

        private fun readEntryPoints(classDeclaration: KSClassDeclaration) = classDeclaration
            .getDeclaredProperties().map { property ->
                val resolvedPropertyType = property.type.resolve().declaration
                EntryPoint(property, resolvedPropertyType)
            }.toList()

        @OptIn(KspExperimental::class)
        private fun readBinds(componentAnnotation: KSAnnotation): Map<KSDeclaration, KSDeclaration> {
            val bindModules = componentAnnotation.getArgument("modules").value as List<KSType>

            val binds = bindModules
                .map { it.declaration as KSClassDeclaration }
                .flatMap { it.getDeclaredFunctions() }
                .filter { it.isAnnotationPresent(Binds::class) }
                .associate { function ->
                    val resolvedReturnType = function.returnType!!.resolve().declaration
                    val resolvedParamType = function.parameters.single().type.resolve().declaration
                    resolvedReturnType to resolvedParamType
                }

            return binds
        }

        @OptIn(KspExperimental::class)
        private fun traverseDependencyGraph(factoryEntryPoints: List<KSDeclaration>): List<ComponentFactory> {
            val typesToProcess = mutableListOf<KSDeclaration>()
            typesToProcess += factoryEntryPoints

            val factories = mutableListOf<ComponentFactory>()
            val typesVisited = mutableListOf<KSDeclaration>()
            while (typesToProcess.isNotEmpty()) {
                val visitedClassDeclaration = typesToProcess.removeFirst() as KSClassDeclaration
                if (visitedClassDeclaration !in typesVisited) {
                    typesVisited += visitedClassDeclaration
                    val injectConstructors = visitedClassDeclaration.getConstructors()
                        .filter { it.isAnnotationPresent(Inject::class) }
                        .toList()
                    check(injectConstructors.size < 2) {
                        "There should be a most one @Inject constructor"
                    }
                    if (injectConstructors.isNotEmpty()) {
                        val injectConstructor = injectConstructors.first()
                        val constructorParams =
                            injectConstructor.parameters.map { it.type.resolve().declaration }
                        typesToProcess += constructorParams
                        val isSingleton = visitedClassDeclaration.isAnnotationPresent(Singleton::class)
                        factories.add(ComponentFactory(visitedClassDeclaration, constructorParams, isSingleton))
                    }
                }
            }
            return factories
        }

        private fun generateComponent(
            model: ComponentModel,
            ktFile: OutputStream
        ) {
            with(model) {
                ktFile.appendLine("package $packageName")
                ktFile.appendLine()

                imports.forEach {
                    ktFile.appendLine("import $it")
                }

                ktFile.appendLine()
                ktFile.appendLine("class $className : $componentInterfaceName {")

                factories.forEach { (classDeclaration, parameterDeclarations, isSingleton) ->
                    val name = classDeclaration.simpleName.asString()
                    val parameters = parameterDeclarations.map { requestedType ->
                        val providedType = binds[requestedType] ?: requestedType
                        providedType.simpleName.asString()
                    }

                    val singleton = if(isSingleton) "componentSingleton" else ""

                    ktFile.appendLine("\tprivate val provide$name = $singleton{")
                    ktFile.appendLine("\t\t$name(${parameters.joinToString(", ") { "provide$it()" }})")
                    ktFile.appendLine("\t}")
                }

                entryPoints.forEach { (propertyDeclaration, type) ->
                    val name = propertyDeclaration.simpleName.asString()

                    val typeSimpleName = type.simpleName.asString()

                    ktFile.appendLine("\toverride val $name: $typeSimpleName")
                    ktFile.appendLine("\t  get() = provide$typeSimpleName()")
                }
                ktFile.appendLine("}")
            }
        }
    }
}

infix fun KSAnnotation.isInstance(annotationKClass: KClass<*>): Boolean {
    return shortName.getShortName() == annotationKClass.simpleName &&
            annotationType.resolve().declaration.qualifiedName?.asString() == annotationKClass.qualifiedName
}

fun KSAnnotation.getArgument(name: String): KSValueArgument {
    return arguments.single { it.name?.asString() == name }
}