package com.nemostudio.ide;

import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.util.List;

/**
 * Dummy window for a workflow step: shows the step name and associated tools as in the workflow diagram.
 */
public class WorkflowStepWindow {

    /**
     * Opens a new window for the given workflow step.
     *
     * @param stepTitle Main label (e.g. "Define Use Case", "Prepare, Generate Data")
     * @param tools      Associated tools (e.g. "NeMo Curator", "NeMo Data Designer"); may be empty
     */
    public static void show(String stepTitle, List<String> tools) {
        Stage stage = new Stage();
        stage.setTitle(stepTitle + " — Nemo Studio");

        Label titleLabel = new Label(stepTitle);
        titleLabel.getStyleClass().add("workflow-step-title");
        titleLabel.setWrapText(true);

        VBox content = new VBox(12);
        content.getStyleClass().add("workflow-step-content");
        content.setPadding(new Insets(24));
        content.getChildren().add(titleLabel);

        if (tools != null && !tools.isEmpty()) {
            Label toolsHeader = new Label("Associated tools");
            toolsHeader.getStyleClass().add("workflow-tools-header");
            content.getChildren().add(toolsHeader);
            for (String tool : tools) {
                Label toolLabel = new Label("• " + tool);
                toolLabel.getStyleClass().add("workflow-tool");
                toolLabel.setWrapText(true);
                content.getChildren().add(toolLabel);
            }
        } else {
            Label placeholder = new Label("(No associated tools for this step)");
            placeholder.getStyleClass().add("workflow-tool-placeholder");
            content.getChildren().add(placeholder);
        }

        Scene scene = new Scene(content, 420, 320);
        java.net.URL cssUrl = WorkflowStepWindow.class.getResource("/styles/ide.css");
        if (cssUrl != null) {
            scene.getStylesheets().add(cssUrl.toExternalForm());
        }
        stage.setScene(scene);
        stage.setMinWidth(360);
        stage.setMinHeight(240);
        stage.show();
    }
}
