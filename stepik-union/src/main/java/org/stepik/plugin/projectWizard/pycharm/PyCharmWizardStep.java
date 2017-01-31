package org.stepik.plugin.projectWizard.pycharm;

import com.intellij.facet.ui.ValidationResult;
import com.intellij.openapi.project.Project;
import com.jetbrains.tmp.learning.SupportedLanguages;
import org.jetbrains.annotations.NotNull;
import org.stepik.api.objects.courses.Course;
import org.stepik.plugin.projectWizard.ui.ProjectSettingListener;
import org.stepik.plugin.projectWizard.ui.ProjectSettingsPanel;

import javax.swing.*;

class PyCharmWizardStep implements ProjectSettingListener {
    private final ValidationResult invalidCourse = new ValidationResult("Please select a course");
    private final ValidationResult adaptiveCourse = new ValidationResult(
            "Sorry, but we don't support adaptive courses yet");
    private final StepikPyProjectGenerator generator;
    private final ProjectSettingsPanel panel;

    PyCharmWizardStep(@NotNull StepikPyProjectGenerator generator, @NotNull Project project) {
        this.generator = generator;
        panel = new ProjectSettingsPanel(project, false);
        panel.addListener(this);
        panel.setLanguage(SupportedLanguages.PYTHON3);
    }

    @NotNull
    Course getSelectedCourse() {
        return panel.getSelectedCourse();
    }

    @NotNull
    JPanel getComponent() {
        return panel.getComponent();
    }

    @NotNull
    ValidationResult check() {
        Course selectedCourse = panel.getSelectedCourse();

        if (selectedCourse.isAdaptive()) {
            return adaptiveCourse;
        }
        if (selectedCourse.getId() == 0) {
            return invalidCourse;
        }
        return ValidationResult.OK;
    }

    @Override
    public void changed() {
        generator.fireStateChanged();
    }

    void updateStep() {
        panel.updateStep();
    }
}