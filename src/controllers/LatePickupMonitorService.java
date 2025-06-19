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

import services.EmailService;

/**
 * Proactive Late Pickup Monitoring Service for Active Parking Sessions
 * Automatically checks every 1 MINUTE for cars that are overdue
 * and sends immediate email notifications while the car is still parked.
 */
public class LatePickupMonitorService {
    
    private final ParkingController parkingController;
    private final ScheduledExecutorService scheduler;
    private boolean isRunning = false;
    
    public LatePickupMonitorService(ParkingController parkingController) {
        this.parkingController = parkingController;
        this.scheduler = Executors.newScheduledThreadPool(1);
    }
    
    /**
     * Start the proactive late pickup monitoring service
     * Runs every 1 MINUTE to check for overdue active parking sessions
     */
    public void startService() {
        if (isRunning) {
            System.out.println("Late pickup monitoring service is already running");
            return;
        }
        
        isRunning = true;
        System.out.println("Starting proactive late pickup monitoring service...");
        System.out.println("Checking for overdue active parking sessions every 1 MINUTE");
        
        // Check every 1 minute for overdue cars
        scheduler.scheduleAtFixedRate(() -> {
            try {
                checkForOverdueParkingSessions();
            } catch (Exception e) {
                System.err.println("Error in late pickup monitoring service: " + e.getMessage());
                e.printStackTrace();
            }
        }, 0, 1, TimeUnit.MINUTES); // Changed to 1 MINUTE interval
    }
    
    /**
     * Stop the late pickup monitoring service
     */
    public void stopService() {
        if (!isRunning) {
            return;
        }
        
        isRunning = false;
        scheduler.shutdown();
        System.out.println("Late pickup monitoring service stopped");
    }
    
    /**
     * Main method that checks for overdue active parking sessions
     * and sends immediate email notifications
     */
    private void checkForOverdueParkingSessions() {
        String query = """
            SELECT 
                pi.ParkingInfo_ID,
                pi.User_ID,
                pi.ParkingSpot_ID,
                u.UserName,
                u.Email,
                u.Name,
                u.Phone,
                pi.Estimated_end_time,
                pi.Actual_start_time,
                TIMESTAMPDIFF(MINUTE, pi.Estimated_end_time, NOW()) as minutes_overdue
            FROM ParkingInfo pi
            JOIN users u ON pi.User_ID = u.User_ID
            WHERE pi.statusEnum = 'active'
            AND pi.Estimated_end_time < NOW()
            AND pi.IsLate = 'no'
            ORDER BY pi.Estimated_end_time ASC
            """;
        
        try (PreparedStatement stmt = parkingController.getConnection().prepareStatement(query)) {
            try (ResultSet rs = stmt.executeQuery()) {
                int notifiedCount = 0;
                
                while (rs.next()) {
                    int parkingInfoID = rs.getInt("ParkingInfo_ID");
                    int userID = rs.getInt("User_ID");
                    int spotId = rs.getInt("ParkingSpot_ID");
                    String userName = rs.getString("UserName");
                    String userEmail = rs.getString("Email");
                    String fullName = rs.getString("Name");
                    int minutesOverdue = rs.getInt("minutes_overdue");
                    Timestamp estimatedEndTime = rs.getTimestamp("Estimated_end_time");
                    Timestamp actualStartTime = rs.getTimestamp("Actual_start_time");
                    
                    if (markAsLateAndNotify(parkingInfoID, userEmail, fullName, spotId, minutesOverdue)) {
                        notifiedCount++;
                        
                        System.out.println(String.format(
                            "⚠️  LATE PICKUP DETECTED: Parking %d for %s (Spot %d) - %d minutes overdue - Email sent immediately",
                            parkingInfoID, userName, spotId, minutesOverdue
                        ));
                        
                        // Log detailed information
                        System.out.println(String.format(
                            "   📋 Details: Started at %s, Should have ended at %s, User: %s (%s)",
                            actualStartTime.toLocalDateTime().format(DateTimeFormatter.ofPattern("HH:mm")),
                            estimatedEndTime.toLocalDateTime().format(DateTimeFormatter.ofPattern("HH:mm")),
                            fullName, userEmail
                        ));
                    }
                }
                
                if (notifiedCount > 0) {
                    System.out.println(String.format(
                        "📧 Late pickup monitoring completed: %d overdue sessions detected, %d email notifications sent",
                        notifiedCount, notifiedCount
                    ));
                } else {
                    // Only log every 10 minutes to avoid spam when no late pickups
                    if (getCurrentMinute() % 10 == 0) {
                        System.out.println("✅ Late pickup check: No overdue parking sessions found");
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("Database error during late pickup monitoring: " + e.getMessage());
        }
    }
    
    /**
     * Mark parking session as late and send immediate email notification
     */
    private boolean markAsLateAndNotify(int parkingInfoID, String userEmail, String fullName, 
                                      int spotId, int minutesOverdue) {
        Connection conn = parkingController.getConnection();
        
        try {
            conn.setAutoCommit(false);
            
            // Mark as late in database (but keep status as 'active' since car is still parked)
            String updateQuery = """
                UPDATE ParkingInfo 
                SET IsLate = 'yes'
                WHERE ParkingInfo_ID = ? AND IsLate = 'no' AND statusEnum = 'active'
                """;
            
            int updatedSessions = 0;
            try (PreparedStatement stmt = conn.prepareStatement(updateQuery)) {
                stmt.setInt(1, parkingInfoID);
                updatedSessions = stmt.executeUpdate();
            }
            
            if (updatedSessions == 0) {
                conn.rollback();
                return false; // Already marked as late or not active
            }
            
            conn.commit();
            
            // Send immediate email notification
            if (userEmail != null && fullName != null) {
                boolean emailSent = EmailService.sendLatePickupNotification(userEmail, fullName);
                if (emailSent) {
                    System.out.println(String.format(
                        "📧 Late pickup email sent to %s (%s) - %d minutes overdue",
                        fullName, userEmail, minutesOverdue
                    ));
                } else {
                    System.err.println(String.format(
                        "❌ Failed to send late pickup email to %s (%s)",
                        fullName, userEmail
                    ));
                }
                return emailSent;
            }
            
            return true;
            
        } catch (SQLException e) {
            try {
                conn.rollback();
            } catch (SQLException rollbackEx) {
                System.err.println("Failed to rollback transaction: " + rollbackEx.getMessage());
            }
            System.err.println("Failed to mark parking session " + parkingInfoID + " as late: " + e.getMessage());
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
     * Get current minute (0-59) for conditional logging
     */
    private int getCurrentMinute() {
        return LocalDateTime.now().getMinute();
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
     * Shutdown the service gracefully
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