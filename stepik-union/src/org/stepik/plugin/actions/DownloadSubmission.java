package org.stepik.plugin.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.KeyboardShortcut;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.tmp.learning.StudyState;
import com.jetbrains.tmp.learning.StudyTaskManager;
import com.jetbrains.tmp.learning.StudyUtils;
import com.jetbrains.tmp.learning.actions.StudyActionWithShortcut;
import com.jetbrains.tmp.learning.courseFormat.Task;
import com.jetbrains.tmp.learning.editor.StudyEditor;
import com.jetbrains.tmp.learning.stepik.StepikConnectorGet;
import com.jetbrains.tmp.learning.stepik.StepikConnectorPost;
import com.jetbrains.tmp.learning.stepik.StepikWrappers;
import org.apache.http.NameValuePair;
import org.apache.http.message.BasicNameValuePair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

public class DownloadSubmission extends StudyActionWithShortcut {
    public static final String ACTION_ID = "STEPIK.DownloadSubmission";
    public static final String SHORTCUT = "ctrl pressed PAGE_DOWN";

    public DownloadSubmission() {
        super("Download submission(" + KeymapUtil.getShortcutText(new KeyboardShortcut(KeyStroke.getKeyStroke(SHORTCUT), null)) + ")", "Download submission", IconLoader.getIcon("/icons/arrow-down.png"));
    }

    @NotNull
    @Override
    public String getActionId() {
        return ACTION_ID;
    }

    @Nullable
    @Override
    public String[] getShortcuts() {
        return new String[0];
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
        downloadSubmission(e.getProject());
    }

    private void downloadSubmission(Project project) {
        StudyEditor studyEditor = StudyUtils.getSelectedStudyEditor(project);
        StudyState studyState = new StudyState(studyEditor);
        if (!studyState.isValid()) {
            return;
        }
        Task targetTask = studyState.getTask();
        if (targetTask == null) {
            return;
        }

        String stepId = Integer.toString(targetTask.getStepId());
        String userId = Integer.toString(StudyTaskManager.getInstance(project).getUser().getId());

        List<NameValuePair> nvps = new ArrayList<>();
        nvps.add(new BasicNameValuePair("step", stepId));
        nvps.add(new BasicNameValuePair("user", userId));
        nvps.add(new BasicNameValuePair("order", "desc"));

        List<StepikWrappers.SubmissionContainer.Submission> submissions = StepikConnectorGet.getSubmissions(nvps).submissions;
        StepikWrappers.MetricsWrapper metric = new StepikWrappers.MetricsWrapper(
                StepikWrappers.MetricsWrapper.PluginNames.S_Union,
                StepikWrappers.MetricsWrapper.MetricActions.DOWNLOAD,
                targetTask.getLesson().getCourse().getId(),
                targetTask.getStepId());
        StepikConnectorPost.postMetric(metric);

        String currentLang = StudyTaskManager.getInstance(project).getLangManager().getLangSetting(targetTask).getCurrentLang();
        String activateFileName = currentLang.equals("python3") ? "main.py" : "Main.java";
        String code = null;
        for (StepikWrappers.SubmissionContainer.Submission submission : submissions){
            if (submission.reply.language.startsWith(currentLang) ){
                code = submission.reply.code;
                break;
            }
        }
        if (code == null) return;
        final String finalCode = code;

        VirtualFile vf = studyState.getTaskDir().findChild(activateFileName);
        FileDocumentManager documentManager = FileDocumentManager.getInstance();

        CommandProcessor.getInstance().executeCommand(project,
                () -> ApplicationManager.getApplication().runWriteAction(
                        () -> {
                            documentManager.getDocument(vf).setText(finalCode);
                        }), "Download last submission", "Download last submission");

    }
}
