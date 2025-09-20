package pos.view;

import javafx.application.Platform;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import pos.db.DBConnection;

public class SupervisorAuthDialog extends Stage {
    
    private boolean authenticationSuccessful = false;
    private int authenticatedSupervisorId = -1;
    private TextField usernameField;
    private PasswordField passwordField;
    private Label errorLabel;
    private Button authorizeButton;
    private ProgressIndicator loadingIndicator;
    private HBox loadingBox;
    
    public SupervisorAuthDialog() {
        initializeDialog();
        createContent();
    }
    
    private void initializeDialog() {
        setTitle("Supervisor Authorization Required");
        initModality(Modality.APPLICATION_MODAL);
        initStyle(StageStyle.DECORATED);
        setResizable(false);
        setMinWidth(360);
        setMinHeight(240);
    }
    
    private void createContent() {
        VBox mainLayout = new VBox(12);
        mainLayout.setPadding(new Insets(18));
        mainLayout.setStyle("-fx-background-color: white;");
        mainLayout.setFillWidth(true);
        
        // Header
        Label headerLabel = new Label("Supervisor Authorization");
        headerLabel.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: #333;");
        
        Label infoLabel = new Label("Please enter admin credentials to authorize this refund:");
        infoLabel.setStyle("-fx-font-size: 14px; -fx-text-fill: #666;");
        infoLabel.setWrapText(true);
        
        // Form
        GridPane formGrid = new GridPane();
        formGrid.setHgap(10);
        formGrid.setVgap(10);
        formGrid.setAlignment(Pos.CENTER);
        
        Label usernameLabel = new Label("Username:");
        usernameLabel.setStyle("-fx-font-weight: bold;");
        usernameField = new TextField();
        usernameField.setPromptText("Enter admin username");
        usernameField.setPrefWidth(180);
        
        Label passwordLabel = new Label("Password:");
        passwordLabel.setStyle("-fx-font-weight: bold;");
        passwordField = new PasswordField();
        passwordField.setPromptText("Enter admin password");
        passwordField.setPrefWidth(180);
        
        formGrid.add(usernameLabel, 0, 0);
        formGrid.add(usernameField, 1, 0);
        formGrid.add(passwordLabel, 0, 1);
        formGrid.add(passwordField, 1, 1);
        
        // Error label
        errorLabel = new Label();
        errorLabel.setStyle("-fx-text-fill: #d32f2f; -fx-font-size: 12px;");
        errorLabel.setVisible(false);
        
        // Loading indicator (initially hidden)
        loadingIndicator = new ProgressIndicator();
        loadingIndicator.setMaxSize(30, 30);
        loadingIndicator.setVisible(false);
        
        // Center the loading indicator
        loadingBox = new HBox();
        loadingBox.setAlignment(Pos.CENTER);
        loadingBox.getChildren().add(loadingIndicator);
        loadingBox.setVisible(false);
        
        // Buttons
        HBox buttonBox = new HBox(12);
        buttonBox.setAlignment(Pos.CENTER_RIGHT);
        buttonBox.setPadding(new Insets(5, 0, 0, 0));
        
        Button cancelButton = new Button("Cancel");
        cancelButton.setStyle("-fx-background-color: #757575; -fx-text-fill: white; -fx-padding: 8 20; -fx-background-radius: 5;");
        cancelButton.setOnAction(e -> {
            authenticationSuccessful = false;
            close();
        });
        
        authorizeButton = new Button("Authorize");
        authorizeButton.setStyle("-fx-background-color: #1976d2; -fx-text-fill: white; -fx-padding: 8 20; -fx-background-radius: 5;");
        authorizeButton.setOnAction(e -> handleAuthorization());
        
        // Make authorize button default
        authorizeButton.setDefaultButton(true);
        
        // Enter key handling
        passwordField.setOnAction(e -> handleAuthorization());
        
        buttonBox.getChildren().addAll(cancelButton, authorizeButton);
        
        mainLayout.getChildren().addAll(
            headerLabel,
            infoLabel,
            formGrid,
            errorLabel,
            loadingBox,
            buttonBox
        );
        
        Scene scene = new Scene(mainLayout, 380, 260);
        setScene(scene);
        
        // Focus username field
        Platform.runLater(() -> usernameField.requestFocus());
    }
    
