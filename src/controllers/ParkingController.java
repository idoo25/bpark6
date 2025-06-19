package controllers;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.Duration;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

import entities.ParkingOrder;
import entities.ParkingSubscriber;
import services.EmailService;

/**
 * Enhanced ParkingController with Smart Features
 * Combines all existing functionality with smart parking algorithms
 */
public class ParkingController {
    protected Connection conn;
    public int successFlag;
    private static final int TOTAL_PARKING_SPOTS = 100;
    private static final double RESERVATION_THRESHOLD = 0.4;
    
    // NEW: Smart parking constants
    private static final int TIME_SLOT_MINUTES = 15; // 15-minute precision
    private static final int STANDARD_BOOKING_HOURS = 4;
    private static final int MINIMUM_SPONTANEOUS_HOURS = 2;
    private static final int MAXIMUM_EXTENSION_HOURS = 4;
    private static final int PREFERRED_WINDOW_HOURS = 8;
    
    /**
     * Role-based access control for all parking operations
     */
    public enum UserRole {
        SUBSCRIBER("sub"),
        ATTENDANT("emp"), 
        MANAGER("mng");
        
        private final String dbValue;
        
        UserRole(String dbValue) {
            this.dbValue = dbValue;
        }
        
        public String getDbValue() {
            return dbValue;
        }
        
        public static UserRole fromDbValue(String dbValue) {
            for (UserRole role : values()) {
                if (role.dbValue.equals(dbValue)) {
                    return role;
                }
            }
            return null;
        }
    }
    
    private LatePickupMonitorService latePickupMonitor;

    // NEW: Smart parking data structures
    public static class TimeSlot {
        public LocalDateTime startTime;
        public boolean isAvailable;
        public int availableSpots;
        public boolean meetsFortyPercentRule;
        
        public TimeSlot(LocalDateTime startTime, boolean isAvailable, int availableSpots, boolean meetsFortyPercentRule) {
            this.startTime = startTime;
            this.isAvailable = isAvailable;
            this.availableSpots = availableSpots;
            this.meetsFortyPercentRule = meetsFortyPercentRule;
        }
        
        public String getFormattedTime() {
            return startTime.format(DateTimeFormatter.ofPattern("HH:mm"));
        }
    }

    private static class SpotAllocation {
        int spotId;
        int allocatedHours;
        boolean hasEightHourWindow;
        
        SpotAllocation(int spotId, int allocatedHours, boolean hasEightHourWindow) {
            this.spotId = spotId;
            this.allocatedHours = allocatedHours;
            this.hasEightHourWindow = hasEightHourWindow;
        }
    }
    
