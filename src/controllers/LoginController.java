package controllers;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import java.net.URL;
import java.util.ResourceBundle;

import client.BParkClientApp;
import entities.Message;
import entities.Message.MessageType;

public class LoginController implements Initializable {
    
    @FXML private TextField txtUsername;
    @FXML private PasswordField txtPassword;
    @FXML private TextField txtServerIP;
    @FXML private Button btnLogin;
    @FXML private Label lblStatus;
    
    private boolean isConnecting = false;
    
    @Override
    public void initialize(URL location, ResourceBundle resources) {
        // Set default server IP
        txtServerIP.setText("localhost");
        
        // Enable Enter key to login
        txtUsername.setOnKeyPressed(this::handleEnterKey);
        txtPassword.setOnKeyPressed(this::handleEnterKey);
        
        // Focus on username field
        Platform.runLater(() -> txtUsername.requestFocus());
    }
    
    @FXML
    private void handleLogin() {
        if (isConnecting) {
            return; // Prevent multiple connection attempts
        }
        
        String username = txtUsername.getText().trim();
        String serverIP = txtServerIP.getText().trim();
        
        // Validate input
        if (username.isEmpty()) {
            showError("Please enter your username");
            txtUsername.requestFocus();
            return;
        }
        
        if (serverIP.isEmpty()) {
            showError("Please enter server IP address");
            txtServerIP.requestFocus();
            return;
        }
        
        // Update UI for connection attempt
        isConnecting = true;
        btnLogin.setDisable(true);
        btnLogin.setText("Connecting...");
        lblStatus.setText("Connecting to server...");
        lblStatus.setStyle("-fx-text-fill: #3498DB;");
        
        // Store server IP and connect
        BParkClientApp.setServerIP(serverIP);
        
        // Connect to server in background thread
        new Thread(() -> {
            try {
                BParkClientApp.connectToServer();
                
                // Wait a bit for connection to establish
                Thread.sleep(500);
                
                // Send login request
                Platform.runLater(() -> {
                    BParkClientApp.setCurrentUser(username);
                    
                    // Send login message
                    Message loginMsg = new Message(MessageType.SUBSCRIBER_LOGIN, username);
                    BParkClientApp.sendMessage(loginMsg);
                    
                    lblStatus.setText("Authenticating...");
                });
                
            } catch (Exception e) {
                Platform.runLater(() -> {
                    showError("Failed to connect to server: " + e.getMessage());
                    resetLoginButton();
                });
            }
        }).start();
    }
    
    /**
     * Handle Enter key press for quick login
     */
    private void handleEnterKey(KeyEvent event) {
        if (event.getCode() == KeyCode.ENTER) {
            handleLogin();
        }
    }
    
    /**
     * Called when login fails
     */
    public void handleLoginFailed(String reason) {
        Platform.runLater(() -> {
            showError(reason != null ? reason : "Login failed. Please check your credentials.");
            resetLoginButton();
            txtUsername.requestFocus();
            txtUsername.selectAll();
        });
    }
    
    /**
     * Called when login succeeds
     */
    public void handleLoginSuccess(String userType) {
        Platform.runLater(() -> {
            lblStatus.setText("Login successful! Loading interface...");
            lblStatus.setStyle("-fx-text-fill: #27AE60;");
            
            // Close login window
            btnLogin.getScene().getWindow().hide();
        });
    }
    
    /**
     * Reset login button state
     */
    private void resetLoginButton() {
        isConnecting = false;
        btnLogin.setDisable(false);
        btnLogin.setText("Login");
    }
    
    /**
     * Show error message
     */
    private void showError(String message) {
        lblStatus.setText(message);
        lblStatus.setStyle("-fx-text-fill: #E74C3C;");
    }
}