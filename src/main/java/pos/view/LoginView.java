package pos.view;

import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.shape.Line;

public class LoginView extends VBox {
    private final TextField usernameField;
    private final PasswordField passwordField;
    private final Label errorLabel;
    private Runnable onLoginSuccess;

    public LoginView() {
        super(30);
        setAlignment(Pos.CENTER);
        getStyleClass().add("main-content");

        VBox loginForm = new VBox(25);
        loginForm.setMaxWidth(300);
        loginForm.setAlignment(Pos.CENTER);

        Line accentLine = new Line(0, 0, 120, 0);
        accentLine.getStyleClass().add("accent-line");

        Label loginTitle = new Label("CASHIER LOGIN");
        loginTitle.getStyleClass().add("login-title");

        HBox usernameBox = createInputField("Username", "/img/user.png");
        usernameField = (TextField) usernameBox.getChildren().get(1);
        HBox passwordBox = createPasswordField("Password", "/img/padlock.png");
        passwordField = (PasswordField) passwordBox.getChildren().get(1);

        Button loginBtn = new Button("AUTHENTICATE");
        loginBtn.getStyleClass().add("login-btn");

        errorLabel = new Label();
        errorLabel.setStyle("-fx-text-fill: #d32f2f; -fx-font-size: 13px;");

        loginBtn.setOnAction(e -> handleLogin());

        loginForm.getChildren().addAll(accentLine, loginTitle, usernameBox, passwordBox, loginBtn, errorLabel);
        getChildren().add(loginForm);
    }

    private HBox createInputField(String prompt, String iconPath) {
        HBox container = new HBox(10);
        container.setAlignment(Pos.CENTER_LEFT);
        ImageView iconView = new ImageView(getClass().getResource(iconPath).toExternalForm());
        iconView.setFitWidth(20);
        iconView.setFitHeight(20);
        iconView.getStyleClass().add("input-icon");
        TextField field = new TextField();
        field.setPromptText(prompt);
        field.getStyleClass().add("input-field");
        container.getChildren().addAll(iconView, field);
        return container;
    }

    private HBox createPasswordField(String prompt, String iconPath) {
        HBox container = new HBox(10);
        container.setAlignment(Pos.CENTER_LEFT);
        ImageView iconView = new ImageView(getClass().getResource(iconPath).toExternalForm());
        iconView.setFitWidth(20);
        iconView.setFitHeight(20);
        iconView.getStyleClass().add("input-icon");
        PasswordField field = new PasswordField();
        field.setPromptText(prompt);
        field.getStyleClass().add("input-field");
        container.getChildren().addAll(iconView, field);
        return container;
    }

    private void handleLogin() {
        String user = usernameField.getText();
        String pass = passwordField.getText();
        if ("admin".equals(user) && "password123".equals(pass)) {
            errorLabel.setText("");
            if (onLoginSuccess != null) {
                onLoginSuccess.run();
            }
        } else {
            errorLabel.setText("Invalid username or password.");
        }
    }

    public void setOnLoginSuccess(Runnable callback) {
        this.onLoginSuccess = callback;
    }

    public void clearFields() {
        usernameField.clear();
        passwordField.clear();
        errorLabel.setText("");
    }
} 