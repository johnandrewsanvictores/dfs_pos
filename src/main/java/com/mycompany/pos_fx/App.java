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
import pos.db.CashierDAO;

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
        scene.setFill(Color.WHITE);
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
        loginView.setOnLoginSuccess((cashierName, username) -> {
            int staffId = -1;
            try {
                staffId = CashierDAO.getStaffIdByUsername(username);
            } catch (Exception e) {
                e.printStackTrace();
            }
            final int finalStaffId = staffId;
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
                        // Log logout activity
                        new Thread(() -> {
                            try { CashierDAO.logActivity(finalStaffId, "login_logout", username + " (cashier) logged out"); } catch (Exception ignored) {}
                        }).start();
                        loginView.clearFields();
                        stage.getScene().setRoot(root);
                        Scene currentScene = stage.getScene();
                        if (currentScene != null) {
                            currentScene.getStylesheets().clear();
                            currentScene.getStylesheets().add(getClass().getResource("/login.css").toExternalForm());
                        }
                        stage.setTitle("Dream Fashion Shop - POS");
                        stage.setMaximized(false);
                    }
                });
            };
            // Update last_login in DB (background thread)
            new Thread(() -> {
                try { 
                    CashierDAO.updateLastLogin(username); 
                    CashierDAO.logActivity(finalStaffId, "login_logout", username + " (cashier) logged in");
                } catch (Exception ignored) {}
            }).start();
            POSView posView = new POSView(logoutCallback, cashierName, staffId);
            posView.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
            posView.setPrefSize(Region.USE_COMPUTED_SIZE, Region.USE_COMPUTED_SIZE);

            StackPane posRoot = new StackPane(posView);
            posRoot.setStyle("-fx-background-color: #fff;");
            StackPane.setAlignment(posView, Pos.CENTER);
            posRoot.setPrefSize(Region.USE_COMPUTED_SIZE, Region.USE_COMPUTED_SIZE);
            posRoot.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);

            stage.getScene().setRoot(posRoot);
            Scene currentScene = stage.getScene();
            if (currentScene != null) {
                currentScene.getStylesheets().clear();
                currentScene.getStylesheets().add(getClass().getResource("/style.css").toExternalForm());
                currentScene.setFill(Color.WHITE);
            }

            stage.setTitle("Dream Fashion Shop - POS System");
            stage.setResizable(true);
            stage.setMinWidth(1200);
            stage.setMinHeight(700);
            if (!stage.isMaximized()) {
                stage.setMaximized(true);
            }
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