package com.nemostudio.ide;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import javafx.scene.web.WebView;

import java.net.URL;

/**
 * Entry point for Nemo Studio Desktop — a basic demo IDE.
 */
public class NemoStudioApp extends Application {

    @Override
    public void start(Stage primaryStage) {
        IdeView ideView = new IdeView();
        StackPane root = new StackPane();

        // Background: workflow diagram SVG loaded via HTML from same origin so the image loads
        URL htmlUrl = getClass().getResource("/diagram-background.html");
        if (htmlUrl != null) {
            WebView webView = new WebView();
            webView.setMouseTransparent(true);
            webView.setPrefSize(1000, 700);
            webView.setMinSize(0, 0);
            webView.getEngine().load(htmlUrl.toExternalForm());
            root.getChildren().add(webView);
        }

        // Main UI on top; transparent so diagram shows where there is no content
        ideView.getRoot().setStyle("-fx-background-color: transparent;");
        root.getChildren().add(ideView.getRoot());

        Scene scene = new Scene(root, 1000, 700);
        scene.getStylesheets().add(getClass().getResource("/styles/ide.css").toExternalForm());
        primaryStage.setTitle("Nemo Studio");
        primaryStage.setScene(scene);
        primaryStage.setMinWidth(640);
        primaryStage.setMinHeight(480);
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