    /**
     * Smart time-aware availability checking for reservation windows
     * Considers future reservations when calculating availability
     */
    private int countAvailableSpotsForTimeWindow(LocalDateTime startTime, LocalDateTime endTime) {
        try {
            String query = """
                SELECT COUNT(*) as conflicts FROM ParkingInfo 
                WHERE statusEnum IN ('preorder', 'active')
                AND NOT (
                    Estimated_end_time <= ? OR 
                    Estimated_start_time >= ?
                )
                """;
            
            try (PreparedStatement stmt = conn.prepareStatement(query)) {
                stmt.setTimestamp(1, Timestamp.valueOf(startTime));
                stmt.setTimestamp(2, Timestamp.valueOf(endTime));
                
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        int conflicts = rs.getInt("conflicts");
                        int availableSpots = TOTAL_PARKING_SPOTS - conflicts;
                        return Math.max(0, availableSpots);
                    }
                }
            }
        } catch (SQLException e) {
            System.out.println("Error counting available spots for time window: " + e.getMessage());
        }
        return 0;
    }
    
    /**
     * Smart 40% rule enforcement considering future reservations
     * Returns true if the time window has sufficient availability
     */
    private boolean hasValidAvailabilityForWindow(LocalDateTime startTime, LocalDateTime endTime) {
        int availableSpots = countAvailableSpotsForTimeWindow(startTime, endTime);
        boolean meetsFortyPercent = availableSpots >= (TOTAL_PARKING_SPOTS * RESERVATION_THRESHOLD);
        
        if (meetsFortyPercent) {
                                    System.out.println("Smart availability check: " + availableSpots + " spots available for window " + 
                             startTime.format(DateTimeFormatter.ofPattern("HH:mm")) + "-" + 
                             endTime.format(DateTimeFormatter.ofPattern("HH:mm")));
        } else {
            System.out.println("Smart availability check: Only " + availableSpots + 
                             " spots available (need " + (int)(TOTAL_PARKING_SPOTS * RESERVATION_THRESHOLD) + 
                             " for 40% rule)");
        }
        
        return meetsFortyPercent;
    }
    
    /**
     * Find optimal available spot for a specific time window
     * Prevents conflicts by checking future reservations
     */
    private int findAvailableSpotForTimeWindow(LocalDateTime startTime, LocalDateTime endTime) {
        try {
            String query = """
                SELECT ps.ParkingSpot_ID 
                FROM ParkingSpot ps 
                WHERE ps.ParkingSpot_ID NOT IN (
                    SELECT pi.ParkingSpot_ID 
                    FROM ParkingInfo pi 
                    WHERE pi.statusEnum IN ('preorder', 'active') 
                    AND NOT (
                        pi.Estimated_end_time <= ? OR 
                        pi.Estimated_start_time >= ?
                    )
                ) 
                ORDER BY ps.ParkingSpot_ID
                LIMIT 1
                """;
            
            try (PreparedStatement stmt = conn.prepareStatement(query)) {
                stmt.setTimestamp(1, Timestamp.valueOf(startTime));
                stmt.setTimestamp(2, Timestamp.valueOf(endTime));
                
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        int spotId = rs.getInt("ParkingSpot_ID");
                        System.out.println("Smart spot allocation: Found conflict-free spot " + spotId + 
                                         " for window " + startTime.format(DateTimeFormatter.ofPattern("HH:mm")) + 
                                         "-" + endTime.format(DateTimeFormatter.ofPattern("HH:mm")));
                        return spotId;
                    }
                }
            }
        } catch (SQLException e) {
            System.out.println("Error finding available spot for time window: " + e.getMessage());
        }
        return -1;
    }

    // ========== ENHANCED EXISTING METHODS ==========
    
    /**
     * ENHANCED: makeReservation with smart availability checking
     * Same API, but now uses time-aware availability checking
     */
    public String makeReservation(String userName, String reservationDateTimeStr) {
        // SAME API - no changes to method signature
        
        // Smart check FIRST - consider future reservations
        try {
            LocalDateTime reservationDateTime = parseDateTime(reservationDateTimeStr);
            LocalDateTime estimatedEndTime = reservationDateTime.plusHours(4); // Standard 4-hour booking
            
            // SMART: Check availability for the actual time window (not just current)
            if (!hasValidAvailabilityForWindow(reservationDateTime, estimatedEndTime)) {
                return "Not enough available spots for your requested time window (need 40% available during your parking period)";
            }
            
            // Validate reservation is within allowed time range (24 hours to 7 days)
            LocalDateTime now = LocalDateTime.now();
            if (reservationDateTime.isBefore(now.plusHours(24))) {
                return "Reservation must be at least 24 hours in advance";
            }
            if (reservationDateTime.isAfter(now.plusDays(7))) {
                return "Reservation cannot be more than 7 days in advance";
            }

            // Get user ID
            int userID = getUserID(userName);
            if (userID == -1) {
                return "User not found";
            }

            // SMART: Find conflict-free spot for the time window
            int parkingSpotID = findAvailableSpotForTimeWindow(reservationDateTime, estimatedEndTime);
            if (parkingSpotID == -1) {
                return "No available parking spots for the selected time window";
            }

            // Create reservation in unified ParkingInfo table (same as before)
            String qry = """
                INSERT INTO ParkingInfo 
                (ParkingSpot_ID, User_ID, Date_Of_Placing_Order, 
                 Estimated_start_time, Estimated_end_time, 
                 IsOrderedEnum, IsLate, IsExtended, statusEnum) 
                VALUES (?, ?, NOW(), ?, ?, 'yes', 'no', 'no', 'preorder')
                """;
            
            try (PreparedStatement stmt = conn.prepareStatement(qry, PreparedStatement.RETURN_GENERATED_KEYS)) {
                stmt.setInt(1, parkingSpotID);
                stmt.setInt(2, userID);
                stmt.setTimestamp(3, Timestamp.valueOf(reservationDateTime));
                stmt.setTimestamp(4, Timestamp.valueOf(estimatedEndTime));
                stmt.executeUpdate();
                
                // Get the generated ParkingInfo_ID (serves as reservation code)
                try (ResultSet generatedKeys = stmt.getGeneratedKeys()) {
                    if (generatedKeys.next()) {
                        int reservationCode = generatedKeys.getInt(1);
                        System.out.println("Smart reservation created: " + reservationCode + 
                                         " for " + reservationDateTime + " with conflict-free spot " + parkingSpotID);
                        
                        // Send email confirmation (same as before)
                        ParkingSubscriber user = getUserInfo(userName);
                        if (user != null && user.getEmail() != null) {
                            String formattedDateTime = reservationDateTime.format(
                                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
                            EmailService.sendReservationConfirmation(
                                user.getEmail(), user.getFirstName(), 
                                String.valueOf(reservationCode), formattedDateTime, "Spot " + parkingSpotID
                            );
                        }
                        
                        return "Reservation confirmed for " + reservationDateTime.format(
                            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")) + 
                            ". Confirmation code: " + reservationCode + " (Conflict-free spot assigned)";
                    }
                }
            }
        } catch (Exception e) {
            System.out.println("Error making reservation: " + e.getMessage());
            return "Reservation failed: " + e.getMessage();
        }
        return "Reservation failed";
    }

    /**
     * ENHANCED: Better 40% rule checking for canMakeReservation()
     * Now considers upcoming reservations, not just current occupancy
     */
    public boolean canMakeReservation() {
        // SAME API - method signature unchanged
        
        try {
            // Check availability for the next 24 hours (typical advance booking window)
            LocalDateTime now = LocalDateTime.now().plusHours(24); // Minimum advance booking
            LocalDateTime endWindow = now.plusHours(4); // Standard 4-hour parking
            
            // SMART: Use time-aware availability instead of just current occupancy
            int smartAvailableSpots = countAvailableSpotsForTimeWindow(now, endWindow);
            boolean canReserve = smartAvailableSpots >= (TOTAL_PARKING_SPOTS * RESERVATION_THRESHOLD);
            
            System.out.println("Smart reservation check: " + smartAvailableSpots + 
                             " spots will be available during typical booking window" +
                             (canReserve ? " (reservations allowed)" : " (reservations blocked)"));
            
            return canReserve;
            
        } catch (Exception e) {
            System.out.println("Error in smart reservation check, falling back to basic check: " + e.getMessage());
            // Fallback to basic check if smart check fails
            int availableSpots = getAvailableParkingSpots();
            return availableSpots >= (TOTAL_PARKING_SPOTS * RESERVATION_THRESHOLD);
        }
    }

    // ========== HELPER METHOD ==========
    
    /**
     * Parse datetime string in various formats (same as before)
     */
    private LocalDateTime parseDateTime(String dateTimeStr) {
        try {
            // Try "YYYY-MM-DD HH:MM:SS" format first
            if (dateTimeStr.matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}")) {
                return LocalDateTime.parse(dateTimeStr, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            }
            // Try "YYYY-MM-DD HH:MM" format
            else if (dateTimeStr.matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}")) {
                return LocalDateTime.parse(dateTimeStr, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
            }
            // Try ISO format "YYYY-MM-DDTHH:MM"
            else if (dateTimeStr.contains("T")) {
                return LocalDateTime.parse(dateTimeStr);
            }
            else {
                throw new IllegalArgumentException("Unsupported datetime format: " + dateTimeStr);
            }
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid datetime format: " + dateTimeStr + 
                                             ". Use 'YYYY-MM-DD HH:MM' or 'YYYY-MM-DD HH:MM:SS'");
        }
    }

    // Auto-cancellation service
    private SimpleAutoCancellationService autoCancellationService;

    public ParkingController(String dbname, String pass) {
        // Using Asia/Jerusalem timezone for Israel
        String connectPath = "jdbc:mysql://localhost/" + dbname + "?serverTimezone=Asia/Jerusalem";
        connectToDB(connectPath, pass);
        
        // Initialize auto-cancellation service after DB connection
        if (successFlag == 1) {
            this.autoCancellationService = new SimpleAutoCancellationService(this);
            startAutoCancellationService();
            this.latePickupMonitor = new LatePickupMonitorService(this);
            startLatePickupMonitoring();
        }
    }
    
    public void startLatePickupMonitoring() {
        if (latePickupMonitor != null) {
            latePickupMonitor.startService();
            System.out.println("Late pickup monitoring started - checking overdue cars every 1 minute");
        }
    }

    // ADD THIS METHOD - stop late pickup monitoring
    public void stopLatePickupMonitoring() {
        if (latePickupMonitor != null) {
            latePickupMonitor.stopService();
            System.out.println("Late pickup monitoring stopped");
        }
    }

    public Connection getConnection() {
        return conn;
    }

    public void connectToDB(String path, String pass) {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            System.out.println("Driver definition succeed");
        } catch (Exception ex) {
            System.out.println("Driver definition failed");
        }

        try {
            conn = DriverManager.getConnection(path, "root", pass);
            System.out.println("SQL connection succeed");
            successFlag = 1;
        } catch (SQLException ex) {
            System.out.println("SQLException: " + ex.getMessage());
            System.out.println("SQLState: " + ex.getSQLState());
            System.out.println("VendorError: " + ex.getErrorCode());
            successFlag = 2;
        }
    }

    /**
     * Start the automatic reservation cancellation service
     */
    public void startAutoCancellationService() {
        if (autoCancellationService != null) {
            autoCancellationService.startService();
            System.out.println("✅ Auto-cancellation service started - monitoring preorder reservations");
        }
    }

    /**
     * Stop the automatic reservation cancellation service
     */
    public void stopAutoCancellationService() {
        if (autoCancellationService != null) {
            autoCancellationService.stopService();
            System.out.println("⛔ Auto-cancellation service stopped");
        }
    }

    /**
     * Cleanup method - call when shutting down the controller
     */
     public void shutdown() {
        // Stop auto-cancellation service
        if (autoCancellationService != null) {
            autoCancellationService.shutdown();
        }
        
        // ADD THESE LINES - stop late pickup monitoring
        if (latePickupMonitor != null) {
            latePickupMonitor.shutdown();
        }
        
        System.out.println("All background services shut down successfully");
    }


    /**
     * Get user role from database
     */
    private UserRole getUserRole(String userName) {
        String qry = "SELECT UserTypeEnum FROM users WHERE UserName = ?";
        
        try (PreparedStatement stmt = conn.prepareStatement(qry)) {
            stmt.setString(1, userName);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    String userType = rs.getString("UserTypeEnum");
                    return UserRole.fromDbValue(userType);
                }
            }
        } catch (SQLException e) {
            System.out.println("Error getting user role: " + e.getMessage());
        }
        return null;
    }

    /**
     * Check if user has required role for operation
     */
    private boolean hasRole(String userName, UserRole requiredRole) {
        UserRole userRole = getUserRole(userName);
        return userRole == requiredRole;
    }

    public String checkLogin(String userName, String password) {
        String qry = "SELECT UserTypeEnum FROM users WHERE UserName = ?";
        
        try (PreparedStatement stmt = conn.prepareStatement(qry)) {
            stmt.setString(1, userName);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("UserTypeEnum");
                }
            }
        } catch (SQLException e) {
            System.out.println("Error checking login: " + e.getMessage());
        }
        return "None";
    }

    /**
     * Gets user information by userName
     */
    public ParkingSubscriber getUserInfo(String userName) {
        String qry = "SELECT * FROM users WHERE UserName = ?";
        
        try (PreparedStatement stmt = conn.prepareStatement(qry)) {
            stmt.setString(1, userName);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    ParkingSubscriber user = new ParkingSubscriber();
                    user.setSubscriberID(rs.getInt("User_ID"));
                    user.setFirstName(rs.getString("Name"));
                    user.setPhoneNumber(rs.getString("Phone"));
                    user.setEmail(rs.getString("Email"));
                    user.setCarNumber(rs.getString("CarNum"));
                    user.setSubscriberCode(userName);
                    user.setUserType(rs.getString("UserTypeEnum"));
                    return user;
                }
            }
        } catch (SQLException e) {
            System.out.println("Error getting user info: " + e.getMessage());
        }
        return null;
    }

    /**
     * Gets the number of available parking spots
     */
    public int getAvailableParkingSpots() {
        String qry = "SELECT COUNT(*) as available FROM ParkingSpot WHERE isOccupied = false";
        
        try (PreparedStatement stmt = conn.prepareStatement(qry)) {
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("available");
                }
            }
        } catch (SQLException e) {
            System.out.println("Error getting available spots: " + e.getMessage());
        }
        return 0;
    }

