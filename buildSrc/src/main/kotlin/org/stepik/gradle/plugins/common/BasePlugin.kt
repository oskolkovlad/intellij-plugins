package org.stepik.gradle.plugins.common

import org.gradle.api.IllegalDependencyNotation
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.DuplicatesStrategy.INCLUDE
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.plugins.JavaPlugin.JAR_TASK_NAME
import org.gradle.api.plugins.JavaPlugin.PROCESS_RESOURCES_TASK_NAME
import org.gradle.api.plugins.JavaPlugin.RUNTIME_ELEMENTS_CONFIGURATION_NAME
import org.gradle.api.tasks.bundling.Zip
import org.gradle.api.tasks.testing.Test
import org.gradle.internal.jvm.Jvm
import org.gradle.language.jvm.tasks.ProcessResources
import org.slf4j.LoggerFactory
import org.stepik.gradle.plugins.common.RepositoryType.MAVEN
import org.stepik.gradle.plugins.common.Utils.getProductJvmArgs
import org.stepik.gradle.plugins.common.dependency.DependencyManager.register
import org.stepik.gradle.plugins.common.dependency.DependencyManager.resolveLocal
import org.stepik.gradle.plugins.common.dependency.DependencyManager.resolveLocalCashRepository
import org.stepik.gradle.plugins.common.dependency.DependencyManager.resolveRemoteMaven
import org.stepik.gradle.plugins.common.tasks.PatchPluginXmlTask
import org.stepik.gradle.plugins.common.tasks.PrepareSandboxTask
import org.stepik.gradle.plugins.common.tasks.PublishTask


abstract class BasePlugin(private val productSettings: ProductSettings) : Plugin<Project> {
    private val logger = LoggerFactory.getLogger(BasePlugin::class.java)

    val prepareSandboxTaskName = "prepare${productSettings.productName}Sandbox"
    private val prepareTestSandboxTaskName = "prepare${productSettings.productName}TestSandbox"
    private val patchPluginXmlTaskName = "patch${productSettings.productName}PluginXml"
    val buildPluginTaskName = "build${productSettings.productName}Plugin"

    override fun apply(project: Project) {
        project.plugins.apply(JavaPlugin::class.java)

        val extension = project.extensions
                .createProductPluginExtension(productSettings.extensionName,
                        productName = productSettings.productName,
                        productType = productSettings.productType,
                        productGroup = productSettings.productGroup,
                        repository = productSettings.repositoryTemplate,
                        project = project,
                        repositoryType = productSettings.repositoryType,
                        publish = ProductPluginExtensionPublish()
                )

        configureTasks(project, extension)
    }

    private fun configureTasks(project: Project, extension: ProductPluginExtension) {
        logger.info("Configuring {} gradle plugin", productSettings.productName)

        configurePatchPluginXmlTask(project, extension)
        configurePrepareSandboxTask(project, extension)
        configureRunTask(project, extension)
        configureBuildPluginTask(project)
        configurePublishPluginTask(project, extension)
        configureProcessResources(project)
        project.afterEvaluate {
            configureProjectAfterEvaluate(it, extension)
        }
    }

    private fun configureProjectAfterEvaluate(project: Project,
                                              extension: ProductPluginExtension) {
        project.subprojects.forEach {
            val subprojectExtension = it.extensions
                    .findByName(productSettings.extensionName) as ProductPluginExtension? ?: return@forEach
            configureProjectAfterEvaluate(it, subprojectExtension)
        }

        configureDependency(project, extension)
        configureTestTasks(project, extension)
    }

    private fun configureDependency(project: Project, extension: ProductPluginExtension) {
        logger.info("Configuring {} dependency", productSettings.productName)

        val dependency = if (extension.repositoryType == MAVEN) {
            resolveRemoteMaven(project, extension)
        } else {
            resolveLocalCashRepository(project, extension)
        } ?: resolveLocal(project, extension) ?: throw IllegalDependencyNotation()

        extension.dependency = dependency
        register(project, dependency, extension)

        val toolsJar = Jvm.current().toolsJar ?: return
        project.dependencies.add(RUNTIME_ELEMENTS_CONFIGURATION_NAME, project.files(toolsJar))
    }

