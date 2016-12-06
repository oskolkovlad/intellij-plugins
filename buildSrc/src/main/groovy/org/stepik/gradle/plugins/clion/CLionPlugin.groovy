package org.stepik.gradle.plugins.clion

import org.stepik.gradle.plugins.jetbrains.BasePlugin
import org.stepik.gradle.plugins.jetbrains.RepositoryType

/**
 * @author meanmail
 */
class CLionPlugin extends BasePlugin {
    private static final def PRODUCT_NAME = "CLion"
    private static final def EXTENSION_NAME = "clion"
    private static final def DEFAULT_REPO =
            'http://download.jetbrains.com/cpp/CLion-[version].[archiveType]'

    CLionPlugin() {
        extensionName = EXTENSION_NAME
        productName = PRODUCT_NAME
        productType = "CL"
        productGroup = "com.jetbrains.clion"
        tasksGroupName = EXTENSION_NAME
        runTaskClass = RunCLionTask
        extensionInstrumentCode = false
        repositoryType = RepositoryType.DIRECTORY
    }

    @Override
    String getRepositoryTemplate() {
        return DEFAULT_REPO
    }
}
