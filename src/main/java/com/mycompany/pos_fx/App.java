package com.mycompany.pos_fx;

import javafx.application.Application;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import pos.view.LoginView;
import pos.view.POSView;

public class App extends Application {

    @Override
    public void start(Stage stage) {
        BorderPane root = new BorderPane();
        root.getStyleClass().add("root-container");

        HBox header = createHeader();
        VBox mainContent = createMainContent(stage, root, header);
        Label footer = createFooter();

        root.setTop(header);
        root.setCenter(mainContent);
        root.setBottom(footer);

        Scene scene = new Scene(root, 800, 600);
        scene.setFill(Color.TRANSPARENT);
        scene.getStylesheets().add(getClass().getResource("/login.css").toExternalForm());

        configureStage(stage, scene);
        makeDraggable(stage, header);
    }

    private HBox createHeader() {
        HBox header = new HBox();
        header.getStyleClass().add("header");
        Label title = new Label("Dream Fashion Shop - POS");
        title.getStyleClass().add("title");
        header.getChildren().addAll(title);
        HBox.setHgrow(title, Priority.ALWAYS);
        return header;
    }

    private VBox createMainContent(Stage stage, BorderPane root, HBox header) {
        VBox mainContent = new VBox(30);
        mainContent.setAlignment(Pos.CENTER);
        mainContent.getStyleClass().add("main-content");
        LoginView loginView = new LoginView();
        mainContent.getChildren().add(loginView);
        setupLoginHandler(stage, mainContent, loginView, root, header);
        return mainContent;
    }

    private Label createFooter() {
        Label footer = new Label("© 2025 Dream Fashion Shop • Secure Login");
        footer.getStyleClass().add("footer");
        return footer;
    }

    private void configureStage(Stage stage, Scene scene) {
        stage.initStyle(StageStyle.DECORATED);
        stage.setScene(scene);
        stage.setResizable(true);
        stage.setMinWidth(1200);
        stage.setMinHeight(700);
        stage.show();
    }

    private void setupLoginHandler(Stage stage, VBox mainContent, LoginView loginView, BorderPane root, HBox header) {
        Scene loginScene = stage.getScene();
        loginView.setOnLoginSuccess(() -> {
            Runnable logoutCallback = () -> {
                Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
                alert.setTitle("Confirm Logout");
                alert.setHeaderText(null);
                alert.setContentText("Are you sure you want to log out?");
                ButtonType yesBtn = new ButtonType("Yes", ButtonBar.ButtonData.YES);
                ButtonType noBtn = new ButtonType("No", ButtonBar.ButtonData.NO);
                alert.getButtonTypes().setAll(yesBtn, noBtn);
                alert.showAndWait().ifPresent(type -> {
                    if (type == yesBtn) {
                        loginView.clearFields();
                        mainContent.getChildren().setAll(loginView);
                        stage.setScene(loginScene);
                        stage.setTitle("Dream Fashion Shop - POS");
                        stage.setMaximized(false);
                    }
                });
            };
            POSView posView = new POSView(logoutCallback);
            Scene posScene = new Scene(posView);
            posScene.getStylesheets().add(getClass().getResource("/style.css").toExternalForm());
            stage.setScene(posScene);
            stage.setTitle("Dream Fashion Shop - POS System");
            stage.setResizable(true);
            stage.setMinWidth(1200);
            stage.setMinHeight(700);
            stage.setMaximized(true);
        });
    }
    
    private void makeDraggable(Stage stage, Node node) {
        final Delta dragDelta = new Delta();
        
        node.setOnMousePressed(mouseEvent -> {
            dragDelta.x = stage.getX() - mouseEvent.getScreenX();
            dragDelta.y = stage.getY() - mouseEvent.getScreenY();
        });
        
        node.setOnMouseDragged(mouseEvent -> {
            stage.setX(mouseEvent.getScreenX() + dragDelta.x);
            stage.setY(mouseEvent.getScreenY() + dragDelta.y);
        });
    }
    
    private static class Delta {
        double x, y;
    }

    public static void main(String[] args) {
        launch();
    }
}