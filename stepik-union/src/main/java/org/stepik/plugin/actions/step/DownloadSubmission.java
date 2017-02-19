package org.stepik.plugin.actions.step;

import com.intellij.ide.projectView.ProjectView;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.KeyboardShortcut;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.JBPopupListener;
import com.intellij.openapi.ui.popup.LightweightWindowEvent;
import com.intellij.openapi.ui.popup.PopupChooserBuilder;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.tmp.learning.StudyUtils;
import com.jetbrains.tmp.learning.SupportedLanguages;
import com.jetbrains.tmp.learning.core.EduNames;
import com.jetbrains.tmp.learning.courseFormat.StepNode;
import com.jetbrains.tmp.learning.courseFormat.StudyNode;
import com.jetbrains.tmp.learning.courseFormat.StudyStatus;
import com.jetbrains.tmp.learning.stepik.StepikConnectorLogin;
import icons.AllStepikIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.stepik.api.client.StepikApiClient;
import org.stepik.api.exceptions.StepikClientException;
import org.stepik.api.objects.submissions.Submission;
import org.stepik.api.objects.submissions.Submissions;
import org.stepik.api.queries.Order;
import org.stepik.core.metrics.Metrics;
import org.stepik.core.utils.Utils;

import javax.swing.*;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;
import java.util.stream.Collectors;

import static org.stepik.core.metrics.MetricsStatus.DATA_NOT_LOADED;
import static org.stepik.core.metrics.MetricsStatus.EMPTY_SOURCE;
import static org.stepik.core.metrics.MetricsStatus.SUCCESSFUL;
import static org.stepik.core.metrics.MetricsStatus.TARGET_NOT_FOUND;
import static org.stepik.core.metrics.MetricsStatus.USER_CANCELED;

/**
 * @author meanmail
 * @since 0.8
 */
public class DownloadSubmission extends AbstractStepAction {
    private static final Logger logger = Logger.getInstance(DownloadSubmission.class);
    private static final String ACTION_ID = "STEPIK.DownloadSubmission";
    private static final String SHORTCUT = "ctrl alt pressed PAGE_DOWN";

