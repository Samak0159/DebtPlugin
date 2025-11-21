package com.github.fligneul.debtplugin.debt

import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProvider
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.Function
import javax.swing.Icon
import java.nio.file.Paths
import java.util.Locale
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.IconLoader

class DebtGutterLineMarkerProvider : LineMarkerProvider, com.intellij.openapi.project.DumbAware {
    private val log = Logger.getInstance(DebtGutterLineMarkerProvider::class.java)

    companion object {
        val DEBT_ICON: Icon = IconLoader.getIcon("/icons/technicalDebt.svg", DebtGutterLineMarkerProvider::class.java)
    }

    override fun getLineMarkerInfo(element: PsiElement): LineMarkerInfo<*>? {
        // Use slow markers flow for reliable per-line detection across languages
        return null
    }

    private fun nextNonWhitespace(start: PsiElement?): PsiElement? {
        var el = start
        // Move forward until a non-whitespace leaf/element is found
        while (el is PsiWhiteSpace) {
            el = PsiTreeUtil.nextLeaf(el, true)
        }
        // Some languages may return composite elements at line start; skip pure whitespace children
        if (el is PsiWhiteSpace) return null
        return el
    }

    override fun collectSlowLineMarkers(elements: MutableList<out PsiElement>, result: MutableCollection<in LineMarkerInfo<*>>) {
        if (elements.isEmpty()) return
        val file = elements.first().containingFile ?: return
        val doc = file.viewProvider.document ?: return
        val project: Project = elements.first().project
        val debtService = project.service<DebtService>()

        val vFile = file.virtualFile ?: return
        val normFilePath = normalizePath(vFile.path)

        // Group elements by line and pick a representative non-whitespace element per line
        val firstElementPerLine = HashMap<Int, PsiElement>()
        for (el in elements) {
            val range = el.textRange ?: continue
            val line = doc.getLineNumber(range.startOffset)
            // pick the earliest element at this line
            val existing = firstElementPerLine[line]
            if (existing == null || range.startOffset < (existing.textRange?.startOffset ?: Int.MAX_VALUE)) {
                firstElementPerLine[line] = el
            }
        }

        for ((line, candidate) in firstElementPerLine) {
            val lineStart = doc.getLineStartOffset(line)
            val firstAtLine = file.findElementAt(lineStart) ?: candidate
            val anchor = nextNonWhitespace(firstAtLine) ?: candidate

            val lineInFile = line + 1
            val debtsOnLine = debtService.all().filter {
                normalizePath(it.file) == normFilePath && it.line == lineInFile
            }
            if (debtsOnLine.isEmpty()) continue

            val icon: Icon = DEBT_ICON
            val tooltipProvider = Function<PsiElement, String> {
                if (debtsOnLine.size == 1) {
                    val d = debtsOnLine.first()
                    "Debt: ${'$'}{d.description} [${'$'}{d.status}, ${'$'}{d.priority}]"
                } else {
                    val items = debtsOnLine.joinToString("\n") { "- ${'$'}{it.description} [${'$'}{it.status}, ${'$'}{it.priority}]" }
                    "Debts (${debtsOnLine.size}):\n${items}"
                }
            }

            result.add(
                LineMarkerInfo(
                    anchor,
                    anchor.textRange,
                    icon,
                    tooltipProvider,
                    { _, _ -> },
                    GutterIconRenderer.Alignment.LEFT,
                    { "Debt" }
                )
            )
        }
    }

    private fun normalizePath(path: String): String {
        return try {
            Paths.get(path).toAbsolutePath().normalize().toString().replace('\\', '/').lowercase(Locale.getDefault())
        } catch (e: Exception) {
            path.replace('\\', '/').lowercase(Locale.getDefault())
        }
    }
}
