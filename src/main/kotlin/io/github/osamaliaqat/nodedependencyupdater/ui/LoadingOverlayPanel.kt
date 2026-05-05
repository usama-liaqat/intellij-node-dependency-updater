package io.github.osamaliaqat.nodedependencyupdater.ui

import com.intellij.ui.JBColor
import java.awt.BasicStroke
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.awt.image.ConvolveOp
import java.awt.image.Kernel
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.Timer
import kotlin.math.exp

/**
 * Wraps content with a custom loading overlay: a 0.3-alpha black scrim across the whole
 * panel + a centered dark rounded card with a rotating spinner and the loading message.
 */
class LoadingOverlayPanel : JPanel(BorderLayout()) {
    private var isLoading = false
    private var loadingText = "Loading…"
    private var spinAngle = 0
    private var blurredCache: BufferedImage? = null
    private var lastBlurAt = 0L
    private val animationTimer = Timer(50) {
        spinAngle = (spinAngle + 18) % 360
        repaint()
    }.apply { isCoalesce = true }

    fun setContent(content: JComponent) {
        removeAll()
        add(content, BorderLayout.CENTER)
        revalidate()
        repaint()
    }

    fun startLoading(message: String = "Loading…") {
        loadingText = message
        isLoading = true
        animationTimer.start()
        repaint()
    }

    fun stopLoading() {
        isLoading = false
        blurredCache = null
        animationTimer.stop()
        repaint()
    }

    fun setLoadingText(message: String) {
        loadingText = message
        if (isLoading) repaint()
    }

    override fun removeNotify() {
        animationTimer.stop()
        super.removeNotify()
    }

    override fun paint(g: Graphics) {
        if (!isLoading || width <= 0 || height <= 0) {
            super.paint(g)
            return
        }
        // Capture children to an offscreen buffer, blur it (cached), draw blurred version,
        // then draw scrim + card on top. The blur cache refreshes every BLUR_REFRESH_MS so
        // streaming updates visibly progress underneath without blurring on every spinner tick.
        val now = System.currentTimeMillis()
        val cache = blurredCache
        val cacheValid = cache != null &&
            cache.width == width &&
            cache.height == height &&
            now - lastBlurAt < BLUR_REFRESH_MS
        val toDraw = if (cacheValid) cache!! else captureAndBlur().also {
            blurredCache = it
            lastBlurAt = now
        }
        g.drawImage(toDraw, 0, 0, null)
        paintOverlay(g)
    }

    private fun captureAndBlur(): BufferedImage {
        val raw = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        val bg = raw.createGraphics()
        try {
            super.paint(bg)
        } finally {
            bg.dispose()
        }
        return gaussianBlur(raw, BLUR_RADIUS)
    }

    private fun gaussianBlur(src: BufferedImage, radius: Int): BufferedImage {
        val sigma = radius / 2f
        val kernel = gaussianKernel1d(radius, sigma)
        val horizontal = ConvolveOp(Kernel(kernel.size, 1, kernel), ConvolveOp.EDGE_NO_OP, null).filter(src, null)
        return ConvolveOp(Kernel(1, kernel.size, kernel), ConvolveOp.EDGE_NO_OP, null).filter(horizontal, null)
    }

    private fun gaussianKernel1d(radius: Int, sigma: Float): FloatArray {
        val size = radius * 2 + 1
        val k = FloatArray(size)
        var sum = 0f
        for (i in 0 until size) {
            val x = (i - radius).toFloat()
            k[i] = exp(-(x * x) / (2 * sigma * sigma))
            sum += k[i]
        }
        for (i in k.indices) k[i] /= sum
        return k
    }

    private fun paintOverlay(g: Graphics) {
        val g2 = g.create() as Graphics2D
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)

            // ~0.3-alpha scrim across the whole panel (sits on top of the blurred snapshot)
            g2.color = scrimColor()
            g2.fillRect(0, 0, width, height)

            // Card sized to the text
            g2.font = font.deriveFont(13f)
            val fm = g2.fontMetrics
            val textWidth = fm.stringWidth(loadingText)
            val spinnerSize = 22
            val padX = 22
            val padY = 18
            val gap = 12
            val cardWidth = padX * 2 + spinnerSize + gap + textWidth
            val cardHeight = padY * 2 + spinnerSize.coerceAtLeast(fm.height)
            val cardX = (width - cardWidth) / 2
            val cardY = (height - cardHeight) / 2

            // Theme-aware card background + subtle border
            g2.color = CARD_BG
            g2.fillRoundRect(cardX, cardY, cardWidth, cardHeight, 12, 12)
            g2.color = CARD_BORDER
            g2.stroke = BasicStroke(1f)
            g2.drawRoundRect(cardX, cardY, cardWidth, cardHeight, 12, 12)

            // Rotating arc spinner
            val spinnerX = cardX + padX
            val spinnerY = cardY + (cardHeight - spinnerSize) / 2
            g2.color = CARD_FG
            g2.stroke = BasicStroke(2.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
            g2.drawArc(spinnerX, spinnerY, spinnerSize, spinnerSize, -spinAngle, 270)

            // Message text
            g2.color = CARD_FG
            val textX = spinnerX + spinnerSize + gap
            val textY = cardY + (cardHeight + fm.ascent) / 2 - 2
            g2.drawString(loadingText, textX, textY)
        } finally {
            g2.dispose()
        }
    }

    private fun scrimColor(): Color {
        // Light theme gets a near-white scrim, dark theme a near-black scrim — both at ~0.3 alpha.
        return if (JBColor.isBright()) Color(255, 255, 255, 110) else Color(0, 0, 0, 110)
    }

    companion object {
        // Card paints over the scrimmed background; use JBColor so dark theme keeps the dark card
        // and light theme gets a soft elevated surface that stays readable.
        private val CARD_BG = JBColor(Color(0xF7F8FA), Color(0x1F2024))
        private val CARD_BORDER = JBColor(Color(0xD0D4D8), Color(0x4E5256))
        private val CARD_FG = JBColor(Color(0x1F2024), Color(0xE6E6E6))
        private const val BLUR_RADIUS = 5
        private const val BLUR_REFRESH_MS = 500L
    }
}
