package com.amplitude

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.gradle.api.Project
import org.gradle.api.plugins.ExtensionContainer
import org.gradle.api.plugins.PluginContainer // Changed from PluginManager
import org.gradle.api.tasks.TaskContainer
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File
import java.util.Properties

class PublishModulePluginTest {

    private lateinit var project: Project
    private lateinit var plugin: PublishModulePlugin
    private lateinit var mockExtensions: ExtensionContainer
    private lateinit var mockPlugins: PluginContainer // Changed from PluginManager
    private lateinit var mockTasks: TaskContainer

    @BeforeEach
    fun setUp() {
        project = ProjectBuilder.builder().build()
        // Mock core Gradle objects
        mockExtensions = mockk()
        mockPlugins = mockk()
        mockTasks = mockk()

        // Stub project calls to return mocks
        every { project.extensions } returns mockExtensions
        every { project.plugins } returns mockPlugins
        every { project.tasks } returns mockTasks
        every { project.logger } returns mockk() // Mock logger to avoid NPE

        // It's often useful to have a real PublicationExtension for tests
        every { mockExtensions.create("publication", PublicationExtension::class.java) } returns PublicationExtension()

        plugin = PublishModulePlugin()
    }

    // Helper to access private methods via reflection
    private fun <T> PublishModulePlugin.callPrivateFunc(name: String, vararg args: Any?): T {
        val method = this::class.java.getDeclaredMethod(name, *args.map { it!!::class.javaPrimitiveType ?: it::class.java }.toTypedArray())
        method.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        return method.invoke(this, *args) as T
    }

    @Test
    fun `applyCorePlugins applies expected plugins`() {
        plugin.callPrivateFunc<Unit>("applyCorePlugins", project)

        verify { mockPlugins.apply("maven-publish") }
        verify { mockPlugins.apply("signing") }
        verify { mockPlugins.apply("org.jetbrains.dokka") }
    }

    @Test
    fun `loadLocalProperties loads properties when file exists`() {
        val tempDir = File(System.getProperty("java.io.tmpdir"))
        val mockRootProjectDir = File(tempDir, "mockRootProject-${System.currentTimeMillis()}")
        mockRootProjectDir.mkdirs()
        val localPropertiesFile = File(mockRootProjectDir, "local.properties")
        localPropertiesFile.writeText("test.property=testValue")

        val mockRootProject = ProjectBuilder.builder().withProjectDir(mockRootProjectDir).build()
        every { project.rootProject } returns mockRootProject


        val properties = plugin.callPrivateFunc<Properties>("loadLocalProperties", project)

        assert(properties.getProperty("test.property") == "testValue")

        localPropertiesFile.delete()
        mockRootProjectDir.deleteRecursively()
    }

    @Test
    fun `loadLocalProperties returns empty when file does not exist`() {
        val tempDir = File(System.getProperty("java.io.tmpdir"))
        val mockRootProjectDir = File(tempDir, "mockRootProject-${System.currentTimeMillis()}")
        mockRootProjectDir.mkdirs()

        val mockRootProject = ProjectBuilder.builder().withProjectDir(mockRootProjectDir).build()
        every { project.rootProject } returns mockRootProject

        val properties = plugin.callPrivateFunc<Properties>("loadLocalProperties", project)

        assert(properties.isEmpty)
        mockRootProjectDir.deleteRecursively()
    }

    // TODO: Add tests for registerJarTasks
    // TODO: Add tests for configurePublication
    // TODO: Add tests for configureSigning
    // TODO: Add test for the main apply method (integration style)
}
