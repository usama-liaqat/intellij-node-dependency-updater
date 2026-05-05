package io.github.osamaliaqat.nodedependencyupdater.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.components.JBPanelWithEmptyText
import com.intellij.ui.content.ContentFactory
import java.io.File

class NodeDependencyUpdaterToolWindowFactory : ToolWindowFactory {

    override fun shouldBeAvailable(project: Project): Boolean {
        val basePath = project.basePath ?: return false
        return File(basePath, "package.json").exists()
    }

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val basePath = project.basePath
        val rootDir = if (basePath != null) File(basePath) else null
        val content = if (rootDir == null || !File(rootDir, "package.json").exists()) {
            JBPanelWithEmptyText().withEmptyText("No package.json found in project root")
        } else {
            OutdatedDependenciesPanel(project, rootDir)
        }
        val tab = ContentFactory.getInstance().createContent(content, "", false)
        toolWindow.contentManager.addContent(tab)
    }
}
