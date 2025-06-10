package entities;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * Represents a parking report in the ParkB system.
 * Contains statistical data about parking usage, subscriber status, and system performance.
 */
public class ParkingReport implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private String reportType; // "PARKING_TIME", "SUBSCRIBER_STATUS"
    private LocalDate reportDate;
    
    // Parking Time Report fields
    private int totalParkings;
    private double averageParkingTime; // in minutes
    private int lateExits;
    private int extensions;
    private int minParkingTime;
    private int maxParkingTime;
    
    // Subscriber Status Report fields
    private int activeSubscribers;
    private int totalOrders;
    private int reservations;
    private int immediateEntries;
    private int cancelledReservations;
    private double averageSessionDuration;
    
    // Constructors
    public ParkingReport() {}

    public ParkingReport(String reportType, LocalDate reportDate) {
        this.reportType = reportType;
        this.reportDate = reportDate;
    }

    // Getters and Setters
    public String getReportType() {
        return reportType;
    }

    public void setReportType(String reportType) {
        this.reportType = reportType;
    }

    public LocalDate getReportDate() {
        return reportDate;
    }

    public void setReportDate(LocalDate reportDate) {
        this.reportDate = reportDate;
    }

    public int getTotalParkings() {
        return totalParkings;
    }

    public void setTotalParkings(int totalParkings) {
        this.totalParkings = totalParkings;
    }

    public double getAverageParkingTime() {
        return averageParkingTime;
    }

    public void setAverageParkingTime(double averageParkingTime) {
        this.averageParkingTime = averageParkingTime;
    }

    public int getLateExits() {
        return lateExits;
    }

    public void setLateExits(int lateExits) {
        this.lateExits = lateExits;
    }

    public int getExtensions() {
        return extensions;
    }

    public void setExtensions(int extensions) {
        this.extensions = extensions;
    }

    public int getMinParkingTime() {
        return minParkingTime;
    }

    public void setMinParkingTime(int minParkingTime) {
        this.minParkingTime = minParkingTime;
    }

    public int getMaxParkingTime() {
        return maxParkingTime;
    }

    public void setMaxParkingTime(int maxParkingTime) {
        this.maxParkingTime = maxParkingTime;
    }

    public int getActiveSubscribers() {
        return activeSubscribers;
    }

    public void setActiveSubscribers(int activeSubscribers) {
        this.activeSubscribers = activeSubscribers;
    }

    public int getTotalOrders() {
        return totalOrders;
    }

    public void setTotalOrders(int totalOrders) {
        this.totalOrders = totalOrders;
    }

    public int getReservations() {
        return reservations;
    }

    public void setReservations(int reservations) {
        this.reservations = reservations;
    }

    public int getImmediateEntries() {
        return immediateEntries;
    }

    public void setImmediateEntries(int immediateEntries) {
        this.immediateEntries = immediateEntries;
    }

    public int getCancelledReservations() {
        return cancelledReservations;
    }

    public void setCancelledReservations(int cancelledReservations) {
        this.cancelledReservations = cancelledReservations;
    }

    public double getAverageSessionDuration() {
        return averageSessionDuration;
    }

    public void setAverageSessionDuration(double averageSessionDuration) {
        this.averageSessionDuration = averageSessionDuration;
    }

    // Utility methods
    public String getFormattedReportDate() {
        if (reportDate != null) {
            return reportDate.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        }
        return "";
    }

    public String getFormattedAverageParkingTime() {
        long hours = (long) (averageParkingTime / 60);
        long minutes = (long) (averageParkingTime % 60);
        return String.format("%d hours, %d minutes", hours, minutes);
    }

    public double getLateExitPercentage() {
        if (totalParkings > 0) {
            return (double) lateExits / totalParkings * 100;
        }
        return 0.0;
    }

    public double getExtensionPercentage() {
        if (totalParkings > 0) {
            return (double) extensions / totalParkings * 100;
        }
        return 0.0;
    }

    public double getReservationPercentage() {
        if (totalOrders > 0) {
            return (double) reservations / totalOrders * 100;
        }
        return 0.0;
    }

    @Override
    public String toString() {
        return "ParkingReport{" +
                "reportType='" + reportType + '\'' +
                ", reportDate=" + reportDate +
                ", totalParkings=" + totalParkings +
                ", averageParkingTime=" + averageParkingTime +
                ", lateExits=" + lateExits +
                ", extensions=" + extensions +
                ", activeSubscribers=" + activeSubscribers +
                ", totalOrders=" + totalOrders +
                ", reservations=" + reservations +
                ", immediateEntries=" + immediateEntries +
                '}';
    }
}