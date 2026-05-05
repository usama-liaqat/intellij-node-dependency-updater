package io.github.osamaliaqat.nodedependencyupdater.action

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.wm.ToolWindowManager
import java.io.File

class ShowOutdatedDependenciesAction : AnAction(
    "Update Node Dependencies",
    "Open the Node Dependency Updater tool window",
    AllIcons.Actions.Refresh,
) {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val project = e.project
        val basePath = project?.basePath
        val hasPackageJson = basePath != null && File(basePath, "package.json").exists()
        e.presentation.isEnabledAndVisible = project != null && hasPackageJson
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val toolWindow = ToolWindowManager.getInstance(project).getToolWindow("Node Dependency Updater")
        if (toolWindow == null) {
            Messages.showWarningDialog(project, "Node Dependency Updater tool window is unavailable.", "Node Dependency Updater")
            return
        }
        toolWindow.activate(null)
    }
}
