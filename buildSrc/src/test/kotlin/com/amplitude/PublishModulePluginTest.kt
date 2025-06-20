package com.amplitude

import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import io.mockk.verify
import org.gradle.api.Project
import org.gradle.api.plugins.ExtensionContainer
import org.gradle.api.plugins.PluginContainer
import org.gradle.api.tasks.TaskContainer
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File
import java.util.Properties
import kotlin.test.assertEquals

class PublishModulePluginTest {

    private lateinit var project: Project
    private lateinit var plugin: PublishModulePlugin
    private lateinit var mockExtensions: ExtensionContainer
    private lateinit var mockPlugins: PluginContainer
    private lateinit var mockTasks: TaskContainer

    @BeforeEach
    fun setUp() {
        val realProject = ProjectBuilder.builder().build() // Create the real project instance
        project = spyk(realProject) // Use a spy of the real project instance

        plugin = PublishModulePlugin()
        mockPlugins = mockk<PluginContainer>(relaxed = true)
        every { project.plugins } returns mockPlugins
        mockExtensions = mockk<ExtensionContainer>(relaxed = true)
        every { project.extensions } returns mockExtensions
        mockTasks = mockk<TaskContainer>(relaxed = true)
        every { project.tasks } returns mockTasks
    }

    @Test
    fun `applyCorePlugins applies expected plugins`() {
        plugin.applyCorePlugins(project)

        verify { mockPlugins.apply("maven-publish") }
        verify { mockPlugins.apply("signing") }
        verify { mockPlugins.apply("org.jetbrains.dokka") }
    }

    @Test
    fun `loadLocalProperties loads properties when file exists`() {
        val testProperties = Properties().apply {
            setProperty("test.property1", "value1")
            setProperty("test.property2", "value2")
        }
        val tempLocalPropertiesFile = File(project.rootDir, "local.properties")
        tempLocalPropertiesFile.writer().use { writer ->
            testProperties.store(writer, null)
        }

        val loadedProperties = plugin.loadLocalProperties(project)

        assertEquals(testProperties.getProperty("test.property1"), loadedProperties.getProperty("test.property1"))
        assertEquals(testProperties.getProperty("test.property2"), loadedProperties.getProperty("test.property2"))

        tempLocalPropertiesFile.delete()
    }

    @Test
    fun `loadLocalProperties returns empty when file does not exist`() {
        val tempLocalPropertiesFile = File(project.rootDir, "local.properties")
        if (tempLocalPropertiesFile.exists()) {
            tempLocalPropertiesFile.delete()
        }

        val loadedProperties = plugin.loadLocalProperties(project)

        assert(loadedProperties.isEmpty) { "Properties should be empty when file does not exist" }
    }
}
