package controllers;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;

import entities.ParkingReport;

/**
 * ReportController handles report generation for the ParkB parking management system.
 * Generates parking time reports and subscriber status reports as specified in the requirements.
 */
public class ReportController {
    protected Connection conn;
    public int successFlag;

    public ReportController(String dbname, String pass) {
        String connectPath = "jdbc:mysql://localhost/" + dbname + "?serverTimezone=IST";
        connectToDB(connectPath, pass);
    }

    public Connection getConnection() {
        return conn;
    }

    /**
     * Establishes connection to the MySQL database
     */
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
     * Gets parking reports based on report type
     * @param reportType The type of report to generate ("PARKING_TIME" or "SUBSCRIBER_STATUS")
     * @return ArrayList of ParkingReport objects
     */
    public ArrayList<ParkingReport> getParkingReports(String reportType) {
        ArrayList<ParkingReport> reports = new ArrayList<>();
        
        switch (reportType.toUpperCase()) {
            case "PARKING_TIME":
                reports.add(generateParkingTimeReport());
                break;
            case "SUBSCRIBER_STATUS":
                reports.add(generateSubscriberStatusReport());
                break;
            case "ALL":
                reports.add(generateParkingTimeReport());
                reports.add(generateSubscriberStatusReport());
                break;
            default:
                System.out.println("Unknown report type: " + reportType);
                break;
        }
        
        return reports;
    }

    /**
     * Generates monthly reports automatically at the end of each month
     * @param monthYear Format: "YYYY-MM"
     * @return ArrayList of monthly reports
     */
    public ArrayList<ParkingReport> generateMonthlyReports(String monthYear) {
        ArrayList<ParkingReport> monthlyReports = new ArrayList<>();
        
        try {
            // Parse the month-year string
            String[] parts = monthYear.split("-");
            int year = Integer.parseInt(parts[0]);
            int month = Integer.parseInt(parts[1]);
            
            LocalDate reportDate = LocalDate.of(year, month, 1);
            
            // Generate parking time report for the specific month
            ParkingReport parkingTimeReport = generateMonthlyParkingTimeReport(reportDate);
            if (parkingTimeReport != null) {
                monthlyReports.add(parkingTimeReport);
            }
            
            // Generate subscriber status report for the specific month
            ParkingReport subscriberReport = generateMonthlySubscriberStatusReport(reportDate);
            if (subscriberReport != null) {
                monthlyReports.add(subscriberReport);
            }
            
            // Store reports in database
            storeMonthlyReports(monthlyReports);
            
        } catch (Exception e) {
            System.out.println("Error generating monthly reports: " + e.getMessage());
        }
        
        return monthlyReports;
    }

