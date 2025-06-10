package controllers;

import java.sql.Connection;
import java.sql.Date;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Time;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Random;

import entities.ParkingOrder;
import entities.ParkingSubscriber;
import services.EmailService; // 🆕 ADD THIS IMPORT

/**
 * Enhanced ParkingController with email notifications
 * Handles all database operations for the ParkB parking management system.
 */
public class ParkingController {
    protected Connection conn;
    public int successFlag;
    private static final int TOTAL_PARKING_SPOTS = 100;
    private static final double RESERVATION_THRESHOLD = 0.4;
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

    /**
     * Check if user has any of the required roles
     */
    private boolean hasAnyRole(String userName, UserRole... requiredRoles) {
        UserRole userRole = getUserRole(userName);
        if (userRole == null) return false;
        
        for (UserRole role : requiredRoles) {
            if (userRole == role) return true;
        }
        return false;
    }
    
    // Auto-cancellation service
    private SimpleAutoCancellationService autoCancellationService;

    public ParkingController(String dbname, String pass) {
        String connectPath = "jdbc:mysql://localhost/" + dbname + "?serverTimezone=IST";
        connectToDB(connectPath, pass);
        
        // Initialize auto-cancellation service after DB connection
        if (successFlag == 1) {
            this.autoCancellationService = new SimpleAutoCancellationService(this);
            startAutoCancellationService();
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
        if (autoCancellationService != null) {
            autoCancellationService.shutdown();
        }
    }

    // ========== ALL YOUR EXISTING METHODS ==========
    
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

    /**
     * Checks if reservation is possible (40% of spots must be available)
     */
    public boolean canMakeReservation() {
        int availableSpots = getAvailableParkingSpots();
        return availableSpots >= (TOTAL_PARKING_SPOTS * RESERVATION_THRESHOLD);
    }

    /**
     * Makes a parking reservation with specific DATE and TIME
     * Format: "YYYY-MM-DD HH:MM" or "YYYY-MM-DD HH:MM:SS"
     */
    public String makeReservation(String userName, String reservationDateTimeStr) {
        // Check if reservation is possible (40% rule)
        if (!canMakeReservation()) {
            return "Not enough available spots for reservation (need 40% available)";
        }

        try {
            // Parse the datetime string
            LocalDateTime reservationDateTime = parseDateTime(reservationDateTimeStr);
            
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

            // Find available parking spot
            int parkingSpotID = getAvailableParkingSpotID();
            if (parkingSpotID == -1) {
                return "No available parking spots";
            }

            // Calculate end time (default 4 hours)
            LocalDateTime estimatedEndTime = reservationDateTime.plusHours(4);

            // Create reservation with DATETIME
            String qry = """
                INSERT INTO Reservations 
                (User_ID, parking_ID, reservation_Date, reservation_start_time, reservation_end_time,
                 Date_Of_Placing_Order, statusEnum, assigned_parking_spot_id) 
                VALUES (?, ?, ?, ?, ?, NOW(), 'preorder', ?)
                """;
            
            try (PreparedStatement stmt = conn.prepareStatement(qry, PreparedStatement.RETURN_GENERATED_KEYS)) {
                stmt.setInt(1, userID);
                stmt.setInt(2, parkingSpotID);
                stmt.setDate(3, Date.valueOf(reservationDateTime.toLocalDate()));
                stmt.setTime(4, Time.valueOf(reservationDateTime.toLocalTime()));
                stmt.setTime(5, Time.valueOf(estimatedEndTime.toLocalTime()));
                stmt.setInt(6, parkingSpotID);
                stmt.executeUpdate();
                
                // Get the generated reservation code
                try (ResultSet generatedKeys = stmt.getGeneratedKeys()) {
                    if (generatedKeys.next()) {
                        int reservationCode = generatedKeys.getInt(1);
                        System.out.println("New preorder reservation created: " + reservationCode + 
                                         " for " + reservationDateTime + " (15-min auto-cancel rule applies)");
                        
                        // Send email confirmation
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
                            ". Confirmation code: " + reservationCode;
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
     * Parse datetime string in various formats
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

    /**
     * Handles parking entry with subscriber code (immediate parking)
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

        // Generate unique parking code
        int parkingCode = generateParkingCode();
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime estimatedEnd = now.plusHours(4); // Default 4 hours

        // Create parking info record
        String qry = "INSERT INTO ParkingInfo (ParkingSpot_ID, User_ID, Date, Code, Actual_start_time, Estimated_start_time, Estimated_end_time, IsOrderedEnum, IsLate, IsExtended) VALUES (?, ?, ?, ?, ?, ?, ?, 'not ordered', false, false)";
        
        try (PreparedStatement stmt = conn.prepareStatement(qry)) {
            stmt.setInt(1, spotID);
            stmt.setInt(2, userID);
            stmt.setDate(3, Date.valueOf(now.toLocalDate()));
            stmt.setInt(4, parkingCode);
            stmt.setTime(5, Time.valueOf(now.toLocalTime()));
            stmt.setTime(6, Time.valueOf(now.toLocalTime()));
            stmt.setTime(7, Time.valueOf(estimatedEnd.toLocalTime()));
            stmt.executeUpdate();

            // Mark parking spot as occupied
            updateParkingSpotStatus(spotID, true);
            
            return "Entry successful. Parking code: " + parkingCode + ". Spot: " + spotID;
        } catch (SQLException e) {
            System.out.println("Error handling entry: " + e.getMessage());
            return "Entry failed";
        }
    }

    /**
     * Handles parking entry with reservation code - NOW SUPPORTS PREORDER->ACTIVE
     */
    public String enterParkingWithReservation(int reservationCode) {
        // Check if reservation exists and is in preorder status
        String checkQry = "SELECT r.*, u.User_ID FROM Reservations r JOIN users u ON r.User_ID = u.User_ID WHERE r.Reservation_code = ? AND r.statusEnum = 'preorder'";
        
        try (PreparedStatement stmt = conn.prepareStatement(checkQry)) {
            stmt.setInt(1, reservationCode);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    Date reservationDate = rs.getDate("reservation_Date");
                    int userID = rs.getInt("User_ID");
                    int parkingSpotID = rs.getInt("assigned_parking_spot_id");
                    
                    // Check if reservation is for today
                    LocalDate today = LocalDate.now();
                    if (!reservationDate.toLocalDate().equals(today)) {
                        if (reservationDate.toLocalDate().isBefore(today)) {
                            // Cancel expired reservation
                            cancelReservation(reservationCode);
                            return "Reservation expired";
                        } else {
                            return "Reservation is for future date";
                        }
                    }

                    // Check if spot is still available
                    if (!isParkingSpotAvailable(parkingSpotID)) {
                        // Find another available spot
                        parkingSpotID = getAvailableParkingSpotID();
                        if (parkingSpotID == -1) {
                            return "No available parking spots found";
                        }
                    }

                    // Generate parking code
                    int parkingCode = generateParkingCode();
                    LocalDateTime now = LocalDateTime.now();
                    LocalDateTime estimatedEnd = now.plusHours(4);

                    // Create parking info record
                    String insertQry = "INSERT INTO ParkingInfo (ParkingSpot_ID, User_ID, Date, Code, Actual_start_time, Estimated_start_time, Estimated_end_time, IsOrderedEnum, IsLate, IsExtended) VALUES (?, ?, ?, ?, ?, ?, ?, 'ordered', false, false)";
                    
                    try (PreparedStatement insertStmt = conn.prepareStatement(insertQry)) {
                        insertStmt.setInt(1, parkingSpotID);
                        insertStmt.setInt(2, userID);
                        insertStmt.setDate(3, Date.valueOf(now.toLocalDate()));
                        insertStmt.setInt(4, parkingCode);
                        insertStmt.setTime(5, Time.valueOf(now.toLocalTime()));
                        insertStmt.setTime(6, Time.valueOf(now.toLocalTime()));
                        insertStmt.setTime(7, Time.valueOf(estimatedEnd.toLocalTime()));
                        insertStmt.executeUpdate();

                        // Mark parking spot as occupied and change reservation to active
                        updateParkingSpotStatus(parkingSpotID, true);
                        updateReservationStatus(reservationCode, "active");
                        
                        System.out.println("Reservation " + reservationCode + " activated (preorder → active)");
                        return "Entry successful! Reservation activated. Parking code: " + parkingCode + ". Spot: " + parkingSpotID;
                    }
                }
            }
        } catch (SQLException e) {
            System.out.println("Error handling reservation entry: " + e.getMessage());
        }
        return "Invalid reservation code or reservation not in preorder status";
    }
    
    /**
     * ATTENDANT-ONLY: Register new subscriber (PDF requirement)
     * Only attendants can register new users
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
     * Registers a new subscriber in the system - WITH EMAIL NOTIFICATIONS
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
                
                // 🆕 SEND EMAIL NOTIFICATIONS
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
     * Generates a unique subscriber code/username
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
     * Handles parking exit - NOW SUPPORTS FINISHING RESERVATIONS
     */
    public String exitParking(String parkingCodeStr) {
        try {
            int parkingCode = Integer.parseInt(parkingCodeStr);
            String qry = "SELECT pi.*, ps.ParkingSpot_ID FROM ParkingInfo pi JOIN ParkingSpot ps ON pi.ParkingSpot_ID = ps.ParkingSpot_ID WHERE pi.Code = ? AND pi.Actual_end_time IS NULL";
            
            try (PreparedStatement stmt = conn.prepareStatement(qry)) {
                stmt.setInt(1, parkingCode);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        int parkingInfoID = rs.getInt("ParkingInfo_ID");
                        int spotID = rs.getInt("ParkingSpot_ID");
                        Time estimatedEndTime = rs.getTime("Estimated_end_time");
                        int userID = rs.getInt("User_ID");
                        String orderType = rs.getString("IsOrderedEnum");
                        
                        LocalTime now = LocalTime.now();
                        LocalTime estimatedEnd = estimatedEndTime.toLocalTime();
                        
                        // Check if parking exceeded estimated time
                        boolean isLate = now.isAfter(estimatedEnd);
                        
                        // Update parking info with exit time
                        String updateQry = "UPDATE ParkingInfo SET Actual_end_time = ?, IsLate = ? WHERE ParkingInfo_ID = ?";
                        
                        try (PreparedStatement updateStmt = conn.prepareStatement(updateQry)) {
                            updateStmt.setTime(1, Time.valueOf(now));
                            updateStmt.setBoolean(2, isLate);
                            updateStmt.setInt(3, parkingInfoID);
                            updateStmt.executeUpdate();
                            
                            // Free the parking spot
                            updateParkingSpotStatus(spotID, false);
                            
                            // If this was from a reservation, finish the reservation
                            if ("ordered".equals(orderType)) {
                                finishReservationBySpotAndUser(spotID, userID);
                            }
                            
                            if (isLate) {
                                sendLateExitNotification(userID);
                                return "Exit successful. You were late - please arrive on time for future reservations";
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
        return "Invalid parking code or already exited";
    }

    /**
     * Extends parking time - 🔧 FIXED COMPILATION ERRORS
     */
    public String extendParkingTime(String parkingCodeStr, int additionalHours) {
        if (additionalHours < 1 || additionalHours > 4) {
            return "Can only extend parking by 1-4 hours";
        }
        
        try {
            int parkingCode = Integer.parseInt(parkingCodeStr);
            
            // 🔧 FIXED: Get user info for email notification
            String getUserQry = "SELECT pi.*, u.Email, u.Name FROM ParkingInfo pi JOIN users u ON pi.User_ID = u.User_ID WHERE pi.Code = ? AND pi.Actual_end_time IS NULL";
            
            try (PreparedStatement stmt = conn.prepareStatement(getUserQry)) {
                stmt.setInt(1, parkingCode);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        Time currentEstimatedEnd = rs.getTime("Estimated_end_time");
                        String userEmail = rs.getString("Email");
                        String userName = rs.getString("Name");
                        
                        LocalTime newEstimatedEnd = currentEstimatedEnd.toLocalTime().plusHours(additionalHours);
                        
                        String updateQry = "UPDATE ParkingInfo SET Estimated_end_time = ?, IsExtended = true WHERE Code = ?";
                        
                        try (PreparedStatement updateStmt = conn.prepareStatement(updateQry)) {
                            updateStmt.setTime(1, Time.valueOf(newEstimatedEnd));
                            updateStmt.setInt(2, parkingCode);
                            updateStmt.executeUpdate();
                            
                            // 🆕 SEND EMAIL NOTIFICATION
                            if (userEmail != null && userName != null) {
                                EmailService.sendExtensionConfirmation(
                                    userEmail, userName, parkingCodeStr, 
                                    additionalHours, newEstimatedEnd.toString()
                                );
                            }
                            
                            return "Parking time extended by " + additionalHours + " hours until " + newEstimatedEnd;
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
     * Sends lost parking code to user - 🔧 FIXED COMPILATION ERRORS
     */
    public String sendLostParkingCode(String userName) {
        String qry = "SELECT pi.Code, u.Email, u.Phone, u.Name FROM ParkingInfo pi JOIN users u ON pi.User_ID = u.User_ID WHERE u.UserName = ? AND pi.Actual_end_time IS NULL";
        
        try (PreparedStatement stmt = conn.prepareStatement(qry)) {
            stmt.setString(1, userName);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    int parkingCode = rs.getInt("Code");
                    String email = rs.getString("Email");
                    String phone = rs.getString("Phone");
                    String name = rs.getString("Name"); // 🔧 FIXED: Now getting name from query
                    
                    // 🆕 SEND EMAIL NOTIFICATION
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
     * Gets parking history for a user
     */
    public ArrayList<ParkingOrder> getParkingHistory(String userName) {
        ArrayList<ParkingOrder> history = new ArrayList<>();
        String qry = "SELECT pi.*, ps.ParkingSpot_ID FROM ParkingInfo pi JOIN users u ON pi.User_ID = u.User_ID JOIN ParkingSpot ps ON pi.ParkingSpot_ID = ps.ParkingSpot_ID WHERE u.UserName = ? ORDER BY pi.Date DESC, pi.Actual_start_time DESC";
        
        try (PreparedStatement stmt = conn.prepareStatement(qry)) {
            stmt.setString(1, userName);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    ParkingOrder order = new ParkingOrder();
                    order.setOrderID(rs.getInt("ParkingInfo_ID"));
                    order.setParkingCode(String.valueOf(rs.getInt("Code")));
                    order.setOrderType(rs.getString("IsOrderedEnum"));
                    order.setSpotNumber("Spot " + rs.getInt("ParkingSpot_ID"));
                    
                    // Convert SQL Date and Time to LocalDateTime
                    Date date = rs.getDate("Date");
                    Time startTime = rs.getTime("Actual_start_time");
                    Time endTime = rs.getTime("Actual_end_time");
                    Time estimatedEnd = rs.getTime("Estimated_end_time");
                    
                    if (date != null && startTime != null) {
                        order.setEntryTime(LocalDateTime.of(date.toLocalDate(), startTime.toLocalTime()));
                    }
                    if (date != null && endTime != null) {
                        order.setExitTime(LocalDateTime.of(date.toLocalDate(), endTime.toLocalTime()));
                    }
                    if (date != null && estimatedEnd != null) {
                        order.setExpectedExitTime(LocalDateTime.of(date.toLocalDate(), estimatedEnd.toLocalTime()));
                    }
                    
                    order.setLate(rs.getBoolean("IsLate"));
                    order.setExtended(rs.getBoolean("IsExtended"));
                    order.setStatus(endTime != null ? "Completed" : "Active");
                    
                    history.add(order);
                }
            }
        } catch (SQLException e) {
            System.out.println("Error getting parking history: " + e.getMessage());
        }
        return history;
    }

    /**
     * Gets all active parking sessions (for attendant view)
     */
    public ArrayList<ParkingOrder> getActiveParkings() {
        ArrayList<ParkingOrder> activeParkings = new ArrayList<>();
        String qry = "SELECT pi.*, u.Name, ps.ParkingSpot_ID FROM ParkingInfo pi JOIN users u ON pi.User_ID = u.User_ID JOIN ParkingSpot ps ON pi.ParkingSpot_ID = ps.ParkingSpot_ID WHERE pi.Actual_end_time IS NULL ORDER BY pi.Actual_start_time";
        
        try (PreparedStatement stmt = conn.prepareStatement(qry)) {
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    ParkingOrder order = new ParkingOrder();
                    order.setOrderID(rs.getInt("ParkingInfo_ID"));
                    order.setParkingCode(String.valueOf(rs.getInt("Code")));
                    order.setOrderType(rs.getString("IsOrderedEnum"));
                    order.setSubscriberName(rs.getString("Name"));
                    order.setSpotNumber("Spot " + rs.getInt("ParkingSpot_ID"));
                    
                    // Convert SQL Date and Time to LocalDateTime
                    Date date = rs.getDate("Date");
                    Time startTime = rs.getTime("Actual_start_time");
                    Time estimatedEnd = rs.getTime("Estimated_end_time");
                    
                    if (date != null && startTime != null) {
                        order.setEntryTime(LocalDateTime.of(date.toLocalDate(), startTime.toLocalTime()));
                    }
                    if (date != null && estimatedEnd != null) {
                        order.setExpectedExitTime(LocalDateTime.of(date.toLocalDate(), estimatedEnd.toLocalTime()));
                    }
                    
                    order.setStatus("Active");
                    activeParkings.add(order);
                }
            }
        } catch (SQLException e) {
            System.out.println("Error getting active parkings: " + e.getMessage());
        }
        return activeParkings;
    }

    /**
     * Updates subscriber information
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
     * Cancels a reservation - 🔧 FIXED COMPILATION ERRORS
     */
    public String cancelReservation(int reservationCode) {
        // 🔧 FIXED: Get user info before cancelling for email notification
        String getUserQry = "SELECT u.Email, u.Name FROM Reservations r JOIN users u ON r.User_ID = u.User_ID WHERE r.Reservation_code = ?";
        String userEmail = null;
        String userName = null;
        
        try (PreparedStatement stmt = conn.prepareStatement(getUserQry)) {
            stmt.setInt(1, reservationCode);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    userEmail = rs.getString("Email");
                    userName = rs.getString("Name");
                }
            }
        } catch (SQLException e) {
            System.out.println("Error getting user info for cancellation: " + e.getMessage());
        }
        
        String qry = "UPDATE Reservations SET statusEnum = 'cancelled' WHERE Reservation_code = ? AND statusEnum IN ('preorder', 'active')";
        
        try (PreparedStatement stmt = conn.prepareStatement(qry)) {
            stmt.setInt(1, reservationCode);
            int rowsUpdated = stmt.executeUpdate();
            
            if (rowsUpdated > 0) {
                // Also free up the spot if it was assigned
                freeSpotForReservation(reservationCode);
                
                // 🆕 SEND EMAIL NOTIFICATION
                if (userEmail != null && userName != null) {
                    EmailService.sendReservationCancelled(userEmail, userName, String.valueOf(reservationCode));
                }
                
                return "Reservation cancelled successfully";
            }
        } catch (SQLException e) {
            System.out.println("Error cancelling reservation: " + e.getMessage());
        }
        return "Reservation not found or already cancelled/finished";
    }

    /**
     * Logs out a user (for future use if needed)
     */
    public void logoutUser(String userName) {
        System.out.println("User logged out: " + userName);
    }

    /**
     * Initializes parking spots if they don't exist
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
                        System.out.println("Successfully initialized " + TOTAL_PARKING_SPOTS + " parking spots with AUTO_INCREMENT");
                    } else {
                        System.out.println("Parking spots already exist: " + rs.getInt(1) + " spots found");
                    }
                }
            }
        } catch (SQLException e) {
            System.out.println("Error initializing parking spots: " + e.getMessage());
        }
    }

    // ========== HELPER METHODS ==========
    
    private int generateParkingCode() {
        Random random = new Random();
        return 100000 + random.nextInt(900000);
    }

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

    private boolean isParkingSpotAvailable(int spotID) {
        String qry = "SELECT isOccupied FROM ParkingSpot WHERE ParkingSpot_ID = ?";
        
        try (PreparedStatement stmt = conn.prepareStatement(qry)) {
            stmt.setInt(1, spotID);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return !rs.getBoolean("isOccupied");
                }
            }
        } catch (SQLException e) {
            System.out.println("Error checking spot availability: " + e.getMessage());
        }
        return false;
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

    private void updateReservationStatus(int reservationCode, String status) {
        String qry = "UPDATE Reservations SET statusEnum = ? WHERE Reservation_code = ?";
        
        try (PreparedStatement stmt = conn.prepareStatement(qry)) {
            stmt.setString(1, status);
            stmt.setInt(2, reservationCode);
            stmt.executeUpdate();
        } catch (SQLException e) {
            System.out.println("Error updating reservation status: " + e.getMessage());
        }
    }

    /**
     * Send late exit notification - 🔧 FIXED: Now uses EmailService
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
                    
                    // 🆕 SEND EMAIL NOTIFICATION
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
    
    private void finishReservationBySpotAndUser(int spotID, int userID) {
        String query = """
            UPDATE Reservations 
            SET statusEnum = 'finished'
            WHERE User_ID = ? AND assigned_parking_spot_id = ? AND statusEnum = 'active'
            """;
        
        try (PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setInt(1, userID);
            stmt.setInt(2, spotID);
            int updated = stmt.executeUpdate();
            
            if (updated > 0) {
                System.out.println("Reservation finished for user " + userID + " at spot " + spotID);
            }
        } catch (SQLException e) {
            System.out.println("Error finishing reservation: " + e.getMessage());
        }
    }
    
    private void freeSpotForReservation(int reservationCode) {
        String query = """
            UPDATE ParkingSpot ps
            SET ps.isOccupied = FALSE
            WHERE ps.ParkingSpot_ID = (
                SELECT r.assigned_parking_spot_id 
                FROM Reservations r 
                WHERE r.Reservation_code = ?
            )
            """;
        
        try (PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setInt(1, reservationCode);
            stmt.executeUpdate();
        } catch (SQLException e) {
            System.out.println("Error freeing spot for reservation: " + e.getMessage());
        }
    }
    /**
     * Activate reservation when customer arrives (PREORDER → ACTIVE)
     */
    public String activateReservation(String subscriberUserName, int reservationCode) {
        // Check if reservation exists and is in preorder status
        String checkQry = """
            SELECT r.*, u.UserName, r.assigned_parking_spot_id,
                   TIMESTAMPDIFF(MINUTE, 
                       CONCAT(r.reservation_Date, ' ', r.reservation_start_time), 
                       NOW()) as minutes_since_start
            FROM Reservations r 
            JOIN users u ON r.User_ID = u.User_ID 
            WHERE r.Reservation_code = ? AND r.statusEnum = 'preorder'
            """;
        
        try (PreparedStatement stmt = conn.prepareStatement(checkQry)) {
            stmt.setInt(1, reservationCode);
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    int minutesSinceStart = rs.getInt("minutes_since_start");
                    int spotId = rs.getInt("assigned_parking_spot_id");
                    
                    // Check if within 15-minute grace period
                    if (minutesSinceStart > 15) {
                        // Too late - auto-cancel
                        cancelReservation(subscriberUserName, reservationCode);
                        return "Reservation cancelled due to late arrival (over 15 minutes). Please make a new reservation.";
                    }
                    
                    // Generate parking code and create parking session
                    int parkingCode = generateParkingCode();
                    LocalDateTime now = LocalDateTime.now();
                    LocalDateTime estimatedEnd = now.plusHours(4); // Default 4 hours
                    
                    // Create parking info record
                    String insertQry = """
                        INSERT INTO ParkingInfo 
                        (ParkingSpot_ID, User_ID, Date, Code, Actual_start_time, Estimated_start_time, 
                         Estimated_end_time, IsOrderedEnum, IsLate, IsExtended) 
                        VALUES (?, ?, ?, ?, ?, ?, ?, 'ordered', ?, false)
                        """;
                    
                    try (PreparedStatement insertStmt = conn.prepareStatement(insertQry)) {
                        insertStmt.setInt(1, spotId);
                        insertStmt.setInt(2, rs.getInt("User_ID"));
                        insertStmt.setDate(3, Date.valueOf(now.toLocalDate()));
                        insertStmt.setInt(4, parkingCode);
                        insertStmt.setTime(5, Time.valueOf(now.toLocalTime()));
                        insertStmt.setTime(6, Time.valueOf(now.toLocalTime()));
                        insertStmt.setTime(7, Time.valueOf(estimatedEnd.toLocalTime()));
                        insertStmt.setBoolean(8, minutesSinceStart > 0); // Mark as late if any delay
                        insertStmt.executeUpdate();
                        
                        // Update reservation status to ACTIVE
                        updateReservationStatus(reservationCode, "active");
                        
                        // Mark parking spot as occupied
                        updateParkingSpotStatus(spotId, true);
                        
                        String lateMessage = minutesSinceStart > 0 ? 
                            " (Note: " + minutesSinceStart + " minutes late)" : "";
                        
                        System.out.println("Reservation " + reservationCode + " activated (preorder → active)" + lateMessage);
                        
                        return "Reservation activated! Parking code: " + parkingCode + 
                               ". Spot: " + spotId + lateMessage;
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
     * Cancel reservation
     */
    public String cancelReservation(String subscriberUserName, int reservationCode) {
        return cancelReservationInternal(reservationCode, "User requested cancellation");
    }

    /**
     * Internal cancellation method (used by auto-cancel and manual cancel)
     */
    private String cancelReservationInternal(int reservationCode, String reason) {
        // Get reservation info first for email notification
        String getUserQry = """
            SELECT u.Email, u.Name, r.statusEnum, r.assigned_parking_spot_id
            FROM Reservations r 
            JOIN users u ON r.User_ID = u.User_ID 
            WHERE r.Reservation_code = ?
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
                    spotId = rs.getObject("assigned_parking_spot_id", Integer.class);
                }
            }
        } catch (SQLException e) {
            System.out.println("Error getting reservation info for cancellation: " + e.getMessage());
        }
        
        // Update reservation status to cancelled
        String qry = "UPDATE Reservations SET statusEnum = 'cancelled' WHERE Reservation_code = ? AND statusEnum IN ('preorder', 'active')";
        
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
                
                System.out.println("Reservation " + reservationCode + " cancelled (" + currentStatus + " → cancelled) - " + reason);
                return "Reservation cancelled successfully";
            }
        } catch (SQLException e) {
            System.out.println("Error cancelling reservation: " + e.getMessage());
        }
        
        return "Reservation not found or already cancelled/finished";
    }

    /**
     * Update reservation status helper method
     */
    
}