package org.stepik.api.actions;

import org.jetbrains.annotations.NotNull;
import org.stepik.api.client.StepikApiClient;

/**
 * @author meanmail
 */
public class StepikCourseGradeBookCsvsAction extends StepikAbstractAction {
    public StepikCourseGradeBookCsvsAction(@NotNull StepikApiClient stepikApiClient) {
        super(stepikApiClient);
    }
}