    public DownloadSubmission() {
        super("Download submission from the List(" + KeymapUtil.getShortcutText(
                new KeyboardShortcut(KeyStroke.getKeyStroke(SHORTCUT), null)) + ")",
                "Download submission from the List", AllStepikIcons.ToolWindow.download);
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

    @Override
    public void actionPerformed(AnActionEvent e) {
        downloadSubmission(e.getProject());
    }

    private void downloadSubmission(@Nullable Project project) {
        if (project == null) {
            return;
        }

        StepNode stepNode = StudyUtils.getSelectedStep(project);
        if (stepNode == null) {
            return;
        }

        String title = "Download submission";

        List<Submission> submissions = ProgressManager.getInstance()
                .run(new Task.WithResult<List<Submission>, RuntimeException>(project, title, true) {
                    @Override
                    protected List<Submission> compute(@NotNull ProgressIndicator progressIndicator)
                            throws RuntimeException {
                        progressIndicator.setIndeterminate(true);
                        StudyNode parent = stepNode.getParent();
                        String lessonName = parent != null ? parent.getName() : "";
                        progressIndicator.setText(lessonName);
                        progressIndicator.setText2(stepNode.getName());
                        List<Submission> submissions = getSubmissions(stepNode);

                        if (Utils.isCanceled()) {
                            Metrics.downloadAction(project, stepNode, USER_CANCELED);
                            return null;
                        }

                        if (submissions == null) {
                            Metrics.downloadAction(project, stepNode, DATA_NOT_LOADED);
                            return Collections.emptyList();
                        }

                        SupportedLanguages currentLang = stepNode.getCurrentLang();

                        return filterSubmissions(submissions, currentLang);
                    }
                });

        if (submissions == null) {
            return;
        }

        showPopup(project, stepNode, submissions);
    }

    @Nullable
    private List<Submission> getSubmissions(@NotNull StepNode stepNode) {
        try {
            StepikApiClient stepikApiClient = StepikConnectorLogin.authAndGetStepikApiClient();

            long stepId = stepNode.getId();
            long userId = StepikConnectorLogin.getCurrentUser().getId();

            Submissions submissions = stepikApiClient.submissions()
                    .get()
                    .step(stepId)
                    .user(userId)
                    .order(Order.DESC)
                    .execute();

            return submissions.getSubmissions();
        } catch (StepikClientException e) {
            logger.warn("Failed get submissions", e);
            return null;
        }
    }

    @NotNull
    private List<Submission> filterSubmissions(
            @NotNull List<Submission> submissions,
            @NotNull SupportedLanguages currentLang) {
        return submissions.stream()
                .filter(submission -> SupportedLanguages.langOfName(submission.getReply().getLanguage()) == currentLang)
                .collect(Collectors.toList());
    }

    private void showPopup(
            @NotNull Project project,
            @NotNull StepNode stepNode,
            @NotNull List<Submission> submissions) {
        JBPopupFactory popupFactory = JBPopupFactory.getInstance();

        PopupChooserBuilder builder;
        if (!submissions.isEmpty()) {
            JList<SubmissionDecorator> list;

            List<SubmissionDecorator> submissionDecorators = submissions.stream()
                    .map(SubmissionDecorator::new).collect(Collectors.toList());
            list = new JList<>(submissionDecorators.toArray(new SubmissionDecorator[submissionDecorators.size()]));
            builder = popupFactory.createListPopupBuilder(list)
                    .addListener(new Listener(list, project, stepNode));
        } else {
            JList<String> emptyList = new JList<>(new String[]{"Empty"});
            builder = popupFactory.createListPopupBuilder(emptyList);
        }

        builder = builder.setTitle("Choose submission");

        JBPopup popup = builder.createPopup();

        popup.showCenteredInCurrentWindow(project);
    }

    private void loadSubmission(
            @NotNull Project project,
            @NotNull StepNode stepNode,
            @NotNull Submission submission) {

        String fileName = stepNode.getCurrentLang().getMainFileName();

        String mainFilePath = String.join("/", stepNode.getPath(), EduNames.SRC, fileName);
        VirtualFile mainFile = project.getBaseDir().findFileByRelativePath(mainFilePath);
        if (mainFile == null) {
            Metrics.downloadAction(project, stepNode, TARGET_NOT_FOUND);
            return;
        }

        final String finalCode = submission.getReply().getCode();

        CommandProcessor.getInstance().executeCommand(project,
                () -> ApplicationManager.getApplication().runWriteAction(
                        () -> {
                            FileDocumentManager documentManager = FileDocumentManager.getInstance();
                            Document document = documentManager.getDocument(mainFile);

                            if (document != null) {
                                document.setText(finalCode);
                                stepNode.setStatus(StudyStatus.of(submission.getStatus()));
                                FileEditorManager.getInstance(project).openFile(mainFile, true);
                                ProjectView.getInstance(project).refresh();
                                Metrics.downloadAction(project, stepNode, SUCCESSFUL);
                            }
                        }),
                "Download submission",
                "Download submission");
    }

    private static class SubmissionDecorator {
        private final static SimpleDateFormat timeISOFormat = getTimeISOFormat();
        private final static SimpleDateFormat timeOutFormat = new SimpleDateFormat("dd MMM yyyy HH:mm:ss");
        private final Submission submission;

        SubmissionDecorator(Submission submission) {
            this.submission = submission;
        }

        private static SimpleDateFormat getTimeISOFormat() {
            SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
            TimeZone tz = TimeZone.getTimeZone("UTC");
            format.setTimeZone(tz);
            return format;
        }

        @Override
        public String toString() {
            String localTime;
            String time = submission.getTime();
            try {
                Date utcTime = timeISOFormat.parse(time);
                localTime = timeOutFormat.format(utcTime);
            } catch (ParseException e) {
                localTime = time;
            }

            return String.format("#%d %-7s %s", submission.getId(), submission.getStatus(), localTime);
        }

        Submission getSubmission() {
            return submission;
        }
    }

    private class Listener implements JBPopupListener {
        private final JList<SubmissionDecorator> list;
        private final Project project;
        private final StepNode stepNode;

        Listener(
                @NotNull JList<SubmissionDecorator> list,
                @NotNull Project project,
                @NotNull StepNode stepNode) {
            this.list = list;
            this.project = project;
            this.stepNode = stepNode;
        }

        @Override
        public void beforeShown(LightweightWindowEvent event) {
        }

        @Override
        public void onClosed(LightweightWindowEvent event) {
            if (!event.isOk()) {
                Metrics.downloadAction(project, stepNode, USER_CANCELED);
                return;
            } else if (list.isSelectionEmpty()) {
                Metrics.downloadAction(project, stepNode, EMPTY_SOURCE);
                return;
            }

            Submission submission = list.getSelectedValue().getSubmission();

            if (submission == null) {
                Metrics.downloadAction(project, stepNode, EMPTY_SOURCE);
                return;
            }

            loadSubmission(project, stepNode, submission);
        }
    }
}