    /**
     * Generates a parking time report showing usage patterns, delays, and extensions
     */
    private ParkingReport generateParkingTimeReport() {
        ParkingReport report = new ParkingReport("PARKING_TIME", LocalDate.now());
        
        String qry = """
            SELECT 
                COUNT(*) as total_parkings,
                AVG(TIMESTAMPDIFF(MINUTE, Actual_start_time, COALESCE(Actual_end_time, NOW()))) as avg_duration,
                SUM(IsLate) as late_exits,
                SUM(IsExtended) as extensions,
                MIN(TIMESTAMPDIFF(MINUTE, Actual_start_time, COALESCE(Actual_end_time, NOW()))) as min_duration,
                MAX(TIMESTAMPDIFF(MINUTE, Actual_start_time, COALESCE(Actual_end_time, NOW()))) as max_duration
            FROM ParkingInfo 
            WHERE Date >= DATE_SUB(CURDATE(), INTERVAL 30 DAY)
            """;
        
        try (PreparedStatement stmt = conn.prepareStatement(qry)) {
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    report.setTotalParkings(rs.getInt("total_parkings"));
                    report.setAverageParkingTime(rs.getDouble("avg_duration"));
                    report.setLateExits(rs.getInt("late_exits"));
                    report.setExtensions(rs.getInt("extensions"));
                    report.setMinParkingTime(rs.getInt("min_duration"));
                    report.setMaxParkingTime(rs.getInt("max_duration"));
                }
            }
        } catch (SQLException e) {
            System.out.println("Error generating parking time report: " + e.getMessage());
        }
        
        return report;
    }

    /**
     * Generates a subscriber status report showing subscriber activity and usage patterns
     */
    private ParkingReport generateSubscriberStatusReport() {
        ParkingReport report = new ParkingReport("SUBSCRIBER_STATUS", LocalDate.now());
        
        // Get active subscribers count
        String activeSubQry = "SELECT COUNT(DISTINCT User_ID) as active_subscribers FROM ParkingInfo WHERE Date >= DATE_SUB(CURDATE(), INTERVAL 30 DAY)";
        
        // Get total orders, reservations, and immediate entries
        String ordersQry = """
            SELECT 
                COUNT(*) as total_orders,
                SUM(CASE WHEN IsOrderedEnum = 'ordered' THEN 1 ELSE 0 END) as reservations,
                SUM(CASE WHEN IsOrderedEnum = 'not ordered' THEN 1 ELSE 0 END) as immediate_entries,
                AVG(TIMESTAMPDIFF(MINUTE, Actual_start_time, COALESCE(Actual_end_time, NOW()))) as avg_session_duration
            FROM ParkingInfo 
            WHERE Date >= DATE_SUB(CURDATE(), INTERVAL 30 DAY)
            """;
        
        // Get cancelled reservations
        String cancelledQry = "SELECT COUNT(*) as cancelled_reservations FROM Reservations WHERE statusEnum = 'cancelled' AND Date_Of_Placing_Order >= DATE_SUB(CURDATE(), INTERVAL 30 DAY)";
        
        try {
            // Get active subscribers
            try (PreparedStatement stmt = conn.prepareStatement(activeSubQry)) {
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        report.setActiveSubscribers(rs.getInt("active_subscribers"));
                    }
                }
            }
            
            // Get order statistics
            try (PreparedStatement stmt = conn.prepareStatement(ordersQry)) {
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        report.setTotalOrders(rs.getInt("total_orders"));
                        report.setReservations(rs.getInt("reservations"));
                        report.setImmediateEntries(rs.getInt("immediate_entries"));
                        report.setAverageSessionDuration(rs.getDouble("avg_session_duration"));
                    }
                }
            }
            
            // Get cancelled reservations
            try (PreparedStatement stmt = conn.prepareStatement(cancelledQry)) {
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        report.setCancelledReservations(rs.getInt("cancelled_reservations"));
                    }
                }
            }
            
        } catch (SQLException e) {
            System.out.println("Error generating subscriber status report: " + e.getMessage());
        }
        
        return report;
    }

    /**
     * Generates a monthly parking time report for a specific month
     */
    private ParkingReport generateMonthlyParkingTimeReport(LocalDate reportDate) {
        ParkingReport report = new ParkingReport("PARKING_TIME", reportDate);
        
        String qry = """
            SELECT 
                COUNT(*) as total_parkings,
                AVG(TIMESTAMPDIFF(MINUTE, Actual_start_time, COALESCE(Actual_end_time, Estimated_end_time))) as avg_duration,
                SUM(IsLate) as late_exits,
                SUM(IsExtended) as extensions,
                MIN(TIMESTAMPDIFF(MINUTE, Actual_start_time, COALESCE(Actual_end_time, Estimated_end_time))) as min_duration,
                MAX(TIMESTAMPDIFF(MINUTE, Actual_start_time, COALESCE(Actual_end_time, Estimated_end_time))) as max_duration
            FROM ParkingInfo 
            WHERE YEAR(Date) = ? AND MONTH(Date) = ?
            """;
        
        try (PreparedStatement stmt = conn.prepareStatement(qry)) {
            stmt.setInt(1, reportDate.getYear());
            stmt.setInt(2, reportDate.getMonthValue());
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    report.setTotalParkings(rs.getInt("total_parkings"));
                    report.setAverageParkingTime(rs.getDouble("avg_duration"));
                    report.setLateExits(rs.getInt("late_exits"));
                    report.setExtensions(rs.getInt("extensions"));
                    report.setMinParkingTime(rs.getInt("min_duration"));
                    report.setMaxParkingTime(rs.getInt("max_duration"));
                    
                    return report;
                }
            }
        } catch (SQLException e) {
            System.out.println("Error generating monthly parking time report: " + e.getMessage());
        }
        
        return null;
    }

    /**
     * Generates a monthly subscriber status report for a specific month
     */
    private ParkingReport generateMonthlySubscriberStatusReport(LocalDate reportDate) {
        ParkingReport report = new ParkingReport("SUBSCRIBER_STATUS", reportDate);
        
        // Get active subscribers for the month
        String activeSubQry = "SELECT COUNT(DISTINCT User_ID) as active_subscribers FROM ParkingInfo WHERE YEAR(Date) = ? AND MONTH(Date) = ?";
        
        // Get monthly order statistics
        String ordersQry = """
            SELECT 
                COUNT(*) as total_orders,
                SUM(CASE WHEN IsOrderedEnum = 'ordered' THEN 1 ELSE 0 END) as reservations,
                SUM(CASE WHEN IsOrderedEnum = 'not ordered' THEN 1 ELSE 0 END) as immediate_entries,
                AVG(TIMESTAMPDIFF(MINUTE, Actual_start_time, COALESCE(Actual_end_time, Estimated_end_time))) as avg_session_duration
            FROM ParkingInfo 
            WHERE YEAR(Date) = ? AND MONTH(Date) = ?
            """;
        
        // Get cancelled reservations for the month
        String cancelledQry = "SELECT COUNT(*) as cancelled_reservations FROM Reservations WHERE statusEnum = 'cancelled' AND YEAR(Date_Of_Placing_Order) = ? AND MONTH(Date_Of_Placing_Order) = ?";
        
        try {
            // Get active subscribers
            try (PreparedStatement stmt = conn.prepareStatement(activeSubQry)) {
                stmt.setInt(1, reportDate.getYear());
                stmt.setInt(2, reportDate.getMonthValue());
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        report.setActiveSubscribers(rs.getInt("active_subscribers"));
                    }
                }
            }
            
            // Get order statistics
            try (PreparedStatement stmt = conn.prepareStatement(ordersQry)) {
                stmt.setInt(1, reportDate.getYear());
                stmt.setInt(2, reportDate.getMonthValue());
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        report.setTotalOrders(rs.getInt("total_orders"));
                        report.setReservations(rs.getInt("reservations"));
                        report.setImmediateEntries(rs.getInt("immediate_entries"));
                        report.setAverageSessionDuration(rs.getDouble("avg_session_duration"));
                    }
                }
            }
            
            // Get cancelled reservations
            try (PreparedStatement stmt = conn.prepareStatement(cancelledQry)) {
                stmt.setInt(1, reportDate.getYear());
                stmt.setInt(2, reportDate.getMonthValue());
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        report.setCancelledReservations(rs.getInt("cancelled_reservations"));
                    }
                }
            }
            
            return report;
            
        } catch (SQLException e) {
            System.out.println("Error generating monthly subscriber status report: " + e.getMessage());
        }
        
        return null;
    }

    /**
     * Stores monthly reports in the database
     */
    private void storeMonthlyReports(ArrayList<ParkingReport> reports) {
        String qry = "INSERT INTO Reports (Report_Type, Generated_Date, Report_Data) VALUES (?, NOW(), ?)";
        
        try (PreparedStatement stmt = conn.prepareStatement(qry)) {
            for (ParkingReport report : reports) {
                stmt.setString(1, report.getReportType());
                stmt.setString(2, report.toString()); // Store as JSON or formatted string
                stmt.executeUpdate();
            }
            System.out.println("Monthly reports stored successfully");
        } catch (SQLException e) {
            System.out.println("Error storing monthly reports: " + e.getMessage());
        }
    }

    /**
     * Gets historical reports from the database
     */
    public ArrayList<ParkingReport> getHistoricalReports(String reportType, LocalDate fromDate, LocalDate toDate) {
        ArrayList<ParkingReport> reports = new ArrayList<>();
        
        String qry = "SELECT * FROM Reports WHERE Report_Type = ? AND DATE(Generated_Date) BETWEEN ? AND ? ORDER BY Generated_Date DESC";
        
        try (PreparedStatement stmt = conn.prepareStatement(qry)) {
            stmt.setString(1, reportType);
            stmt.setString(2, fromDate.toString());
            stmt.setString(3, toDate.toString());
            
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    // This would need to be enhanced to parse the stored report data
                    // For now, we'll create a basic report object
                    ParkingReport report = new ParkingReport();
                    report.setReportType(rs.getString("Report_Type"));
                    report.setReportDate(rs.getDate("Generated_Date").toLocalDate());
                    reports.add(report);
                }
            }
        } catch (SQLException e) {
            System.out.println("Error getting historical reports: " + e.getMessage());
        }
        
        return reports;
    }

    /**
     * Gets peak usage hours for analysis
     */
    public ArrayList<String> getPeakUsageHours() {
        ArrayList<String> peakHours = new ArrayList<>();
        
        String qry = """
            SELECT 
                HOUR(Actual_start_time) as entry_hour,
                COUNT(*) as entry_count
            FROM ParkingInfo 
            WHERE Date >= DATE_SUB(CURDATE(), INTERVAL 30 DAY)
            GROUP BY HOUR(Actual_start_time)
            ORDER BY entry_count DESC
            LIMIT 5
            """;
        
        try (PreparedStatement stmt = conn.prepareStatement(qry)) {
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    int hour = rs.getInt("entry_hour");
                    int count = rs.getInt("entry_count");
                    peakHours.add(String.format("%02d:00 - %d entries", hour, count));
                }
            }
        } catch (SQLException e) {
            System.out.println("Error getting peak usage hours: " + e.getMessage());
        }
        
        return peakHours;
    }

    /**
     * Gets daily parking statistics for the current month
     */
    public ArrayList<String> getDailyStatistics() {
        ArrayList<String> dailyStats = new ArrayList<>();
        
        String qry = """
            SELECT 
                Date,
                COUNT(*) as daily_entries,
                SUM(IsLate) as daily_late_exits,
                AVG(TIMESTAMPDIFF(MINUTE, Actual_start_time, COALESCE(Actual_end_time, NOW()))) as avg_daily_duration
            FROM ParkingInfo 
            WHERE YEAR(Date) = YEAR(CURDATE()) AND MONTH(Date) = MONTH(CURDATE())
            GROUP BY Date
            ORDER BY Date DESC
            """;
        
        try (PreparedStatement stmt = conn.prepareStatement(qry)) {
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String date = rs.getDate("Date").toString();
                    int entries = rs.getInt("daily_entries");
                    int lateExits = rs.getInt("daily_late_exits");
                    double avgDuration = rs.getDouble("avg_daily_duration");
                    
                    dailyStats.add(String.format("%s: %d entries, %d late exits, %.1f min avg duration", 
                                                date, entries, lateExits, avgDuration));
                }
            }
        } catch (SQLException e) {
            System.out.println("Error getting daily statistics: " + e.getMessage());
        }
        
        return dailyStats;
    }
}