//    /**
//     * Checks if reservation is possible (40% of spots must be available)
//     */
//    public boolean canMakeReservation() {
//        int availableSpots = getAvailableParkingSpots();
//        return availableSpots >= (TOTAL_PARKING_SPOTS * RESERVATION_THRESHOLD);
//    }

    // ========== NEW SMART FEATURES ==========

    /**
     * NEW: Get available 15-minute time slots for a specific date and time
     * Returns time slots within ±1 hour of preferred time
     */
    public List<TimeSlot> getAvailableTimeSlots(LocalDateTime preferredDateTime) {
        List<TimeSlot> timeSlots = new ArrayList<>();
        
        try {
            LocalDateTime startRange = preferredDateTime.minusHours(1);
            LocalDateTime endRange = preferredDateTime.plusHours(1);
            
            // Ensure we're working with 15-minute aligned slots
            startRange = alignToTimeSlot(startRange);
            
            LocalDateTime currentSlot = startRange;
            while (!currentSlot.isAfter(endRange)) {
                LocalDateTime bookingEnd = currentSlot.plusHours(STANDARD_BOOKING_HOURS);
                boolean hasValidWindow = hasValidFourHourWindow(currentSlot, bookingEnd);
                int availableSpots = countAvailableSpotsForWindow(currentSlot, bookingEnd);
                boolean meetsFortyPercent = availableSpots >= (TOTAL_PARKING_SPOTS * RESERVATION_THRESHOLD);
                
                timeSlots.add(new TimeSlot(
                    currentSlot, 
                    hasValidWindow && meetsFortyPercent, 
                    availableSpots,
                    meetsFortyPercent
                ));
                
                currentSlot = currentSlot.plusMinutes(TIME_SLOT_MINUTES);
            }
            
        } catch (Exception e) {
            System.out.println("Error getting available time slots: " + e.getMessage());
        }
        
        return timeSlots;
    }