    private void handleAuthorization() {
        String username = usernameField.getText().trim();
        String password = passwordField.getText();
        
        if (username.isEmpty() || password.isEmpty()) {
            showError("Please enter both username and password.");
            return;
        }
        
        // Show loading indicator and disable button
        loadingBox.setVisible(true);
        authorizeButton.setDisable(true);
        authorizeButton.setText("Authenticating...");
        errorLabel.setVisible(false);
        
        // Run authentication in background thread
        javafx.concurrent.Task<Boolean> authTask = new javafx.concurrent.Task<Boolean>() {
            @Override
            protected Boolean call() throws Exception {
                // Add slight delay for better UX
                Thread.sleep(800);
                return authenticateAdmin(username, password);
            }
        };
        
        authTask.setOnSucceeded(e -> {
            Platform.runLater(() -> {
                loadingBox.setVisible(false);
                authorizeButton.setDisable(false);
                authorizeButton.setText("Authorize");
                
                if (authTask.getValue()) {
                    authenticationSuccessful = true;
                    close();
                } else {
                    showError("Invalid credentials. Please check username and password.");
                    passwordField.clear();
                    passwordField.requestFocus();
                }
            });
        });
        
        authTask.setOnFailed(e -> {
            Platform.runLater(() -> {
                loadingBox.setVisible(false);
                authorizeButton.setDisable(false);
                authorizeButton.setText("Authorize");
                showError("Authentication failed. Please try again.");
                passwordField.clear();
                passwordField.requestFocus();
            });
        });
        
        new Thread(authTask).start();
    }
    
    private boolean authenticateAdmin(String username, String password) {
        try (java.sql.Connection conn = DBConnection.getConnection()) {
            String sql = "SELECT * FROM staff_acc WHERE username = ? AND role = 'admin' AND status = 'active'";
            java.sql.PreparedStatement stmt = conn.prepareStatement(sql);
            stmt.setString(1, username);
            java.sql.ResultSet rs = stmt.executeQuery();
            
            if (rs.next()) {
                String storedHash = rs.getString("password");
                int staffId = rs.getInt("id");
                
                // Check if password is already hashed (BCrypt hashes start with $2a$, $2b$, $2y$)
                if (storedHash.startsWith("$2") && storedHash.length() == 60) {
                    try {
                        // Fix salt revision compatibility issue (same as LoginView)
                        if (storedHash.startsWith("$2y$")) {
                            storedHash = "$2a$" + storedHash.substring(4);
                        }
                        boolean isValid = org.mindrot.jbcrypt.BCrypt.checkpw(password, storedHash);
                        if (isValid) {
                            authenticatedSupervisorId = staffId; // Store the staff ID
                        }
                        return isValid;
                    } catch (IllegalArgumentException e) {
                        System.err.println("BCrypt verification failed: " + e.getMessage());
                        return false;
                    }
                } else {
                    // Plain text comparison for non-hashed passwords
                    boolean isValid = password.equals(storedHash);
                    if (isValid) {
                        authenticatedSupervisorId = staffId; // Store the staff ID
                    }
                    return isValid;
                }
            }
            return false;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
    
    private void showError(String message) {
        errorLabel.setText(message);
        errorLabel.setVisible(true);
        
        // Hide error after 5 seconds
        javafx.animation.Timeline hideError = new javafx.animation.Timeline(
            new javafx.animation.KeyFrame(javafx.util.Duration.seconds(5), 
                e -> errorLabel.setVisible(false))
        );
        hideError.play();
    }
    
    public boolean isAuthenticationSuccessful() {
        return authenticationSuccessful;
    }
    
    public int getAuthenticatedSupervisorId() {
        return authenticatedSupervisorId;
    }
    
    public static boolean requestSupervisorAuthorization() {
        SupervisorAuthDialog dialog = new SupervisorAuthDialog();
        dialog.showAndWait();
        return dialog.isAuthenticationSuccessful();
    }
    
    public static SupervisorAuthResult requestSupervisorAuthorizationWithId() {
        SupervisorAuthDialog dialog = new SupervisorAuthDialog();
        dialog.showAndWait();
        return new SupervisorAuthResult(dialog.isAuthenticationSuccessful(), dialog.getAuthenticatedSupervisorId());
    }
    
    public static class SupervisorAuthResult {
        private final boolean successful;
        private final int supervisorId;
        
        public SupervisorAuthResult(boolean successful, int supervisorId) {
            this.successful = successful;
            this.supervisorId = supervisorId;
        }
        
        public boolean isSuccessful() {
            return successful;
        }
        
        public int getSupervisorId() {
            return supervisorId;
        }
    }
}