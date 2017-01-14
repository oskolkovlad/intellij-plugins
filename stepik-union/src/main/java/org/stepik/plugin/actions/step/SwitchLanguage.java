package org.stepik.plugin.actions.step;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.KeyboardShortcut;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.refactoring.move.moveFilesOrDirectories.MoveFilesOrDirectoriesUtil;
import com.jetbrains.tmp.learning.StudyUtils;
import com.jetbrains.tmp.learning.SupportedLanguages;
import com.jetbrains.tmp.learning.core.EduNames;
import com.jetbrains.tmp.learning.courseFormat.StepNode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class SwitchLanguage extends AbstractStepAction {
    private static final String ACTION_ID = "STEPIK.SwitchLanguage";
    private static final String SHORTCUT = "ctrl alt pressed PAGE_UP";

    public SwitchLanguage() {
        super("Switch language(" + KeymapUtil.getShortcutText(
                new KeyboardShortcut(KeyStroke.getKeyStroke(SHORTCUT), null)) + ")",
                "Switch language", AllIcons.Actions.Diff);
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
        switchLang(e.getProject());
    }

    private void switchLang(@Nullable Project project) {
        if (project == null) {
            return;
        }
        StepNode targetStepNode = StudyUtils.getSelectedStep(project);
        if (targetStepNode == null) {
            return;
        }

        if (targetStepNode.getSupportedLanguages().size() == 1) {
            return;
        }

        if (!ActionUtils.checkLangSettings(targetStepNode, project)) {
            return;
        }

        FileDocumentManager documentManager = FileDocumentManager.getInstance();
        FileEditorManager editorManager = FileEditorManager.getInstance(project);
        for (VirtualFile file : FileEditorManager.getInstance(project).getOpenFiles()) {
            Document document = documentManager.getDocument(file);
            if (document == null)
                continue;
            documentManager.saveDocument(document);
            editorManager.closeFile(file);
        }

        String srcPath = String.join("/", targetStepNode.getPath(), EduNames.SRC);
        VirtualFile src = project.getBaseDir().findFileByRelativePath(srcPath);
        if (src == null) {
            return;
        }
        VirtualFile hideVF = src.findChild(EduNames.HIDE);
        if (hideVF == null) {
            return;
        }
        PsiDirectory hide = PsiManager.getInstance(project).findDirectory(hideVF);
        if (hide == null) {
            return;
        }
        PsiDirectory srcPsi = PsiManager.getInstance(project).findDirectory(src);
        if (srcPsi == null) {
            return;
        }
        SupportedLanguages currentLang = targetStepNode.getCurrentLang();
        SupportedLanguages secondLang;
        if (currentLang == SupportedLanguages.JAVA) {
            secondLang = SupportedLanguages.PYTHON;
        } else {
            secondLang = SupportedLanguages.JAVA;
        }

        VirtualFile firstVF = src.findChild(currentLang.getMainFileName());
        if (firstVF == null) {
            return;
        }
        final PsiFile first = PsiManager
                .getInstance(project)
                .findFile(firstVF);
        if (first == null) {
            return;
        }

        VirtualFile secondVF = hide.getVirtualFile().findChild(secondLang.getMainFileName());
        if (secondVF == null) {
            return;
        }
        final PsiFile second = PsiManager
                .getInstance(project)
                .findFile(secondVF);
        if (second == null) {
            return;
        }

        ApplicationManager.getApplication().runWriteAction(() -> {
            MoveFilesOrDirectoriesUtil.doMoveFile(first, hide);
            MoveFilesOrDirectoriesUtil.doMoveFile(second, srcPsi);
        });
        String activateFileName = secondLang.getMainFileName();
        targetStepNode.setCurrentLang(secondLang);

        VirtualFile vf = src.findChild(activateFileName);
        if (vf != null)
            FileEditorManager.getInstance(project).openFile(vf, true);
    }

    @NotNull
    @Override
    public String getActionId() {
        return ACTION_ID;
    }

    @Nullable
    @Override
    public String[] getShortcuts() {
        return new String[]{SHORTCUT};
    }
}