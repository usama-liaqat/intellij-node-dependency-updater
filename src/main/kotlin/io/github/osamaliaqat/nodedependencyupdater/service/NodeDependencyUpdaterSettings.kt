package io.github.osamaliaqat.nodedependencyupdater.service

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.StoragePathMacros
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project

/**
 * Project-scoped settings for the Node dependency updater dialog.
 *
 * Stored in `.idea/workspace.xml` under `<component name="NodeDependencyUpdaterSettings">`.
 * `workspace.xml` is in the standard JetBrains `.gitignore` template, so this state stays
 * local to each developer and does not get committed.
 */
@Service(Service.Level.PROJECT)
@State(
    name = "NodeDependencyUpdaterSettings",
    storages = [Storage(StoragePathMacros.WORKSPACE_FILE)],
)
class NodeDependencyUpdaterSettings : PersistentStateComponent<NodeDependencyUpdaterSettings.State> {

    data class State(
        var minReleaseAgeDays: Int = 0,
        var postUpdateCommand: String = "",
        // Package names the user has explicitly chosen to skip during Update All.
        var ignoredPackages: MutableSet<String> = mutableSetOf(),
    )

    private var myState = State()

    override fun getState(): State = myState

    override fun loadState(state: State) {
        myState = state
    }

    companion object {
        fun getInstance(project: Project): NodeDependencyUpdaterSettings = project.service()
    }
}
