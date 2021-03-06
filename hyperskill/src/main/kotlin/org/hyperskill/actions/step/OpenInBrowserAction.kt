package org.hyperskill.actions.step

import com.intellij.openapi.project.Project
import org.hyperskill.api.objects.lesson.HSLesson
import org.stepik.core.actions.step.AbstractOpenInBrowserAction
import org.stepik.core.courseFormat.StudyNode
import org.stepik.core.host

class OpenInBrowserAction : AbstractOpenInBrowserAction() {
    
    override fun getLink(project: Project, stepNode: StudyNode): String {
        val lessonId = (stepNode.parent?.data as? HSLesson)?.stepikId ?: return host
        return "$host/learn/lesson/$lessonId"
    }
    
}
