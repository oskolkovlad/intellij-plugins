package org.stepik.gradle.plugins.jetbrains.dependency

import org.gradle.api.Nullable
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.publish.ivy.internal.artifact.DefaultIvyArtifact
import org.gradle.api.publish.ivy.internal.publication.DefaultIvyConfiguration
import org.gradle.api.publish.ivy.internal.publication.DefaultIvyPublicationIdentity
import org.gradle.api.publish.ivy.internal.publisher.IvyDescriptorFileGenerator
import org.gradle.tooling.BuildException
import org.jetbrains.annotations.NotNull
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.stepik.gradle.plugins.jetbrains.BasePlugin
import org.stepik.gradle.plugins.jetbrains.ProductPluginExtension
import org.stepik.gradle.plugins.jetbrains.Utils

/**
 * @author meanmail
 */
class DependencyManager {
    private static final Logger logger = LoggerFactory.getLogger(DependencyManager)

    @NotNull
    static ProductDependency resolveRemoteMaven(
            @NotNull Project project,
            @NotNull BasePlugin plugin,
            @NotNull ProductPluginExtension extension) {
        logger.debug("Adding $plugin.productName repository")
        project.repositories.maven { it.url = extension.repository }

        logger.debug("Adding $plugin.productName dependency")
        def libraryType = extension.type
        def version = extension.version
        def group = plugin.productGroup
        def name = plugin.productName.toLowerCase()
        def dependency = project.dependencies.create("$group:$name$libraryType:$version")
        def configuration = project.configurations.detachedConfiguration(dependency)

        def classesDirectory = getClassesDirectory(project, configuration)
        extension.idePath = classesDirectory
        return createCompileDependency(extension.version, classesDirectory, project)
    }

    @NotNull
    private static File getClassesDirectory(@NotNull Project project,
            @NotNull Configuration configuration) {
        File zipFile = configuration.singleFile
        logger.debug("Product zip: " + zipFile.path)
        def directoryName = zipFile.name - ".zip"

        def cacheDirectory = new File(zipFile.parent, directoryName)
        def markerFile = new File(cacheDirectory, "markerFile")
        if (!markerFile.exists()) {
            if (cacheDirectory.exists()) cacheDirectory.deleteDir()
            cacheDirectory.mkdir()
            logger.debug("Unzipping idea")
            project.copy {
                it.from(project.zipTree(zipFile))
                it.into(cacheDirectory)
            }
            markerFile.createNewFile()
            logger.debug("Unzipped")
        }
        return cacheDirectory
    }

    @NotNull
    static ProductDependency resolveLocal(
            @NotNull Project project,
            @NotNull ProductPluginExtension extension,
            @NotNull String productName) {
        def idePath = extension.idePath
        if (!idePath.exists() || !idePath.isDirectory()) {
            throw new BuildException("Specified idePath '$idePath' is not path to $productName", null)
        }

        return createCompileDependency(extension.version, idePath, project)
    }

    @NotNull
    private static ProductDependency createCompileDependency(
            @NotNull String version,
            @NotNull File classesDirectory,
            @NotNull Project project) {
        return new ProductDependency(version,
                version,
                classesDirectory,
                !hasKotlinDependency(project))
    }

    @NotNull
    private static Boolean hasKotlinDependency(@NotNull Project project) {
        def configurations = project.configurations
        def closure = {
            if ("org.jetbrains.kotlin" == it.group) {
                return "kotlin-runtime" == it.name || "kotlin-stdlib" == it.name || "kotlin-reflect" == it.name
            }
            return false
        }
        return configurations.getByName(JavaPlugin.RUNTIME_CONFIGURATION_NAME).allDependencies.find(closure) ||
                configurations.getByName(JavaPlugin.COMPILE_CONFIGURATION_NAME).allDependencies.find(closure)
    }

    static void register(
            @NotNull Project project,
            @NotNull final ProductDependency dependency,
            @NotNull String productName) {
        productName = productName.toLowerCase()

        final File ivyFile = getOrCreateIvyXml(dependency, productName)
        project.repositories.ivy {
            it.setUrl(dependency.classes)
            it.ivyPattern(ivyFile.absolutePath)
            it.artifactPattern("${dependency.classes.path}/[artifact].[ext]")
        }

        def map = [
                group        : "com.jetbrains",
                name         : productName,
                version      : dependency.version,
                configuration: "compile"
        ]

        project.dependencies.add(JavaPlugin.COMPILE_CONFIGURATION_NAME, map)
    }

    private static File getOrCreateIvyXml(
            @NotNull final ProductDependency dependency,
            @NotNull final String productName) {
        def ivyFile = new File(dependency.classes, "${dependency.getFqn(productName)}.xml")
        if (!ivyFile.exists()) {
            final def version = dependency.version
            final def identity = new DefaultIvyPublicationIdentity("com.jetbrains", productName, version)
            final def generator = new IvyDescriptorFileGenerator(identity)
            generator.addConfiguration(new DefaultIvyConfiguration("default"))
            generator.addConfiguration(new DefaultIvyConfiguration("compile"))
            generator.addConfiguration(new DefaultIvyConfiguration("sources"))
            dependency.jarFiles.each {
                generator.addArtifact(createJarCompileDependency(it, dependency.classes))
            }

            generator.writeTo(ivyFile)
        }

        return ivyFile
    }

    @NotNull
    private static DefaultIvyArtifact createJarCompileDependency(@NotNull File file, @NotNull File baseDir) {
        def relativePath = baseDir.toURI().relativize(file.toURI()).path

        def name = relativePath - ".jar"

        def artifact = new DefaultIvyArtifact(file, name, "jar", "jar", null)
        artifact.conf = "compile"
        return artifact
    }

    @Nullable
    static ProductDependency resolveLocalCashRepository(
            @NotNull Project project,
            @NotNull BasePlugin basePlugin,
            @NotNull ProductPluginExtension extension) {
        def idePath = extension.getIdePath()
        def ideArchiveType = extension.getArchiveType()
        def productName = basePlugin.productName

        if (!idePath.exists()) {
            def archive = Utils.getArchivePath(project, basePlugin, extension)
            if (!archive.exists()) {
                logger.info("Download {}", extension.repository)
                println("Download $extension.repository")

                archive = Utils.downloadProduct(basePlugin, extension, archive)
            }

            if (!archive) {
                println("$productName not loaded from $extension.repository")
                logger.warn("{} not loaded from {}", productName, extension.repository)
                return null
            }

            logger.info("{} loaded", productName)
            println("$productName Loaded")

            if (ideArchiveType == "zip") {
                println("Start Unzip ${productName}...")
                Utils.Unzip(archive, idePath)
            } else if (ideArchiveType == "tar.gz") {
                println("Start Untgz ${productName}...")
                Utils.Untgz(archive, idePath)
            }
            logger.info("Unarchivated $productName to $idePath")
            println("Unarchivated $productName to $idePath")
        }

        return DependencyManager.resolveLocal(project, extension, productName)
    }
}
