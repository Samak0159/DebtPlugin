package com.github.fligneul.debtplugin.debt.glutter;

import com.github.fligneul.debtplugin.debt.icons.DebtIcons;
import com.github.fligneul.debtplugin.debt.model.DebtItem;
import com.github.fligneul.debtplugin.debt.model.Repository;
import com.github.fligneul.debtplugin.debt.service.DebtService;
import com.intellij.codeInsight.daemon.LineMarkerInfo;
import com.intellij.codeInsight.daemon.LineMarkerProvider;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.Function;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.Icon;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public class DebtGutterLineMarkerProvider implements LineMarkerProvider, DumbAware {
    @Override
    public @Nullable LineMarkerInfo<?> getLineMarkerInfo(PsiElement element) {
        PsiFile file = element.getContainingFile();
        if (file == null) return null;
        Document doc = file.getViewProvider().getDocument();
        if (doc == null) return null;
        if (element.getTextRange() == null) return null;

        int lineNumber = doc.getLineNumber(element.getTextRange().getStartOffset());
        int lineStartOffset = doc.getLineStartOffset(lineNumber);

        PsiElement firstAtLine = file.findElementAt(lineStartOffset);
        if (firstAtLine == null) return null;
        PsiElement firstNonWs = nextNonWhitespace(firstAtLine);
        if (firstNonWs == null) return null;

        if (element != firstNonWs) return null;

        Project project = element.getProject();
        DebtService debtService = project.getService(DebtService.class);

        if (file.getVirtualFile() == null) return null;
        String osPath = file.getVirtualFile().getPath();
        String basePath = debtService.findRepoRootForAbsolutePath(osPath);
        final Map.Entry<Repository, List<DebtItem>> debtsForRepository = debtService.getDebtForRepositoryAbsolutePath(basePath).orElseThrow();

        String currentRel = toProjectRelative(osPath, basePath);
        int lineInFile = lineNumber + 1;
        List<DebtItem> debtsOnLine = new ArrayList<>();
        for (DebtItem debtItem : debtsForRepository.getValue()) {
            String stored = debtItem.getFile();
            if (stored.isBlank()) continue;
            String storedRel = toProjectRelative(stored, basePath);
            if (storedRel.equalsIgnoreCase(currentRel) && debtItem.getLine() == lineInFile) {
                debtsOnLine.add(debtItem);
            }
        }
        if (debtsOnLine.isEmpty()) return null;

        Icon icon = DebtIcons.TECHNICAL_DEBT;
        Function<PsiElement, String> tooltipProvider = psi -> {
            if (debtsOnLine.size() == 1) {
                DebtItem d = debtsOnLine.get(0);
                return """
                            Debt
                                %s
                                by %s
                        """.formatted(getDebtName(d), d.getUsername());
            } else {
                StringBuilder sb = new StringBuilder();
                for (DebtItem it : debtsOnLine) {
                    sb.append("Debt : \n ")
                            .append(getDebtName(it))
                            .append(" \n by ")
                            .append(it.getUsername())
                            .append("\n");
                }
                return sb.toString();
            }
        };

        return new LineMarkerInfo<>(
                element,
                firstNonWs.getTextRange(),
                icon,
                tooltipProvider,
                (evt, el) -> {
                },
                GutterIconRenderer.Alignment.LEFT,
                () -> "Debt"
        );
    }

    private @NotNull String getDebtName(final DebtItem d) {
        final String debtName = Optional.ofNullable(d.getTitle())
                .filter(str -> !str.isBlank())
                .or(() -> Optional.ofNullable(d.getDescription())
                        .filter(str -> !str.isBlank()))
                .orElse("UNKNOWN");
        return debtName;
    }

    private static @Nullable PsiElement nextNonWhitespace(@Nullable PsiElement start) {
        PsiElement el = start;
        while (el instanceof PsiWhiteSpace) {
            el = PsiTreeUtil.nextLeaf(el, true);
        }
        if (el instanceof PsiWhiteSpace) return null;
        return el;
    }

    public void collectSlowLineMarkers(List<? extends PsiElement> elements, Collection<? super LineMarkerInfo<?>> result) {
        // Not used
    }

    private static String normalizePath(String path) {
        try {
            return Paths.get(path).toAbsolutePath().normalize().toString().replace('\\', '/').toLowerCase(Locale.getDefault());
        } catch (Exception e) {
            return path.replace('\\', '/').toLowerCase(Locale.getDefault());
        }
    }

    private static String toProjectRelative(String anyPath, String basePath) {
        if (anyPath == null) return "";
        String norm = anyPath.replace('\\', '/');
        try {
            Path p = Paths.get(anyPath);
            if (basePath != null) {
                Path base = Paths.get(basePath).toAbsolutePath().normalize();
                Path abs = p.isAbsolute() ? p.toAbsolutePath().normalize() : base.resolve(p).normalize();
                if (abs.startsWith(base)) {
                    return base.relativize(abs).toString().replace('\\', '/');
                }
                return abs.toString().replace('\\', '/');
            } else {
                Path abs = p.isAbsolute() ? p.toAbsolutePath().normalize() : p.normalize();
                return abs.toString().replace('\\', '/');
            }
        } catch (Exception e) {
            return norm;
        }
    }
}