    private fun configurePrepareSandboxTask(project: Project, ext: ProductPluginExtension) {
        logger.info("Configuring prepare {} sandbox task", productSettings.productName)

        project.tasks.create(prepareSandboxTaskName, PrepareSandboxTask::class.java).apply {
            group = productSettings.productName
            description = "Prepare sandbox directory with installed plugin and its dependencies."
            extension = ext
            dependsOn(JAR_TASK_NAME)
        }
    }

    private fun configurePatchPluginXmlTask(project: Project, ext: ProductPluginExtension) {
        logger.info("Configuring patch plugin.xml task")

        project.tasks.create(patchPluginXmlTaskName, PatchPluginXmlTask::class.java).apply {
            group = productSettings.productName
            description = "Patch plugin xml files with corresponding since/until build numbers and version attributes"
            extension = ext
        }
    }

    private fun configureRunTask(project: Project, ext: ProductPluginExtension) {
        logger.info("Configuring run {} task", productSettings.productName)

        project.tasks.create("run${productSettings.productName}", productSettings.runTaskClass).apply {
            group = productSettings.productName
            description = "Runs ${productSettings.productName} with installed plugin."
            extension = ext
            plugin = this@BasePlugin
            dependsOn(prepareSandboxTaskName)
        }
    }

    private fun configurePublishPluginTask(project: Project, ext: ProductPluginExtension) {
        logger.info("Configuring publishing {} plugin task", productSettings.productName)
        project.tasks.create("publish${productSettings.productName}", PublishTask::class.java).apply {
            group = productSettings.productName
            description = "Publish plugin distribution on plugins.jetbrains.com."
            extension = ext
            plugin = this@BasePlugin
            dependsOn(buildPluginTaskName)
        }
    }

    private fun configureProcessResources(project: Project) {
        logger.info("Configuring {} resources task", productSettings.productName)

        val processResourcesTask = project.tasks.findByName(PROCESS_RESOURCES_TASK_NAME) as ProcessResources?
        if (processResourcesTask != null) {
            val patchPluginXmlTask = project.tasks.findByName(patchPluginXmlTaskName) ?: return
            processResourcesTask.from(patchPluginXmlTask) {
                it.into("META-INF")
                it.duplicatesStrategy = INCLUDE
            }
        }
    }

    private fun configureTestTasks(project: Project, extension: ProductPluginExtension) {
        logger.info("Configuring IntelliJ tests tasks")
        project.tasks.withType(Test::class.java).forEach {
            val configDirectory = project.file("${extension.sandboxDirectory}/config-test")
            val systemDirectory = project.file("${extension.sandboxDirectory}/system-test")
            val pluginsDirectory = project.file("${extension.sandboxDirectory}/plugins-test")

            it.enableAssertions = true
            it.systemProperties(extension.systemProperties)
            it.systemProperties(Utils.getProductSystemProperties(configDirectory, systemDirectory, pluginsDirectory))
            val idePath = extension.ideDirectory ?: return@forEach
            it.jvmArgs = getProductJvmArgs(it, it.jvmArgs, idePath)
            val dependency = extension.dependency
            if (dependency != null) {
                it.classpath += project.files("${dependency.classes}/lib/resources.jar",
                        "${dependency.classes}/lib/idea.jar")
            }
            it.outputs.dir(systemDirectory)
            it.outputs.dir(configDirectory)
            it.dependsOn(project.getTasksByName(prepareTestSandboxTaskName, false))
        }
    }

    private fun configureBuildPluginTask(project: Project) {
        logger.info("Configuring building plugin task")
        val prepareSandboxTask = project.tasks.findByName(prepareSandboxTaskName) as PrepareSandboxTask
        project.tasks.create(buildPluginTaskName, Zip::class.java).apply {
            description = "Bundles the project as a distribution."
            group = productSettings.productName
            from("${prepareSandboxTask.destinationDir}/${project.name}")
            into(project.name)
            dependsOn(prepareSandboxTask)
            baseName = project.name
        }
    }

}

