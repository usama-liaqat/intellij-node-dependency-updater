package io.github.osamaliaqat.nodedependencyupdater.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.Messages
import com.intellij.ui.JBColor
import com.intellij.ui.RoundedLineBorder
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.RightGap
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.ui.JBUI
import io.github.osamaliaqat.nodedependencyupdater.model.OutdatedDependency
import io.github.osamaliaqat.nodedependencyupdater.model.VersionInfo
import io.github.osamaliaqat.nodedependencyupdater.service.NodeDependencyUpdaterSettings
import io.github.osamaliaqat.nodedependencyupdater.service.NpmService
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Dimension
import java.awt.Font
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JSpinner
import javax.swing.SpinnerNumberModel
import javax.swing.SwingUtilities

/**
 * Tool-window content panel: header row with status + min-age + Refresh + Update All,
 * scrollable card list in the middle, post-update command field at the bottom.
 */
class OutdatedDependenciesPanel(
    private val project: Project,
    rootDir: File,
) : JBPanel<OutdatedDependenciesPanel>(BorderLayout()) {

    private val service = NpmService(rootDir)
    private val deps = mutableListOf<OutdatedDependency>()

    /** Per-package selected version chosen by the user; survives Refresh within the same panel session. */
    private val sessionSelections = mutableMapOf<String, String>()
    private val cardsPanel = JPanel().apply {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        isOpaque = false
    }
    private val cardsScrollPane = JBScrollPane(cardsPanel).apply {
        border = JBUI.Borders.empty()
        verticalScrollBar.unitIncrement = 16
    }
    private val cardsLoadingPanel = LoadingOverlayPanel().apply {
        setContent(cardsScrollPane)
    }
    private val statusLabel = JBLabel("Ready")
    private val refreshButton = JButton("Refresh").apply { addActionListener { loadInBackground() } }
    private val updateAllButton = JButton("Update All").apply {
        toolTipText = "Update all packages that are not ignored"
        addActionListener { updateAll() }
    }
    private val settings = NodeDependencyUpdaterSettings.getInstance(project)
    private val minAgeSpinner = JSpinner(SpinnerNumberModel(settings.state.minReleaseAgeDays, 0, 365, 1)).apply {
        (editor as? JSpinner.DefaultEditor)?.textField?.columns = 4
        toolTipText = "Only show versions released at least this many days ago. Click Refresh to apply."
    }
    private val postCommandField = JBTextField(settings.state.postUpdateCommand, 40).apply {
        emptyText.text = "e.g. yarn install && bundle exec pod install"
        toolTipText = "Shell command to run after a successful update. Empty = nothing runs."
    }
    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneId.systemDefault())

    init {
        add(buildContent(), BorderLayout.CENTER)
        loadInBackground()
    }

    private fun buildContent(): JComponent = panel {
        row {
            cell(statusLabel).resizableColumn().align(AlignX.LEFT)
            label("Min age:")
            cell(minAgeSpinner).gap(RightGap.SMALL)
            label("d").gap(RightGap.SMALL)
            cell(refreshButton)
        }
        row {
            cell(cardsLoadingPanel).align(Align.FILL).resizableColumn()
        }.resizableRow()
        row("Post-update:") {
            cell(postCommandField).align(AlignX.FILL).resizableColumn()
        }
        row {
            cell(updateAllButton).align(AlignX.RIGHT).resizableColumn()
        }
    }

    // ---- Card management ----

    private fun setCards(items: List<OutdatedDependency>) {
        deps.clear()
        // Apply persisted ignore state so the checkbox reflects the user's saved decisions.
        items.forEach { it.ignored = settings.state.ignoredPackages.contains(it.name) }
        deps.addAll(items)
        cardsPanel.removeAll()
        deps.forEach { cardsPanel.add(createCard(it)) }
        cardsPanel.revalidate()
        cardsPanel.repaint()
    }

    private fun replaceCard(name: String, replacement: OutdatedDependency) {
        val idx = deps.indexOfFirst { it.name == name }
        if (idx < 0) return
        // Preserve a prior session-level selection if it's still in the available versions list.
        val carriedSelection = sessionSelections[name]
        if (carriedSelection != null && replacement.availableVersions.any { it.version == carriedSelection }) {
            replacement.selectedVersion = carriedSelection
        }
        replacement.ignored = settings.state.ignoredPackages.contains(name)
        deps[idx] = replacement
        cardsPanel.remove(idx)
        cardsPanel.add(createCard(replacement), idx)
        cardsPanel.revalidate()
        cardsPanel.repaint()
    }

    private fun removeCard(name: String) {
        val idx = deps.indexOfFirst { it.name == name }
        if (idx < 0) return
        deps.removeAt(idx)
        cardsPanel.remove(idx)
        cardsPanel.revalidate()
        cardsPanel.repaint()
    }

    private fun createCard(dep: OutdatedDependency): JComponent {
        val card = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            border = JBUI.Borders.compound(
                JBUI.Borders.empty(3, 4),
                JBUI.Borders.compound(
                    RoundedLineBorder(JBColor.border(), 10, 1),
                    JBUI.Borders.empty(8, 12),
                ),
            )
            alignmentX = Component.LEFT_ALIGNMENT
            isOpaque = false
        }

        // ---- Row 1: package name + badges ----
        val header = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.X_AXIS)
            alignmentX = Component.LEFT_ALIGNMENT
            isOpaque = false
        }
        header.add(bold(dep.name).locked())
        header.add(Box.createHorizontalStrut(8))
        header.add(badge(typeOf(dep)).locked())
        if (dep.workspace != null) {
            header.add(Box.createHorizontalStrut(4))
            header.add(badge(dep.workspace).locked())
        }
        header.add(Box.createHorizontalGlue())
        card.add(header)

        card.add(Box.createVerticalStrut(6))

        // ---- Row 2: current → latest + Ignore + Update ----
        val actions = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.X_AXIS)
            alignmentX = Component.LEFT_ALIGNMENT
            isOpaque = false
        }

        actions.add(JBLabel(dep.current).locked())
        actions.add(Box.createHorizontalStrut(4))
        actions.add(dimmed(formatDateTag(dep.currentReleaseDate, !dep.enriched)).locked())
        actions.add(Box.createHorizontalStrut(8))
        actions.add(dimmed("→").locked())
        actions.add(Box.createHorizontalStrut(8))

        if (dep.availableVersions.size > 1) {
            val combo = ComboBox(dep.availableVersions.map { formatVersionItem(it) }.toTypedArray()).apply {
                val selectedIdx = dep.availableVersions.indexOfFirst { it.version == dep.selectedVersion }
                if (selectedIdx >= 0) selectedIndex = selectedIdx
                toolTipText = "Pick a specific version to install"
                addActionListener {
                    val idx = selectedIndex
                    if (idx in dep.availableVersions.indices) {
                        val picked = dep.availableVersions[idx].version
                        dep.selectedVersion = picked
                        sessionSelections[dep.name] = picked
                    }
                }
            }
            actions.add(combo.locked())
        } else {
            val sel = dep.availableVersions.firstOrNull { it.version == dep.selectedVersion }
            val ver = sel?.version ?: dep.latest
            val date = sel?.releaseDate ?: dep.latestReleaseDate
            actions.add(bold(ver).locked())
            actions.add(Box.createHorizontalStrut(4))
            actions.add(dimmed(formatDateTag(date, loading = !dep.enriched)).locked())
        }

        actions.add(Box.createHorizontalGlue())

        actions.add(JBCheckBox("Ignore", dep.ignored).apply {
            addActionListener {
                dep.ignored = isSelected
                if (isSelected) settings.state.ignoredPackages.add(dep.name)
                else settings.state.ignoredPackages.remove(dep.name)
            }
            toolTipText = "Skip this package when running Update All (persisted across IDE restarts)"
            isOpaque = false
        })
        actions.add(Box.createHorizontalStrut(10))
        actions.add(JButton("Update").apply {
            isEnabled = dep.enriched
            addActionListener { updateOne(dep) }
        })

        card.add(actions)

        SwingUtilities.invokeLater {
            card.maximumSize = Dimension(Int.MAX_VALUE, card.preferredSize.height)
        }
        return card
    }

    private fun dimmed(text: String): JBLabel = JBLabel(text).apply { foreground = JBColor.GRAY }

    private fun bold(text: String): JBLabel = JBLabel(text).apply { font = font.deriveFont(Font.BOLD) }

    /** Pin a component's max size to its preferred so BoxLayout doesn't stretch it horizontally. */
    private fun <T : JComponent> T.locked(): T = apply { maximumSize = preferredSize }

    private fun formatDateTag(date: Instant?, loading: Boolean): String = when {
        loading -> "(loading…)"
        date != null -> "(${dateFormatter.format(date)})"
        else -> "(—)"
    }

    private fun formatVersionItem(v: VersionInfo): String {
        val date = v.releaseDate?.let { dateFormatter.format(it) } ?: "—"
        return "${v.version}  ($date)"
    }

    private fun badge(text: String): JBLabel = JBLabel(text).apply {
        foreground = JBColor.GRAY
        font = font.deriveFont(font.size - 1f)
        border = JBUI.Borders.compound(
            RoundedLineBorder(JBColor.border(), 6, 1),
            JBUI.Borders.empty(0, 6),
        )
    }

    private fun typeOf(d: OutdatedDependency): String = when {
        d.isDev && d.isPeer -> "dev+peer"
        d.isDev -> "dev"
        d.isPeer -> "peer"
        else -> "prod"
    }

    // ---- Persistence ----

    private fun persistSettings() {
        settings.state.minReleaseAgeDays = currentMinAge()
        settings.state.postUpdateCommand = postCommandField.text.trim()
    }

    private fun currentMinAge(): Int = (minAgeSpinner.value as? Int) ?: 0

    // ---- Loading flow ----

    private fun loadInBackground() {
        persistSettings()
        val minAge = currentMinAge()
        setLoading(true, "Fetching outdated packages…")
        AppExecutorUtil.getAppExecutorService().submit { streamLoad(minAge) }
    }

    private fun streamLoad(minAge: Int) {
        val raw = try {
            service.fetchOutdatedRaw()
        } catch (e: Exception) {
            onUiThread {
                setLoading(false)
                setCards(emptyList())
                statusLabel.text = "Failed to fetch — see error dialog"
                Messages.showErrorDialog(this, e.message ?: e.toString(), "Could Not Fetch Outdated Packages")
            }
            return
        }

        onUiThread {
            setCards(raw)
            statusLabel.text = if (raw.isEmpty())
                "All dependencies are up to date."
            else
                "Found ${raw.size} outdated — fetching version metadata…"
        }
        if (raw.isEmpty()) {
            onUiThread { setLoading(false) }
            return
        }

        val executor = AppExecutorUtil.getAppExecutorService()
        val futures = raw.map { dep ->
            executor.submit<Unit> {
                val enriched = try {
                    service.enrichOne(dep, minAge)
                } catch (_: Exception) {
                    null
                }
                onUiThread {
                    if (enriched == null) removeCard(dep.name)
                    else replaceCard(dep.name, enriched)
                }
            }
        }
        futures.forEach { runCatching { it.get(60, TimeUnit.SECONDS) } }

        onUiThread {
            setLoading(false)
            val count = deps.size
            val ageHint = if (minAge > 0) " (≥$minAge days old)" else ""
            statusLabel.text = if (count == 0)
                "All dependencies are up to date$ageHint."
            else
                "Found $count outdated dependencies$ageHint"
        }
    }

    private fun updateOne(dep: OutdatedDependency) {
        runUpdate(listOf(dep), "Updating ${dep.name}…")
    }

    private fun updateAll() {
        val toUpdate = deps.filter { !it.ignored }
        if (toUpdate.isEmpty()) {
            Messages.showInfoMessage(this, "All packages are ignored — nothing to update.", "Nothing to Update")
            return
        }
        runUpdate(toUpdate, "Updating ${toUpdate.size} packages…")
    }

    private fun runUpdate(deps: List<OutdatedDependency>, status: String) {
        persistSettings()
        val minAge = currentMinAge()
        val postCommand = postCommandField.text
        setLoading(true, status)
        AppExecutorUtil.getAppExecutorService().submit {
            val result = runCatching { service.update(deps) }
            val output = result.getOrNull()
            val failure = result.exceptionOrNull()

            if (failure != null) {
                onUiThread {
                    setLoading(false)
                    statusLabel.text = "Update failed — see error dialog"
                    Messages.showErrorDialog(this, failure.message ?: failure.toString(), "Package Update Failed")
                }
                return@submit
            }
            if (output != null && output.exitCode != 0) {
                val stderr = output.stderr.takeLast(2000).ifBlank { output.stdout.takeLast(2000) }
                onUiThread {
                    setLoading(false)
                    statusLabel.text = "Update failed (exit ${output.exitCode})"
                    Messages.showErrorDialog(this, stderr, "Package Update Failed")
                }
                return@submit
            }

            if (postCommand.isNotBlank()) {
                onUiThread { setLoading(true, "Running post-update command…") }
                val postResult = runCatching { service.runPostCommand(postCommand) }
                val postOutput = postResult.getOrNull()
                val postFailure = postResult.exceptionOrNull()
                if (postFailure != null) {
                    onUiThread {
                        setLoading(false)
                        statusLabel.text = "Post-update command failed — see error dialog"
                        Messages.showErrorDialog(this, postFailure.message ?: postFailure.toString(), "Post-Update Command Failed")
                    }
                    return@submit
                }
                if (postOutput != null && postOutput.exitCode != 0) {
                    val tail = postOutput.stderr.takeLast(2000).ifBlank { postOutput.stdout.takeLast(2000) }
                    onUiThread {
                        setLoading(false)
                        statusLabel.text = "Post-update command exited ${postOutput.exitCode}"
                        Messages.showErrorDialog(this, tail, "Post-Update Command Failed")
                    }
                    return@submit
                }
            }

            streamLoad(minAge)
        }
    }

    private fun setLoading(loading: Boolean, message: String? = null) {
        if (loading) {
            if (message != null) cardsLoadingPanel.setLoadingText(message)
            cardsLoadingPanel.startLoading()
        } else {
            cardsLoadingPanel.stopLoading()
        }
        if (message != null) statusLabel.text = message
        refreshButton.isEnabled = !loading
        updateAllButton.isEnabled = !loading
        minAgeSpinner.isEnabled = !loading
        postCommandField.isEnabled = !loading
    }

    private fun onUiThread(block: () -> Unit) {
        SwingUtilities.invokeLater(block)
    }
}
