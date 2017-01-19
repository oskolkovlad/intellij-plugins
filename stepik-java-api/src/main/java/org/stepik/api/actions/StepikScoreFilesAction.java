package org.stepik.api.actions;

import org.jetbrains.annotations.NotNull;
import org.stepik.api.client.StepikApiClient;

/**
 * @author meanmail
 */
public class StepikScoreFilesAction extends StepikAbstractAction {
    public StepikScoreFilesAction(@NotNull StepikApiClient stepikApiClient) {
        super(stepikApiClient);
    }
}