//    /**
//     * NEW: Enhanced makeReservation with 15-minute precision and smart allocation
//     */
//    public String makeReservation(String userName, String reservationDateTimeStr) {
//        // Check if reservation is possible (40% rule)
//        if (!canMakeReservation()) {
//            return "Not enough available spots for reservation (need 40% available)";
//        }
//
//        try {
//            // Parse the datetime string with 15-minute precision
//            LocalDateTime reservationDateTime = parseDateTime(reservationDateTimeStr);
//            
//            // NEW: Align to 15-minute slots
//            reservationDateTime = alignToTimeSlot(reservationDateTime);
//            
//            // Validate reservation is within allowed time range (24 hours to 7 days)
//            LocalDateTime now = LocalDateTime.now();
//            if (reservationDateTime.isBefore(now.plusHours(24))) {
//                return "Reservation must be at least 24 hours in advance";
//            }
//            if (reservationDateTime.isAfter(now.plusDays(7))) {
//                return "Reservation cannot be more than 7 days in advance";
//            }
//
//            // Get user ID
//            int userID = getUserID(userName);
//            if (userID == -1) {
//                return "User not found";
//            }
//
//            // NEW: Smart spot allocation
//            int parkingSpotID = findOptimalSpotForTimeWindow(reservationDateTime, reservationDateTime.plusHours(STANDARD_BOOKING_HOURS));
//            if (parkingSpotID == -1) {
//                return "No available parking spots for the selected time window";
//            }
//
//            // Calculate end time (default 4 hours)
//            LocalDateTime estimatedEndTime = reservationDateTime.plusHours(STANDARD_BOOKING_HOURS);
//
//            // Create reservation in unified ParkingInfo table
//            String qry = """
//                INSERT INTO ParkingInfo 
//                (ParkingSpot_ID, User_ID, Date_Of_Placing_Order, 
//                 Estimated_start_time, Estimated_end_time, 
//                 IsOrderedEnum, IsLate, IsExtended, statusEnum) 
//                VALUES (?, ?, NOW(), ?, ?, 'yes', 'no', 'no', 'preorder')
//                """;
//            
//            try (PreparedStatement stmt = conn.prepareStatement(qry, PreparedStatement.RETURN_GENERATED_KEYS)) {
//                stmt.setInt(1, parkingSpotID);
//                stmt.setInt(2, userID);
//                stmt.setTimestamp(3, Timestamp.valueOf(reservationDateTime));
//                stmt.setTimestamp(4, Timestamp.valueOf(estimatedEndTime));
//                stmt.executeUpdate();
//                
//                // Get the generated ParkingInfo_ID (serves as reservation code)
//                try (ResultSet generatedKeys = stmt.getGeneratedKeys()) {
//                    if (generatedKeys.next()) {
//                        int reservationCode = generatedKeys.getInt(1);
//                        System.out.println("Smart reservation created: " + reservationCode + 
//                                         " for " + reservationDateTime + " (15-min precision, optimal spot allocation)");
//                        
//                        // Send email confirmation
//                        ParkingSubscriber user = getUserInfo(userName);
//                        if (user != null && user.getEmail() != null) {
//                            String formattedDateTime = reservationDateTime.format(
//                                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
//                            EmailService.sendReservationConfirmation(
//                                user.getEmail(), user.getFirstName(), 
//                                String.valueOf(reservationCode), formattedDateTime, "Spot " + parkingSpotID
//                            );
//                        }
//                        
//                        return "Smart reservation confirmed for " + reservationDateTime.format(
//                            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")) + 
//                            ". Confirmation code: " + reservationCode + " (Optimal spot: " + parkingSpotID + ")";
//                    }
//                }
//            }
//        } catch (Exception e) {
//            System.out.println("Error making reservation: " + e.getMessage());
//            return "Reservation failed: " + e.getMessage();
//        }
//        return "Reservation failed";
//    }

    /**
     * NEW: Smart spontaneous parking with optimal duration allocation
     */
    public String enterSpontaneousParking(String userName) {
        LocalDateTime now = LocalDateTime.now();
        
        try {
            SpotAllocation allocation = findOptimalSpontaneousAllocation(now);
            if (allocation == null) {
                return "No parking spots available for spontaneous parking (minimum 2 hours required)";
            }

            int userID = getUserID(userName);
            if (userID == -1) {
                return "Invalid user code";
            }

            LocalDateTime estimatedEnd = now.plusHours(allocation.allocatedHours);

            // Create parking info record in unified table
            String qry = """
                INSERT INTO ParkingInfo 
                (ParkingSpot_ID, User_ID, Date_Of_Placing_Order, 
                 Actual_start_time, Estimated_start_time, Estimated_end_time, 
                 IsOrderedEnum, IsLate, IsExtended, statusEnum) 
                VALUES (?, ?, NOW(), ?, ?, ?, 'no', 'no', 'no', 'active')
                """;
            
            try (PreparedStatement stmt = conn.prepareStatement(qry, PreparedStatement.RETURN_GENERATED_KEYS)) {
                stmt.setInt(1, allocation.spotId);
                stmt.setInt(2, userID);
                stmt.setTimestamp(3, Timestamp.valueOf(now));
                stmt.setTimestamp(4, Timestamp.valueOf(now));
                stmt.setTimestamp(5, Timestamp.valueOf(estimatedEnd));
                stmt.executeUpdate();

                // Mark parking spot as occupied
                updateParkingSpotStatus(allocation.spotId, true);
                
                // Get the generated ParkingInfo_ID (serves as parking code)
                try (ResultSet generatedKeys = stmt.getGeneratedKeys()) {
                    if (generatedKeys.next()) {
                        int parkingCode = generatedKeys.getInt(1);
                        return String.format("Smart spontaneous parking successful! Code: %d, Spot: %d, Duration: %d hours%s",
                                           parkingCode, allocation.spotId, allocation.allocatedHours,
                                           allocation.hasEightHourWindow ? " (8+ hour window available)" : "");
                    }
                }
            }
        } catch (Exception e) {
            System.out.println("Error in spontaneous parking: " + e.getMessage());
            return "Spontaneous parking failed: " + e.getMessage();
        }
        return "Spontaneous parking failed";
    }

    // ========== EXISTING METHODS (UNCHANGED) ==========

    /**
     * Handles parking entry with subscriber code (immediate parking) - EXISTING
     */
    public String enterParking(String userName) {
        // Get user ID
        int userID = getUserID(userName);
        if (userID == -1) {
            return "Invalid user code";
        }

        // Check if spots are available
        if (getAvailableParkingSpots() <= 0) {
            return "No parking spots available";
        }

        // Find available parking spot
        int spotID = getAvailableParkingSpotID();
        if (spotID == -1) {
            return "No available parking spot found";
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime estimatedEnd = now.plusHours(4); // Default 4 hours

        // Create parking info record in unified table
        String qry = """
            INSERT INTO ParkingInfo 
            (ParkingSpot_ID, User_ID, Date_Of_Placing_Order, 
             Actual_start_time, Estimated_start_time, Estimated_end_time, 
             IsOrderedEnum, IsLate, IsExtended, statusEnum) 
            VALUES (?, ?, NOW(), ?, ?, ?, 'no', 'no', 'no', 'active')
            """;
        
        try (PreparedStatement stmt = conn.prepareStatement(qry, PreparedStatement.RETURN_GENERATED_KEYS)) {
            stmt.setInt(1, spotID);
            stmt.setInt(2, userID);
            stmt.setTimestamp(3, Timestamp.valueOf(now));
            stmt.setTimestamp(4, Timestamp.valueOf(now));
            stmt.setTimestamp(5, Timestamp.valueOf(estimatedEnd));
            stmt.executeUpdate();

            // Mark parking spot as occupied
            updateParkingSpotStatus(spotID, true);
            
            // Get the generated ParkingInfo_ID (serves as parking code)
            try (ResultSet generatedKeys = stmt.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    int parkingCode = generatedKeys.getInt(1);
                    return "Entry successful. Parking code: " + parkingCode + ". Spot: " + spotID;
                }
            }
        } catch (SQLException e) {
            System.out.println("Error handling entry: " + e.getMessage());
            return "Entry failed";
        }
        return "Entry failed";
    }

    /**
     * ATTENDANT-ONLY: Register new subscriber (EXISTING)
     */
    public String registerNewSubscriber(String attendantUserName, String name, String phone, 
                                       String email, String carNumber, String userName) {
        // Verify caller is attendant
        if (!hasRole(attendantUserName, UserRole.ATTENDANT)) {
            return "ERROR: Only parking attendants can register new subscribers";
        }
        
        // Continue with existing registration logic
        return registerNewSubscriberInternal(name, phone, email, carNumber, userName);
    }
    
    /**
     * Registers a new subscriber in the system - EXISTING
     */
    private String registerNewSubscriberInternal(String name, String phone, String email, String carNumber, String userName) {
        // Validate input
        if (name == null || name.trim().isEmpty()) {
            return "Name is required";
        }
        if (phone == null || phone.trim().isEmpty()) {
            return "Phone number is required";
        }
        if (email == null || email.trim().isEmpty()) {
            return "Email is required";
        }
        if (userName == null || userName.trim().isEmpty()) {
            return "Username is required";
        }
        
        // Check if username already exists
        String checkQry = "SELECT COUNT(*) FROM users WHERE UserName = ?";
        
        try (PreparedStatement checkStmt = conn.prepareStatement(checkQry)) {
            checkStmt.setString(1, userName);
            try (ResultSet rs = checkStmt.executeQuery()) {
                if (rs.next() && rs.getInt(1) > 0) {
                    return "Username already exists. Please choose a different username.";
                }
            }
        } catch (SQLException e) {
            System.out.println("Error checking username: " + e.getMessage());
            return "Error checking username availability";
        }
        
        // Insert new subscriber
        String insertQry = "INSERT INTO users (UserName, Name, Phone, Email, CarNum, UserTypeEnum) VALUES (?, ?, ?, ?, ?, 'sub')";
        
        try (PreparedStatement stmt = conn.prepareStatement(insertQry)) {
            stmt.setString(1, userName);
            stmt.setString(2, name);
            stmt.setString(3, phone);
            stmt.setString(4, email);
            stmt.setString(5, carNumber);
            
            int rowsInserted = stmt.executeUpdate();
            if (rowsInserted > 0) {
                System.out.println("New subscriber registered: " + userName);
                
                // SEND EMAIL NOTIFICATIONS
                EmailService.sendRegistrationConfirmation(email, name, userName);
                EmailService.sendWelcomeMessage(email, name, userName);
                
                return "SUCCESS:Subscriber registered successfully. Username: " + userName;
            }
        } catch (SQLException e) {
            System.out.println("Registration failed: " + e.getMessage());
            return "Registration failed: " + e.getMessage();
        }
        
        return "Registration failed: Unknown error";
    }

    /**
     * Generates a unique subscriber code/username - EXISTING
     */
    public String generateUniqueUsername(String baseName) {
        // Remove spaces and special characters
        String cleanName = baseName.replaceAll("[^a-zA-Z0-9]", "").toLowerCase();
        
        // Try the clean name first
        if (isUsernameAvailable(cleanName)) {
            return cleanName;
        }
        
        // If taken, try with numbers
        for (int i = 1; i <= 999; i++) {
            String candidate = cleanName + i;
            if (isUsernameAvailable(candidate)) {
                return candidate;
            }
        }
        
        // Fallback to random number
        return cleanName + System.currentTimeMillis() % 10000;
    }

    /**
     * Handles parking exit - EXISTING
     */
    public String exitParking(String parkingCodeStr) {
        try {
            int parkingCode = Integer.parseInt(parkingCodeStr);
            String qry = """
                SELECT pi.*, ps.ParkingSpot_ID 
                FROM ParkingInfo pi 
                JOIN ParkingSpot ps ON pi.ParkingSpot_ID = ps.ParkingSpot_ID 
                WHERE pi.ParkingInfo_ID = ? AND pi.statusEnum = 'active'
                """;
            
            try (PreparedStatement stmt = conn.prepareStatement(qry)) {
                stmt.setInt(1, parkingCode);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        int spotID = rs.getInt("ParkingSpot_ID");
                        Timestamp estimatedEndTime = rs.getTimestamp("Estimated_end_time");
                        int userID = rs.getInt("User_ID");
                        
                        LocalDateTime now = LocalDateTime.now();
                        LocalDateTime estimatedEnd = estimatedEndTime.toLocalDateTime();
                        
                        // Check if parking exceeded estimated time (late exit)
                        boolean isLate = now.isAfter(estimatedEnd);
                        
                        // Update parking info with exit time and status
                        String updateQry = """
                            UPDATE ParkingInfo 
                            SET Actual_end_time = ?, IsLate = ?, statusEnum = 'finished' 
                            WHERE ParkingInfo_ID = ?
                            """;
                        
                        try (PreparedStatement updateStmt = conn.prepareStatement(updateQry)) {
                            updateStmt.setTimestamp(1, Timestamp.valueOf(now));
                            updateStmt.setString(2, isLate ? "yes" : "no");
                            updateStmt.setInt(3, parkingCode);
                            updateStmt.executeUpdate();
                            
                            // Free the parking spot
                            updateParkingSpotStatus(spotID, false);
                            
                            if (isLate) {
                                sendLateExitNotification(userID);
                                return "Exit successful. You were late - please exit on time in the future";
                            }
                            
                            return "Exit successful. Thank you for using ParkB!";
                        }
                    }
                }
            }
        } catch (NumberFormatException e) {
            return "Invalid parking code format";
        } catch (SQLException e) {
            System.out.println("Error handling exit: " + e.getMessage());
        }
        return "Invalid parking code or parking session not active";
    }

    /**
     * Extends parking time - EXISTING WITH SMART ENHANCEMENTS
     */
    public String extendParkingTime(String parkingCodeStr, int additionalHours) {
        if (additionalHours < 1 || additionalHours > MAXIMUM_EXTENSION_HOURS) {
            return "Can only extend parking by 1-" + MAXIMUM_EXTENSION_HOURS + " hours";
        }
        
        try {
            int parkingCode = Integer.parseInt(parkingCodeStr);
            
            // Get current parking session info
            String getUserQry = """
                SELECT pi.*, u.Email, u.Name, ps.ParkingSpot_ID
                FROM ParkingInfo pi 
                JOIN users u ON pi.User_ID = u.User_ID 
                JOIN ParkingSpot ps ON pi.ParkingSpot_ID = ps.ParkingSpot_ID
                WHERE pi.ParkingInfo_ID = ? AND pi.statusEnum = 'active'
                """;
            
            try (PreparedStatement stmt = conn.prepareStatement(getUserQry)) {
                stmt.setInt(1, parkingCode);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        Timestamp currentEstimatedEnd = rs.getTimestamp("Estimated_end_time");
                        String isExtended = rs.getString("IsExtended");
                        String userEmail = rs.getString("Email");
                        String userName = rs.getString("Name");
                        int spotId = rs.getInt("ParkingSpot_ID");
                        
                        // Check if already extended
                        if ("yes".equals(isExtended)) {
                            return "Parking time can only be extended once per session";
                        }
                        
                        LocalDateTime now = LocalDateTime.now();
                        LocalDateTime estimatedEnd = currentEstimatedEnd.toLocalDateTime();
                        
                        // Check if within last hour
                        long minutesRemaining = java.time.Duration.between(now, estimatedEnd).toMinutes();
                        if (minutesRemaining > 60) {
                            return "Extension only allowed in the last 60 minutes of parking time";
                        }
                        
                        // Check if already expired
                        if (now.isAfter(estimatedEnd)) {
                            return "Cannot extend expired parking session";
                        }
                        
                        // NEW: Smart extension - check if spot is available for the extension period
                        int maxExtensionHours = findMaximumExtension(spotId, estimatedEnd);
                        if (maxExtensionHours < additionalHours) {
                            return "Can only extend by " + maxExtensionHours + " hours due to upcoming reservations";
                        }
                        
                        LocalDateTime newEstimatedEnd = estimatedEnd.plusHours(additionalHours);
                        
                        String updateQry = """
                            UPDATE ParkingInfo 
                            SET Estimated_end_time = ?, IsExtended = 'yes' 
                            WHERE ParkingInfo_ID = ?
                            """;
                        
                        try (PreparedStatement updateStmt = conn.prepareStatement(updateQry)) {
                            updateStmt.setTimestamp(1, Timestamp.valueOf(newEstimatedEnd));
                            updateStmt.setInt(2, parkingCode);
                            updateStmt.executeUpdate();
                            
                            // SEND EMAIL NOTIFICATION
                            if (userEmail != null && userName != null) {
                                EmailService.sendExtensionConfirmation(
                                    userEmail, userName, parkingCodeStr, 
                                    additionalHours, newEstimatedEnd.format(DateTimeFormatter.ofPattern("HH:mm"))
                                );
                            }
                            
                            return "Smart parking extension successful! Extended by " + additionalHours + " hours until " + 
                                   newEstimatedEnd.format(DateTimeFormatter.ofPattern("HH:mm"));
                        }
                    }
                }
            }
        } catch (NumberFormatException e) {
            return "Invalid parking code format";
        } catch (SQLException e) {
            System.out.println("Error extending parking time: " + e.getMessage());
        }
        return "Invalid parking code or parking session not active";
    }

    /**
     * Sends lost parking code to user - EXISTING
     */
    public String sendLostParkingCode(String userName) {
        String qry = """
            SELECT pi.ParkingInfo_ID, u.Email, u.Phone, u.Name 
            FROM ParkingInfo pi 
            JOIN users u ON pi.User_ID = u.User_ID 
            WHERE u.UserName = ? AND pi.statusEnum = 'active'
            """;
        
        try (PreparedStatement stmt = conn.prepareStatement(qry)) {
            stmt.setString(1, userName);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    int parkingCode = rs.getInt("ParkingInfo_ID");
                    String email = rs.getString("Email");
                    String phone = rs.getString("Phone");
                    String name = rs.getString("Name");
                    
                    // SEND EMAIL NOTIFICATION
                    EmailService.sendParkingCodeRecovery(email, name, String.valueOf(parkingCode));
                    
                    return String.valueOf(parkingCode);
                }
            }
        } catch (SQLException e) {
            System.out.println("Error sending lost code: " + e.getMessage());
        }
        return "No active parking session found";
    }

    /**
     * Gets parking history for a user - EXISTING
     */
    public ArrayList<ParkingOrder> getParkingHistory(String userName) {
        ArrayList<ParkingOrder> history = new ArrayList<>();
        String qry = """
            SELECT pi.*, ps.ParkingSpot_ID 
            FROM ParkingInfo pi 
            JOIN users u ON pi.User_ID = u.User_ID 
            JOIN ParkingSpot ps ON pi.ParkingSpot_ID = ps.ParkingSpot_ID 
            WHERE u.UserName = ? 
            ORDER BY COALESCE(pi.Actual_start_time, pi.Estimated_start_time) DESC
            """;
        
        try (PreparedStatement stmt = conn.prepareStatement(qry)) {
            stmt.setString(1, userName);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    ParkingOrder order = new ParkingOrder();
                    order.setOrderID(rs.getInt("ParkingInfo_ID"));
                    order.setParkingCode(String.valueOf(rs.getInt("ParkingInfo_ID")));
                    order.setOrderType(rs.getString("IsOrderedEnum"));
                    order.setSpotNumber("Spot " + rs.getInt("ParkingSpot_ID"));
                    
                    // Set entry time (actual or estimated)
                    Timestamp actualStart = rs.getTimestamp("Actual_start_time");
                    Timestamp estimatedStart = rs.getTimestamp("Estimated_start_time");
                    if (actualStart != null) {
                        order.setEntryTime(actualStart.toLocalDateTime());
                    } else if (estimatedStart != null) {
                        order.setEntryTime(estimatedStart.toLocalDateTime());
                    }
                    
                    // Set exit time
                    Timestamp actualEnd = rs.getTimestamp("Actual_end_time");
                    if (actualEnd != null) {
                        order.setExitTime(actualEnd.toLocalDateTime());
                    }
                    
                    // Set expected exit time
                    Timestamp estimatedEnd = rs.getTimestamp("Estimated_end_time");
                    if (estimatedEnd != null) {
                        order.setExpectedExitTime(estimatedEnd.toLocalDateTime());
                    }
                    
                    order.setLate("yes".equals(rs.getString("IsLate")));
                    order.setExtended("yes".equals(rs.getString("IsExtended")));
                    order.setStatus(rs.getString("statusEnum"));
                    
                    history.add(order);
                }
            }
        } catch (SQLException e) {
            System.out.println("Error getting parking history: " + e.getMessage());
        }
        return history;
    }

    /**
     * Gets all active parking sessions - EXISTING
     */
    public ArrayList<ParkingOrder> getActiveParkings() {
        ArrayList<ParkingOrder> activeParkings = new ArrayList<>();
        String qry = """
            SELECT pi.*, u.Name, ps.ParkingSpot_ID 
            FROM ParkingInfo pi 
            JOIN users u ON pi.User_ID = u.User_ID 
            JOIN ParkingSpot ps ON pi.ParkingSpot_ID = ps.ParkingSpot_ID 
            WHERE pi.statusEnum = 'active' 
            ORDER BY pi.Actual_start_time
            """;
        
        try (PreparedStatement stmt = conn.prepareStatement(qry)) {
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    ParkingOrder order = new ParkingOrder();
                    order.setOrderID(rs.getInt("ParkingInfo_ID"));
                    order.setParkingCode(String.valueOf(rs.getInt("ParkingInfo_ID")));
                    order.setOrderType(rs.getString("IsOrderedEnum"));
                    order.setSubscriberName(rs.getString("Name"));
                    order.setSpotNumber("Spot " + rs.getInt("ParkingSpot_ID"));
                    
                    // Set entry time
                    Timestamp actualStart = rs.getTimestamp("Actual_start_time");
                    if (actualStart != null) {
                        order.setEntryTime(actualStart.toLocalDateTime());
                    }
                    
                    // Set expected exit time
                    Timestamp estimatedEnd = rs.getTimestamp("Estimated_end_time");
                    if (estimatedEnd != null) {
                        order.setExpectedExitTime(estimatedEnd.toLocalDateTime());
                    }
                    
                    order.setStatus("active");
                    activeParkings.add(order);
                }
            }
        } catch (SQLException e) {
            System.out.println("Error getting active parkings: " + e.getMessage());
        }
        return activeParkings;
    }

    /**
     * Updates subscriber information - EXISTING
     */
    public String updateSubscriberInfo(String updateData) {
        // Format: userName,phone,email
        String[] data = updateData.split(",");
        if (data.length != 3) {
            return "Invalid update data format";
        }
        
        String userName = data[0];
        String phone = data[1];
        String email = data[2];
        
        String qry = "UPDATE users SET Phone = ?, Email = ? WHERE UserName = ?";
        
        try (PreparedStatement stmt = conn.prepareStatement(qry)) {
            stmt.setString(1, phone);
            stmt.setString(2, email);
            stmt.setString(3, userName);
            
            int rowsUpdated = stmt.executeUpdate();
            if (rowsUpdated > 0) {
                return "Subscriber information updated successfully";
            }
        } catch (SQLException e) {
            System.out.println("Error updating subscriber info: " + e.getMessage());
        }
        return "Failed to update subscriber information";
    }

    /**
     * Activate reservation when customer arrives - EXISTING
     */
    public String activateReservation(String userName, int reservationCode) {
        // Check if reservation exists and is in preorder status
        String checkQry = """
            SELECT pi.*, 
                   TIMESTAMPDIFF(MINUTE, pi.Estimated_start_time, NOW()) as minutes_since_start
            FROM ParkingInfo pi 
            WHERE pi.ParkingInfo_ID = ? AND pi.statusEnum = 'preorder'
            """;
        
        try (PreparedStatement stmt = conn.prepareStatement(checkQry)) {
            stmt.setInt(1, reservationCode);
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    int minutesSinceStart = rs.getInt("minutes_since_start");
                    int spotId = rs.getInt("ParkingSpot_ID");
                    
                    // Check if within 15-minute grace period
                    if (minutesSinceStart > 15) {
                        // Too late - auto-cancel
                        cancelReservation(null, reservationCode);
                        return "Reservation cancelled due to late arrival (over 15 minutes). Please make a new reservation.";
                    }
                    
                    LocalDateTime now = LocalDateTime.now();
                    
                    // Update reservation to active status
                    String updateQry = """
                        UPDATE ParkingInfo 
                        SET Actual_start_time = ?, 
                            statusEnum = 'active',
                            IsLate = 'no'
                        WHERE ParkingInfo_ID = ?
                        """;
                    
                    try (PreparedStatement updateStmt = conn.prepareStatement(updateQry)) {
                        updateStmt.setTimestamp(1, Timestamp.valueOf(now));
                        updateStmt.setInt(2, reservationCode);
                        updateStmt.executeUpdate();
                        
                        // Mark parking spot as occupied
                        updateParkingSpotStatus(spotId, true);
                        
                        String message;
                        if (minutesSinceStart < 0) {
                            message = String.format("Welcome! You arrived %d minutes early.", Math.abs(minutesSinceStart));
                        } else if (minutesSinceStart == 0) {
                            message = "Welcome! Perfect timing - right on schedule.";
                        } else {
                            message = String.format("Welcome! You arrived %d minutes late (within grace period).", minutesSinceStart);
                        }
                        
                        System.out.println("Reservation " + reservationCode + " activated (preorder → active)");
                        
                        return String.format("Smart reservation activated! %s\nParking code: %d, Spot: %d", 
                                           message, reservationCode, spotId);
                    }
                }
            }
        } catch (SQLException e) {
            System.out.println("Error activating reservation: " + e.getMessage());
            return "Failed to activate reservation";
        }
        
        return "Reservation not found or already activated";
    }

    /**
     * PUBLIC method - Cancels a reservation - EXISTING
     */
    public String cancelReservation(String userName, int reservationCode) {
        return cancelReservationInternal(reservationCode);
    }

    /**
     * Internal cancellation method - EXISTING
     */
    private String cancelReservationInternal(int reservationCode) {
        // Get reservation info first for email notification
        String getUserQry = """
            SELECT u.Email, u.Name, pi.statusEnum, pi.ParkingSpot_ID
            FROM ParkingInfo pi 
            JOIN users u ON pi.User_ID = u.User_ID 
            WHERE pi.ParkingInfo_ID = ?
            """;
        
        String userEmail = null;
        String userName = null;
        String currentStatus = null;
        Integer spotId = null;
        
        try (PreparedStatement stmt = conn.prepareStatement(getUserQry)) {
            stmt.setInt(1, reservationCode);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    userEmail = rs.getString("Email");
                    userName = rs.getString("Name");
                    currentStatus = rs.getString("statusEnum");
                    spotId = rs.getInt("ParkingSpot_ID");
                }
            }
        } catch (SQLException e) {
            System.out.println("Error getting reservation info for cancellation: " + e.getMessage());
        }
        
        // Check if status is preorder
        if (!"preorder".equals(currentStatus)) {
            if ("active".equals(currentStatus)) {
                return "Cannot cancel active parking session. Please exit properly.";
            } else if ("finished".equals(currentStatus)) {
                return "This parking session is already completed.";
            } else if ("cancelled".equals(currentStatus)) {
                return "This reservation is already cancelled.";
            }
            return "Invalid reservation status for cancellation.";
        }
        
        // Update reservation status to cancelled
        String qry = "UPDATE ParkingInfo SET statusEnum = 'cancelled' WHERE ParkingInfo_ID = ? AND statusEnum = 'preorder'";
        
        try (PreparedStatement stmt = conn.prepareStatement(qry)) {
            stmt.setInt(1, reservationCode);
            int rowsUpdated = stmt.executeUpdate();
            
            if (rowsUpdated > 0) {
                // Free up the spot if it was assigned
                if (spotId != null) {
                    updateParkingSpotStatus(spotId, false);
                }
                
                // Send email notification
                if (userEmail != null && userName != null) {
                    EmailService.sendReservationCancelled(userEmail, userName, String.valueOf(reservationCode));
                }
                
                System.out.println("Reservation " + reservationCode + " cancelled (preorder → cancelled)");
                return "Reservation cancelled successfully";
            }
        } catch (SQLException e) {
            System.out.println("Error cancelling reservation: " + e.getMessage());
        }
        
        return "Failed to cancel reservation";
    }

    /**
     * Logs out a user - EXISTING
     */
    public void logoutUser(String userName) {
        System.out.println("User logged out: " + userName);
    }

    /**
     * Initializes parking spots if they don't exist - EXISTING
     */
    public void initializeParkingSpots() {
        try {
            // Check if spots already exist
            String checkQry = "SELECT COUNT(*) FROM ParkingSpot";
            try (PreparedStatement stmt = conn.prepareStatement(checkQry)) {
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next() && rs.getInt(1) == 0) {
                        // Initialize parking spots - AUTO_INCREMENT will handle ParkingSpot_ID
                        String insertQry = "INSERT INTO ParkingSpot (isOccupied) VALUES (false)";
                        try (PreparedStatement insertStmt = conn.prepareStatement(insertQry)) {
                            for (int i = 1; i <= TOTAL_PARKING_SPOTS; i++) {
                                insertStmt.executeUpdate();
                            }
                        }
                        System.out.println("Successfully initialized " + TOTAL_PARKING_SPOTS + " parking spots with smart allocation");
                    } else {
                        System.out.println("Parking spots already exist: " + rs.getInt(1) + " spots found");
                    }
                }
            }
        } catch (SQLException e) {
            System.out.println("Error initializing parking spots: " + e.getMessage());
        }
    }

    // ========== NEW SMART HELPER METHODS ==========

    /**
     * Align datetime to 15-minute time slots
     */
    private LocalDateTime alignToTimeSlot(LocalDateTime dateTime) {
        int minutes = dateTime.getMinute();
        int alignedMinutes = (minutes / TIME_SLOT_MINUTES) * TIME_SLOT_MINUTES;
        return dateTime.withMinute(alignedMinutes).withSecond(0).withNano(0);
    }

    /**
     * Check if there's a valid 4-hour window with sufficient capacity
     */
    private boolean hasValidFourHourWindow(LocalDateTime startTime, LocalDateTime endTime) {
        try {
            int availableSpots = countAvailableSpotsForWindow(startTime, endTime);
            return availableSpots >= (TOTAL_PARKING_SPOTS * RESERVATION_THRESHOLD);
        } catch (Exception e) {
            System.out.println("Error checking four-hour window: " + e.getMessage());
            return false;
        }
    }

    /**
     * Count available spots for a specific time window
     */
    private int countAvailableSpotsForWindow(LocalDateTime startTime, LocalDateTime endTime) {
        try {
            String query = """
                SELECT COUNT(*) as conflicts FROM ParkingInfo 
                WHERE statusEnum IN ('preorder', 'active')
                AND NOT (
                    Estimated_end_time <= ? OR 
                    Estimated_start_time >= ?
                )
                """;
            
            try (PreparedStatement stmt = conn.prepareStatement(query)) {
                stmt.setTimestamp(1, Timestamp.valueOf(startTime));
                stmt.setTimestamp(2, Timestamp.valueOf(endTime));
                
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        int conflicts = rs.getInt("conflicts");
                        return Math.max(0, TOTAL_PARKING_SPOTS - conflicts);
                    }
                }
            }
        } catch (SQLException e) {
            System.out.println("Error counting available spots: " + e.getMessage());
        }
        return 0;
    }

    /**
     * Find optimal parking spot for a time window
     */
    private int findOptimalSpotForTimeWindow(LocalDateTime startTime, LocalDateTime endTime) {
        try {
            String query = """
                SELECT ps.ParkingSpot_ID 
                FROM ParkingSpot ps 
                WHERE ps.ParkingSpot_ID NOT IN (
                    SELECT pi.ParkingSpot_ID 
                    FROM ParkingInfo pi 
                    WHERE pi.statusEnum IN ('preorder', 'active') 
                    AND NOT (
                        pi.Estimated_end_time <= ? OR 
                        pi.Estimated_start_time >= ?
                    )
                ) 
                ORDER BY ps.ParkingSpot_ID
                LIMIT 1
                """;
            
            try (PreparedStatement stmt = conn.prepareStatement(query)) {
                stmt.setTimestamp(1, Timestamp.valueOf(startTime));
                stmt.setTimestamp(2, Timestamp.valueOf(endTime));
                
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return rs.getInt("ParkingSpot_ID");
                    }
                }
            }
        } catch (SQLException e) {
            System.out.println("Error finding optimal spot: " + e.getMessage());
        }
        return -1;
    }

    /**
     * Find optimal allocation for spontaneous parking
     */
    private SpotAllocation findOptimalSpontaneousAllocation(LocalDateTime startTime) {
        try {
            // Try different durations, starting with the longest
            for (int hours = PREFERRED_WINDOW_HOURS; hours >= MINIMUM_SPONTANEOUS_HOURS; hours--) {
                LocalDateTime endTime = startTime.plusHours(hours);
                
                int spotId = findOptimalSpotForTimeWindow(startTime, endTime);
                if (spotId != -1) {
                    return new SpotAllocation(spotId, hours, hours >= PREFERRED_WINDOW_HOURS);
                }
            }
            
            return null;
            
        } catch (Exception e) {
            System.out.println("Error finding spontaneous allocation: " + e.getMessage());
            return null;
        }
    }

    /**
     * Find maximum extension hours based on spot availability
     */
    private int findMaximumExtension(int spotId, LocalDateTime currentEndTime) {
        try {
            for (int hours = MAXIMUM_EXTENSION_HOURS; hours >= 1; hours--) {
                LocalDateTime testEndTime = currentEndTime.plusHours(hours);
                
                String query = """
                    SELECT COUNT(*) FROM ParkingInfo 
                    WHERE ParkingSpot_ID = ? 
                    AND statusEnum IN ('preorder', 'active')
                    AND Estimated_start_time < ? 
                    AND Estimated_end_time > ?
                    """;
                
                try (PreparedStatement stmt = conn.prepareStatement(query)) {
                    stmt.setInt(1, spotId);
                    stmt.setTimestamp(2, Timestamp.valueOf(testEndTime));
                    stmt.setTimestamp(3, Timestamp.valueOf(currentEndTime));
                    
                    try (ResultSet rs = stmt.executeQuery()) {
                        if (rs.next() && rs.getInt(1) == 0) {
                            return hours; // No conflicts found
                        }
                    }
                }
            }
        } catch (SQLException e) {
            System.out.println("Error finding maximum extension: " + e.getMessage());
        }
        return 0;
    }

