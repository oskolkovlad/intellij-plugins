package org.stepik.core.actions

import com.intellij.execution.ui.ConsoleViewContentType.ERROR_OUTPUT
import com.intellij.ide.projectView.ProjectView
import com.intellij.openapi.application.ApplicationManager.getApplication
import com.intellij.openapi.project.Project
import org.stepik.api.client.StepikApiClient
import org.stepik.api.objects.submissions.Submission
import org.stepik.core.common.Loggable
import org.stepik.core.courseFormat.StepNode
import org.stepik.core.courseFormat.StudyStatus
import org.stepik.core.courseFormat.StudyStatus.SOLVED
import org.stepik.core.getProjectManager
import org.stepik.core.metrics.Metrics
import org.stepik.core.metrics.MetricsStatus.SUCCESSFUL
import org.stepik.core.metrics.MetricsStatus.TIME_OVER
import org.stepik.core.testFramework.toolWindow.StepikTestResultToolWindow

object SendAction : Loggable {
    private const val EVALUATION = "evaluation"
    private const val PERIOD = 2 * 1000L //ms
    private const val FIVE_MINUTES = 5 * MILLISECONDS_IN_MINUTES //ms
    
    fun checkStepStatus(
            project: Project,
            stepikApiClient: StepikApiClient,
            stepNode: StepNode,
            submissionId: Long,
            resultWindow: StepikTestResultToolWindow) {
        val stepIdString = "id=${stepNode.id}"
        logger.info("Started check a status for step: $stepIdString")
        var stepStatus = EVALUATION
        var timer = 0L
        var showedTimer = false
        
        var currentSubmission: Submission? = null
        while (timer < FIVE_MINUTES) {
            try {
                val submission = stepikApiClient.submissions()
                        .get()
                        .id(submissionId)
                        .execute()
                
                if (submission.isNotEmpty) {
                    currentSubmission = submission.first()
                    if (showedTimer) {
                        resultWindow.clearLastLine()
                    } else {
                        showedTimer = true
                    }
                    setupCheckProgress(resultWindow, currentSubmission, timer)
                    stepStatus = currentSubmission.status
                    if (stepStatus != EVALUATION) {
                        if (showedTimer) {
                            resultWindow.clearLastLine()
                        }
                        break
                    }
                }
                
                Thread.sleep(PERIOD)
                timer += PERIOD
            } catch (e: Exception) {
                if (showedTimer) {
                    resultWindow.clearLastLine()
                }
                resultWindow.apply {
                    println("Error: can't get submission status", ERROR_OUTPUT)
                    println(e.message ?: "Unknown error", ERROR_OUTPUT)
                }
                logger.info("Stop check a status for step: $stepIdString", e)
                return
            }
        }
        
        if (currentSubmission == null) {
            logger.info("Stop check a status for step: $stepIdString without result")
            return
        }
        val actionStatus = if (stepStatus == EVALUATION) TIME_OVER else SUCCESSFUL
        Metrics.getStepStatusAction(project, stepNode, actionStatus)
        
        if (StudyStatus.of(stepStatus) == SOLVED) {
            stepNode.passed()
        }
        resultWindow.apply {
            println(stepStatus)
            println(currentSubmission.hint)
        }
        getApplication().invokeLater {
            if (!project.isDisposed) {
                ProjectView.getInstance(project)
                        .refresh()
            }
            getProjectManager(project)?.updateSelection()
        }
        logger.info("Finish check a status for step: $stepIdString with status: $stepStatus")
    }
}
