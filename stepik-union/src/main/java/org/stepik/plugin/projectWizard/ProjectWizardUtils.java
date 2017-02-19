package org.stepik.plugin.projectWizard;

import com.intellij.openapi.diagnostic.Logger;
import com.jetbrains.tmp.learning.stepik.StepikConnectorLogin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.stepik.api.client.StepikApiClient;
import org.stepik.api.exceptions.StepikClientException;
import org.stepik.api.objects.StudyObject;
import org.stepik.api.objects.courses.Course;
import org.stepik.api.objects.courses.Courses;
import org.stepik.api.objects.lessons.CompoundUnitLesson;
import org.stepik.api.objects.sections.Section;
import org.stepik.api.objects.sections.Sections;
import org.stepik.api.objects.steps.Step;

import java.io.File;

/**
 * @author meanmail
 */
public class ProjectWizardUtils {
    private static final Logger logger = Logger.getInstance(ProjectWizardUtils.class);

    @NotNull
    private static String findNonExistingFileName(String searchDirectory, String preferredName) {
        String fileName = preferredName;
        int idx = 1;

        while (new File(searchDirectory, fileName).exists()) {
            fileName = preferredName + "_" + idx;
            ++idx;
        }

        return fileName;
    }

    @Nullable
    public static String getProjectDefaultName(String projectDirectory, StudyObject studyObject) {
        long id = studyObject.getId();
        String projectName = null;
        if (studyObject instanceof Course) {
            projectName = "course" + id;
        } else if (studyObject instanceof CompoundUnitLesson) {
            projectName = "lesson" + id;
        } else if (studyObject instanceof Section) {
            projectName = "section" + id;
        } else if (studyObject instanceof Step) {
            projectName = "step" + id;
        }

        if (projectName != null) {
            projectName = findNonExistingFileName(projectDirectory, projectName);
        }
        return projectName;
    }

    public static void enrollmentCourse(StudyObject studyObject) {
        if (studyObject instanceof Course) {
            ProjectWizardUtils.enrollment(studyObject);
        } else if (studyObject instanceof CompoundUnitLesson) {
            enrollment((CompoundUnitLesson) studyObject);
        }
    }

    private static void enrollment(CompoundUnitLesson studyObject) {
        int sectionId = studyObject.getUnit().getSection();
        if (sectionId != 0) {
            StepikApiClient stepikApiClient = StepikConnectorLogin.authAndGetStepikApiClient();
            try {
                Sections sections = stepikApiClient.sections()
                        .get()
                        .id(sectionId)
                        .execute();

                if (!sections.isEmpty()) {
                    long courseId = sections.getSections().get(0).getId();

                    if (courseId != 0) {
                        Courses courses = stepikApiClient.courses()
                                .get()
                                .id(courseId)
                                .execute();
                        if (!courses.isEmpty()) {
                            enrollment(courses.getCourses().get(0));
                        }
                    }
                }
            } catch (StepikClientException e) {
                String messageTemplate = "Can't enrollment on a lesson: id = %s, name = %s";
                String message = String.format(messageTemplate, studyObject.getId(), studyObject.getTitle());
                logger.error(message, e);
            }
        }
    }

    private static void enrollment(StudyObject studyObject) {
        long id = studyObject.getId();
        StepikApiClient stepikApiClient = StepikConnectorLogin.authAndGetStepikApiClient();
        try {
            stepikApiClient.enrollments()
                    .post()
                    .course(id)
                    .execute();
        } catch (StepikClientException e) {
            String messageTemplate = "Can't enrollment on a course: id = %s, name = %s";
            String message = String.format(messageTemplate, id, studyObject.getTitle());
            logger.error(message, e);
        }
    }
}