//    /**
//     * Parse datetime string in various formats
//     */
//    private LocalDateTime parseDateTime(String dateTimeStr) {
//        try {
//            // Try "YYYY-MM-DD HH:MM:SS" format first
//            if (dateTimeStr.matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}")) {
//                return LocalDateTime.parse(dateTimeStr, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
//            }
//            // Try "YYYY-MM-DD HH:MM" format
//            else if (dateTimeStr.matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}")) {
//                return LocalDateTime.parse(dateTimeStr, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
//            }
//            // Try ISO format "YYYY-MM-DDTHH:MM"
//            else if (dateTimeStr.contains("T")) {
//                return LocalDateTime.parse(dateTimeStr);
//            }
//            else {
//                throw new IllegalArgumentException("Unsupported datetime format: " + dateTimeStr);
//            }
//        } catch (Exception e) {
//            throw new IllegalArgumentException("Invalid datetime format: " + dateTimeStr + 
//                                             ". Use 'YYYY-MM-DD HH:MM' or 'YYYY-MM-DD HH:MM:SS'");
//        }
//    }

    // ========== EXISTING HELPER METHODS (UNCHANGED) ==========
    
    private int getUserID(String userName) {
        String qry = "SELECT User_ID FROM users WHERE UserName = ?";
        
        try (PreparedStatement stmt = conn.prepareStatement(qry)) {
            stmt.setString(1, userName);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("User_ID");
                }
            }
        } catch (SQLException e) {
            System.out.println("Error getting user ID: " + e.getMessage());
        }
        return -1;
    }

    private int getAvailableParkingSpotID() {
        String qry = "SELECT ParkingSpot_ID FROM ParkingSpot WHERE isOccupied = false LIMIT 1";
        
        try (PreparedStatement stmt = conn.prepareStatement(qry)) {
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("ParkingSpot_ID");
                }
            }
        } catch (SQLException e) {
            System.out.println("Error getting available spot ID: " + e.getMessage());
        }
        return -1;
    }

    private void updateParkingSpotStatus(int spotID, boolean isOccupied) {
        String qry = "UPDATE ParkingSpot SET isOccupied = ? WHERE ParkingSpot_ID = ?";
        
        try (PreparedStatement stmt = conn.prepareStatement(qry)) {
            stmt.setBoolean(1, isOccupied);
            stmt.setInt(2, spotID);
            stmt.executeUpdate();
        } catch (SQLException e) {
            System.out.println("Error updating parking spot status: " + e.getMessage());
        }
    }

    /**
     * Send late exit notification
     */
    private void sendLateExitNotification(int userID) {
        String qry = "SELECT Email, Phone, Name FROM users WHERE User_ID = ?";
        
        try (PreparedStatement stmt = conn.prepareStatement(qry)) {
            stmt.setInt(1, userID);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    String email = rs.getString("Email");
                    String phone = rs.getString("Phone");
                    String name = rs.getString("Name");
                    
                    // SEND EMAIL NOTIFICATION
                    EmailService.sendLatePickupNotification(email, name);
                }
            }
        } catch (SQLException e) {
            System.out.println("Error sending late notification: " + e.getMessage());
        }
    }
    
    private boolean isUsernameAvailable(String userName) {
        String checkQry = "SELECT COUNT(*) FROM users WHERE UserName = ?";
        
        try (PreparedStatement stmt = conn.prepareStatement(checkQry)) {
            stmt.setString(1, userName);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) == 0;
                }
            }
        } catch (SQLException e) {
            System.out.println("Error checking username availability: " + e.getMessage());
        }
        
        return false;
    }
    
    /**
     * Get user info by ID - PUBLIC for SimpleAutoCancellationService
     */
    public ParkingSubscriber getUserInfoById(int userID) {
        String qry = "SELECT * FROM users WHERE User_ID = ?";
        
        try (PreparedStatement stmt = conn.prepareStatement(qry)) {
            stmt.setInt(1, userID);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    ParkingSubscriber user = new ParkingSubscriber();
                    user.setSubscriberID(rs.getInt("User_ID"));
                    user.setFirstName(rs.getString("Name"));
                    user.setPhoneNumber(rs.getString("Phone"));
                    user.setEmail(rs.getString("Email"));
                    user.setCarNumber(rs.getString("CarNum"));
                    user.setUserType(rs.getString("UserTypeEnum"));
                    return user;
                }
            }
        } catch (SQLException e) {
            System.out.println("Error getting user info by ID: " + e.getMessage());
        }
        return null;
    }
    
    /**
     * Enter parking with reservation (for backward compatibility)
     */
    public String enterParkingWithReservation(int reservationCode) {
        // In the unified system, this is the same as activating a reservation
        return activateReservation(null, reservationCode);
    }

    // ========== NEW SMART SYSTEM METHODS ==========

    /**
     * Get parking system status with smart analytics
     */
    public String getSystemStatus() {
        try {
            int totalSpots = TOTAL_PARKING_SPOTS;
            int occupiedSpots = getCurrentlyOccupiedSpots();
            int availableSpots = totalSpots - occupiedSpots;
            int preorderReservations = getPreorderCount();
            
            return String.format("Smart Parking Status: %d total spots, %d occupied, %d available (%.1f%% available), %d pending reservations",
                               totalSpots, occupiedSpots, availableSpots, 
                               (double) availableSpots / totalSpots * 100, preorderReservations);
                               
        } catch (Exception e) {
            return "Error getting system status: " + e.getMessage();
        }
    }

    private int getCurrentlyOccupiedSpots() throws SQLException {
        String query = "SELECT COUNT(*) FROM ParkingSpot WHERE isOccupied = true";
        
        try (PreparedStatement stmt = conn.prepareStatement(query)) {
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }
        return 0;
    }

    private int getPreorderCount() throws SQLException {
        String query = "SELECT COUNT(*) FROM ParkingInfo WHERE statusEnum = 'preorder'";
        
        try (PreparedStatement stmt = conn.prepareStatement(query)) {
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }
        return 0;
    }
}