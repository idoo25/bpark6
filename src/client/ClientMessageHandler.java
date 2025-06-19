package client;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;

import entities.Message;
import entities.ParkingOrder;
import entities.ParkingReport;
import entities.ParkingSubscriber;
import javafx.application.Platform;
import javafx.scene.control.Alert;

public class ClientMessageHandler {
    
    /**
     * Handle Message objects received from server
     */
    public static void handleMessage(Message message) {
        switch (message.getType()) {
            case SUBSCRIBER_LOGIN_RESPONSE:
                handleLoginResponse(message);
                break;
                
            case PARKING_AVAILABILITY_RESPONSE:
                handleParkingAvailability(message);
                break;
                
            case RESERVATION_RESPONSE:
                handleReservationResponse(message);
                break;
                
            case REGISTRATION_RESPONSE:
                handleRegistrationResponse(message);
                break;
                
            case LOST_CODE_RESPONSE:
                handleLostCodeResponse(message);
                break;
                
            case PARKING_HISTORY_RESPONSE:
                handleParkingHistory(message);
                break;
                
            case MANAGER_SEND_REPORTS:
                handleReports(message);
                break;
                
            case ACTIVE_PARKINGS_RESPONSE:
                handleActiveParkings(message);
                break;
                
            case UPDATE_SUBSCRIBER_RESPONSE:
                handleUpdateResponse(message);
                break;
                
            case ACTIVATION_RESPONSE:
                handleActivationResponse(message);
                break;
                
            case CANCELLATION_RESPONSE:
                handleCancellationResponse(message);
                break;
                
            default:
                System.out.println("Unknown message type: " + message.getType());
        }
    }
    
    /**
     * Handle String messages from server (legacy support)
     */
    public static void handleStringMessage(String message) {
        String[] parts = message.split(" ", 2);
        String command = parts[0];
        String data = parts.length > 1 ? parts[1] : "";
        
        switch (command) {
            case "login:":
                handleStringLoginResponse(data);
                break;
                
            case "availableSpots":
                handleStringAvailableSpots(data);
                break;
                
            case "enterResult":
                showAlert("Entry Result", data);
                break;
                
            case "exitResult":
                showAlert("Exit Result", data);
                break;
                
            case "parkingCode":
                showAlert("Lost Code", "Your parking code is: " + data);
                break;
                
            case "reservationResult":
                showAlert("Reservation", data);
                break;
                
            default:
                System.out.println("Unknown string command: " + command);
        }
    }
    
    // Message Type Handlers
    
    private static void handleLoginResponse(Message message) {
        ParkingSubscriber subscriber = (ParkingSubscriber) message.getContent();
        if (subscriber != null) {
            BParkClientApp.setCurrentUser(subscriber.getSubscriberCode());
            BParkClientApp.setUserType(subscriber.getUserType());
            BParkClientApp.switchToMainScreen(subscriber.getUserType());
        } else {
            showAlert("Login Failed", "Invalid username or user not found");
        }
    }
    
    private static void handleParkingAvailability(Message message) {
        Integer availableSpots = (Integer) message.getContent();
        // Update UI with available spots
        // This would typically update a label in the current screen
        showAlert("Parking Availability", "Available spots: " + availableSpots);
    }
    
    private static void handleReservationResponse(Message message) {
        String response = (String) message.getContent();
        if (response.startsWith("SUCCESS") || response.contains("confirmed")) {
            showAlert("Reservation Success", response);
        } else {
            showAlert("Reservation Failed", response);
        }
    }
    
    private static void handleRegistrationResponse(Message message) {
        String response = (String) message.getContent();
        if (response.startsWith("SUCCESS")) {
            showAlert("Registration Success", response);
            // Clear registration form
        } else {
            showAlert("Registration Failed", response);
        }
    }
    
    private static void handleLostCodeResponse(Message message) {
        String code = (String) message.getContent();
        if (code.matches("\\d+")) {
            showAlert("Parking Code Recovery", 
                "Your parking code is: " + code + "\n" +
                "This has also been sent to your email/SMS");
        } else {
            showAlert("Code Recovery Failed", code);
        }
    }
    
    @SuppressWarnings("unchecked")
    private static void handleParkingHistory(Message message) {
        ArrayList<ParkingOrder> history = (ArrayList<ParkingOrder>) message.getContent();
        // Update the parking history table in the UI
        // This would typically be handled by the controller of the current screen
        System.out.println("Received " + history.size() + " parking records");
    }
    
    @SuppressWarnings("unchecked")
    private static void handleReports(Message message) {
        ArrayList<ParkingReport> reports = (ArrayList<ParkingReport>) message.getContent();
        // Update the reports view in the manager interface
        System.out.println("Received " + reports.size() + " reports");
    }
    
    @SuppressWarnings("unchecked")
    private static void handleActiveParkings(Message message) {
        ArrayList<ParkingOrder> activeParkings = (ArrayList<ParkingOrder>) message.getContent();
        // Update the active parkings table in attendant/manager view
        System.out.println("Received " + activeParkings.size() + " active parking sessions");
    }
    
    private static void handleUpdateResponse(Message message) {
        String response = (String) message.getContent();
        showAlert("Update Profile", response);
    }
    
    private static void handleActivationResponse(Message message) {
        String response = (String) message.getContent();
        if (response.contains("successful") || response.contains("activated")) {
            showAlert("Reservation Activated", response);
        } else {
            showAlert("Activation Failed", response);
        }
    }
    
    private static void handleCancellationResponse(Message message) {
        String response = (String) message.getContent();
        showAlert("Reservation Cancellation", response);
    }
    
    // String message handlers (legacy)
    
    private static void handleStringLoginResponse(String data) {
        if (!data.equals("None")) {
            BParkClientApp.setUserType(data);
            BParkClientApp.switchToMainScreen(data);
        } else {
            showAlert("Login Failed", "Invalid credentials");
        }
    }
    
    private static void handleStringAvailableSpots(String data) {
        showAlert("Available Spots", "Current available spots: " + data);
    }
    
    // Utility methods
    
    /**
     * Serialize a Message object to byte array
     */
    public static byte[] serialize(Message msg) {
        try {
            ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
            ObjectOutputStream out = new ObjectOutputStream(byteStream);
            out.writeObject(msg);
            out.flush();
            return byteStream.toByteArray();
        } catch (Exception ex) {
            ex.printStackTrace();
            return null;
        }
    }
    
    /**
     * Deserialize byte array to object
     */
    public static Object deserialize(Object msg) {
        try {
            byte[] messageBytes = (byte[]) msg;
            ByteArrayInputStream byteStream = new ByteArrayInputStream(messageBytes);
            ObjectInputStream objectStream = new ObjectInputStream(byteStream);
            return objectStream.readObject();
        } catch (Exception ex) {
            ex.printStackTrace();
            return null;
        }
    }
    
    /**
     * Show alert dialog
     */
    private static void showAlert(String title, String content) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText(content);
            alert.showAndWait();
        });
    }
}