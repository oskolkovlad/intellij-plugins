package org.stepik.plugin.collective.ui;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import org.jetbrains.annotations.NotNull;
import org.stepik.core.StepikProjectManager;
import org.stepik.core.stepik.StepikAuthManager;
import org.stepik.core.stepik.StepikAuthManagerListener;
import org.stepik.core.stepik.StepikAuthState;
import org.stepik.core.utils.Utils;

import javax.swing.*;

import static org.stepik.core.stepik.StepikAuthManager.isAuthenticated;
import static org.stepik.core.stepik.StepikAuthState.AUTH;
import static org.stepik.core.stepik.StepikAuthState.NOT_AUTH;

class StepikSettingsPanel implements StepikAuthManagerListener {
    private JPanel pane;
    private JCheckBox hintCheckBox;
    private JButton logoutButton;
    private JLabel userName;
    private JButton loginButton;

    private boolean hintCheckBoxModified;
    private StepikProjectManager projectManager;

    StepikSettingsPanel() {
        initProjectOfSettings();
        hintCheckBox.addActionListener(e -> hintCheckBoxModified = true);
        logoutButton.addActionListener(e -> StepikAuthManager.logout());
        loginButton.addActionListener(e -> StepikAuthManager.logoutAndAuth());

        StepikAuthManager.addListener(this);
    }

    private void updateUserName() {
        userName.setText(StepikAuthManager.getCurrentUserFullName());
    }

    JComponent getPanel() {
        return pane;
    }

    private void initProjectOfSettings() {
        projectManager = StepikProjectManager.getInstance(Utils.getCurrentProject());
        hintCheckBox.setSelected(projectManager != null && projectManager.getShowHint());
        logoutButton.setEnabled(isAuthenticated());
    }

    void reset() {
        initProjectOfSettings();
        resetModification();
        updateUserName();
    }

    void apply() {
        if (hintCheckBoxModified && projectManager != null) {
            projectManager.setShowHint(hintCheckBox.isSelected());
        }
        resetModification();
    }

    boolean isModified() {
        return hintCheckBoxModified;
    }

    private void resetModification() {
        hintCheckBoxModified = false;
    }

    @Override
    public void stateChanged(@NotNull StepikAuthState oldState, @NotNull StepikAuthState newState) {
        if (newState == NOT_AUTH || newState == AUTH) {
            ApplicationManager.getApplication().invokeLater(() -> {
                updateUserName();
                logoutButton.setEnabled(newState == AUTH);
            }, ModalityState.stateForComponent(pane));
        }
    }

    void dispose() {
        StepikAuthManager.removeListener(this);
    }
}
