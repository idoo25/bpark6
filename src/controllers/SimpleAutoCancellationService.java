package controllers;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import entities.ParkingSubscriber;
import services.EmailService;

/**
 * Simplified Automatic Reservation Cancellation Service for Unified ParkingInfo Table
 * 15-minute rule: If a customer with "preorder" status is late by more than 15 minutes,
 * their reservation is automatically cancelled and the spot becomes available.
 * NOW INCLUDES EMAIL NOTIFICATIONS
 */
public class SimpleAutoCancellationService {
    
    private final ParkingController parkingController;
    private final ScheduledExecutorService scheduler;
    private static final int LATE_THRESHOLD_MINUTES = 15;
    private boolean isRunning = false;
    
    public SimpleAutoCancellationService(ParkingController parkingController) {
        this.parkingController = parkingController;
        this.scheduler = Executors.newScheduledThreadPool(1);
    }
    
    /**
     * Start the automatic cancellation service
     * Runs every minute to check for late preorder reservations
     */
    public void startService() {
        if (isRunning) {
            System.out.println("Auto-cancellation service is already running");
            return;
        }
        
        isRunning = true;
        System.out.println("Starting automatic reservation cancellation service...");
        System.out.println("Checking for late preorder reservations every minute (15+ min late = auto-cancel)");
        
        // Schedule to run every minute
        scheduler.scheduleAtFixedRate(() -> {
            try {
                checkAndCancelLatePreorders();
            } catch (Exception e) {
                System.err.println("Error in auto-cancellation service: " + e.getMessage());
            }
        }, 0, 1, TimeUnit.MINUTES);
    }
    
    /**
     * Stop the automatic cancellation service
     */
    public void stopService() {
        if (!isRunning) {
            return;
        }
        
        isRunning = false;
        scheduler.shutdown();
        System.out.println("Auto-cancellation service stopped");
    }
    
    /**
     * Main method that checks for and cancels late preorder reservations
     * NOW WITH EMAIL NOTIFICATIONS - UPDATED FOR UNIFIED TABLE
     */
    private void checkAndCancelLatePreorders() {
        String query = """
            SELECT 
                pi.ParkingInfo_ID,
                pi.User_ID,
                pi.ParkingSpot_ID,
                u.UserName,
                u.Email,
                u.Name,
                u.Phone,
                pi.Estimated_start_time,
                TIMESTAMPDIFF(MINUTE, pi.Estimated_start_time, NOW()) as minutes_late
            FROM ParkingInfo pi
            JOIN users u ON pi.User_ID = u.User_ID
            WHERE pi.statusEnum = 'preorder'
            AND pi.Estimated_start_time < NOW()
            AND TIMESTAMPDIFF(MINUTE, pi.Estimated_start_time, NOW()) >= ?
            """;
        
        try (PreparedStatement stmt = parkingController.getConnection().prepareStatement(query)) {
            stmt.setInt(1, LATE_THRESHOLD_MINUTES);
            
            try (ResultSet rs = stmt.executeQuery()) {
                int cancelledCount = 0;
                
                while (rs.next()) {
                    int parkingInfoID = rs.getInt("ParkingInfo_ID");
                    int userID = rs.getInt("User_ID");
                    int spotId = rs.getInt("ParkingSpot_ID");
                    String userName = rs.getString("UserName");
                    String userEmail = rs.getString("Email");
                    String fullName = rs.getString("Name");
                    int minutesLate = rs.getInt("minutes_late");
                    Timestamp estimatedStartTime = rs.getTimestamp("Estimated_start_time");
                    
                    if (cancelLateReservation(parkingInfoID, spotId)) {
                        cancelledCount++;
                        
                        // SEND EMAIL NOTIFICATION for auto-cancellation
                        if (userEmail != null && fullName != null) {
                            EmailService.sendReservationCancelled(userEmail, fullName, String.valueOf(parkingInfoID));
                        }
                        
                        System.out.println(String.format(
                            "AUTO-CANCELLED: Reservation %d for %s (Spot %d) - %d minutes late - Email sent",
                            parkingInfoID, userName, spotId, minutesLate
                        ));
                    }
                }
                
                if (cancelledCount > 0) {
                    System.out.println(String.format(
                        "Auto-cancellation completed: %d preorder reservations cancelled, %d spots freed, %d emails sent",
                        cancelledCount, cancelledCount, cancelledCount
                    ));
                }
            }
        } catch (SQLException e) {
            System.err.println("Database error during auto-cancellation: " + e.getMessage());
        }
    }
    
    /**
     * Cancel a specific late preorder reservation and free up the parking spot
     * UPDATED FOR UNIFIED TABLE
     */
    private boolean cancelLateReservation(int parkingInfoID, int spotId) {
        Connection conn = parkingController.getConnection();
        
        try {
            conn.setAutoCommit(false);
            
            // 1. Cancel the reservation (change status from preorder to cancelled)
            String cancelQuery = """
                UPDATE ParkingInfo 
                SET statusEnum = 'cancelled', IsLate = 'yes'
                WHERE ParkingInfo_ID = ? AND statusEnum = 'preorder'
                """;
            
            int updatedReservations = 0;
            try (PreparedStatement stmt = conn.prepareStatement(cancelQuery)) {
                stmt.setInt(1, parkingInfoID);
                updatedReservations = stmt.executeUpdate();
            }
            
            if (updatedReservations == 0) {
                conn.rollback();
                return false; // Reservation was already cancelled or doesn't exist
            }
            
            // 2. Free up the parking spot
            String freeSpotQuery = """
                UPDATE ParkingSpot 
                SET isOccupied = FALSE 
                WHERE ParkingSpot_ID = ?
                """;
            
            try (PreparedStatement stmt = conn.prepareStatement(freeSpotQuery)) {
                stmt.setInt(1, spotId);
                stmt.executeUpdate();
            }
            
            conn.commit();
            return true;
            
        } catch (SQLException e) {
            try {
                conn.rollback();
            } catch (SQLException rollbackEx) {
                System.err.println("Failed to rollback transaction: " + rollbackEx.getMessage());
            }
            System.err.println("Failed to cancel reservation " + parkingInfoID + ": " + e.getMessage());
            return false;
        } finally {
            try {
                conn.setAutoCommit(true);
            } catch (SQLException e) {
                System.err.println("Failed to reset auto-commit: " + e.getMessage());
            }
        }
    }
    
    /**
     * Get current timestamp for logging
     */
    private String getCurrentTimestamp() {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }
    
    /**
     * Check if service is running
     */
    public boolean isRunning() {
        return isRunning;
    }
    
    /**
     * Shutdown the service
     */
    public void shutdown() {
        stopService();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}