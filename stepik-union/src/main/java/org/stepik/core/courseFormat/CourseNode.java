package org.stepik.core.courseFormat;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.stepik.api.client.StepikApiClient;
import org.stepik.api.exceptions.StepikClientException;
import org.stepik.api.objects.courses.Course;
import org.stepik.api.objects.courses.Courses;
import org.stepik.api.objects.sections.Section;
import org.stepik.api.objects.sections.Sections;
import org.stepik.api.objects.users.User;
import org.stepik.api.objects.users.Users;
import org.stepik.core.stepik.StepikConnectorLogin;

import java.util.Collections;
import java.util.List;

import static org.stepik.core.stepik.StepikConnectorLogin.authAndGetStepikApiClient;

public class CourseNode extends Node<Course, SectionNode, Section, LessonNode> {
    private static final Logger logger = Logger.getInstance(CourseNode.class);
    private List<User> authors;

    public CourseNode() {
    }

    public CourseNode(@NotNull Project project, @NotNull Course data) {
        super(project, data);
    }

    @Override
    protected List<Section> getChildDataList() {
        Sections sections = new Sections();
        try {
            StepikApiClient stepikApiClient = authAndGetStepikApiClient();
            Course data = getData();
            List<Long> sectionsIds = data != null ? getData().getSections() : Collections.emptyList();
            if (!sectionsIds.isEmpty()) {
                sections = stepikApiClient.sections()
                        .get()
                        .id(sectionsIds)
                        .execute();


            }
        } catch (StepikClientException logged) {
            logger.warn("A course initialization don't is fully", logged);
        }

        return sections.getSections();
    }

    @Override
    public void init(@NotNull Project project, @Nullable StudyNode parent) {
        authors = null;
        super.init(project, parent);
    }

    @Override
    protected boolean loadData(long id) {
        try {
            StepikApiClient stepikApiClient = StepikConnectorLogin.authAndGetStepikApiClient();
            Courses courses = stepikApiClient.courses()
                    .get()
                    .id(id)
                    .execute();

            Course data;

            if (!courses.isEmpty()) {
                data = courses.getCourses().get(0);
            } else {
                data = new Course();
                data.setId(id);
            }
            setData(data);

            Course oldData = this.getData();
            return oldData == null || !oldData.getUpdateDate().equals(data.getUpdateDate());
        } catch (StepikClientException logged) {
            logger.warn(String.format("Failed load course data id=%d", id), logged);
        }
        return true;
    }

    @Override
    protected Class<SectionNode> getChildClass() {
        return SectionNode.class;
    }

    @Override
    protected Class<Section> getChildDataClass() {
        return Section.class;
    }

    @Override
    protected Class<Course> getDataClass() {
        return Course.class;
    }

    @SuppressWarnings("unused")
    @NotNull
    public List<User> getAuthors() {
        if (authors == null) {
            List<Long> authorsIds;
            Course data = getData();
            authorsIds = data != null ? data.getAuthors() : Collections.emptyList();
            if (!authorsIds.isEmpty()) {
                try {
                    Users users = authAndGetStepikApiClient().users()
                            .get()
                            .id(authorsIds)
                            .execute();
                    authors = users.getUsers();
                } catch (StepikClientException e) {
                    return Collections.emptyList();
                }
            }
        }
        return authors != null ? authors : Collections.emptyList();
    }

    @Override
    public long getCourseId() {
        return getId();
    }
}