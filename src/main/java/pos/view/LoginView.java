package pos.view;

import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.shape.Line;
import pos.db.CashierDAO;
import java.sql.ResultSet;
import java.sql.SQLException;
import org.mindrot.jbcrypt.BCrypt;
import javafx.application.Platform;

public class LoginView extends VBox {
    private final TextField usernameField;
    private final PasswordField passwordField;
    private final Label errorLabel;
    private OnLoginSuccessListener onLoginSuccess;
    private final ProgressIndicator loader = new ProgressIndicator();

    public interface OnLoginSuccessListener {
        void onLoginSuccess(String fullName, String username);
    }

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

        loader.setVisible(false);
        loader.setPrefSize(30, 30);

        loginBtn.setOnAction(e -> handleLogin());
        // Allow Enter key to move from username to password, and authenticate from password
        usernameField.setOnAction(e -> passwordField.requestFocus());
        passwordField.setOnAction(e -> {
            if (!usernameField.getText().trim().isEmpty()) {
                handleLogin();
            }
        });

        loginForm.getChildren().addAll(accentLine, loginTitle, usernameBox, passwordBox, loginBtn, errorLabel, loader);
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
        errorLabel.setText("");
        Platform.runLater(() -> loader.setVisible(true));
        // Run DB check in background thread
        new Thread(() -> {
            try {
                ResultSet rs = CashierDAO.getActiveCashierByUsername(user);
                if (rs.next()) {
                    String hash = rs.getString("password");
                    if (hash.startsWith("$2y$")) {
                        hash = "$2a$" + hash.substring(4);
                    }
                    if (BCrypt.checkpw(pass, hash)) {
                        String firstName = rs.getString("first_name");
                        String lastName = rs.getString("last_name");
                        // Proper title casing for full name
                        firstName = toTitleCase(firstName);
                        lastName = toTitleCase(lastName);
                        String fullName = firstName + " " + lastName;
                        Platform.runLater(() -> {
                            loader.setVisible(false);
                            errorLabel.setText("");
                            if (onLoginSuccess != null) onLoginSuccess.onLoginSuccess(fullName, user);
                        });
                    } else {
                        Platform.runLater(() -> {
                            loader.setVisible(false);
                            errorLabel.setText("Invalid username or password.");
                        });
                    }
                } else {
                    Platform.runLater(() -> {
                        loader.setVisible(false);
                        errorLabel.setText("Invalid username or password.");
                    });
                }
                rs.getStatement().getConnection().close();
            } catch (SQLException ex) {
                Platform.runLater(() -> {
                    loader.setVisible(false);
                    errorLabel.setText("Database error: " + ex.getMessage());
                });
            }
        }).start();
    }

    public void setOnLoginSuccess(OnLoginSuccessListener callback) {
        this.onLoginSuccess = callback;
    }

    public void clearFields() {
        usernameField.clear();
        passwordField.clear();
        errorLabel.setText("");
    }

    // Add a helper for title case
    private static String toTitleCase(String input) {
        if (input == null || input.isEmpty()) return input;
        StringBuilder result = new StringBuilder(input.length());
        boolean capitalize = true;
        for (char c : input.toCharArray()) {
            if (Character.isWhitespace(c)) {
                capitalize = true;
                result.append(c);
            } else if (capitalize) {
                result.append(Character.toUpperCase(c));
                capitalize = false;
            } else {
                result.append(Character.toLowerCase(c));
            }
        }
        return result.toString();
    }
} 