package entities;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

/**
 * Represents a parking order/session in the ParkB system.
 * Contains information about entry, exit, and parking details.
 */
public class ParkingOrder implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private int orderID;
    private String parkingCode;
    private String subscriberName;
    private String orderType; // "ordered" (reservation) or "not ordered" (immediate)
    private LocalDateTime entryTime;
    private LocalDateTime exitTime;
    private LocalDateTime expectedExitTime;
    private boolean isLate;
    private boolean isExtended;
    private String status; // "Active", "Completed"
    private String spotNumber;
    
    // Constructors
    public ParkingOrder() {}
    
    public ParkingOrder(int orderID, String parkingCode, String subscriberName, 
                       String orderType, LocalDateTime entryTime, LocalDateTime expectedExitTime) {
        this.orderID = orderID;
        this.parkingCode = parkingCode;
        this.subscriberName = subscriberName;
        this.orderType = orderType;
        this.entryTime = entryTime;
        this.expectedExitTime = expectedExitTime;
        this.isLate = false;
        this.isExtended = false;
        this.status = "Active";
    }
    
    // Getters and Setters
    public int getOrderID() {
        return orderID;
    }
    
    public void setOrderID(int orderID) {
        this.orderID = orderID;
    }
    
    public String getParkingCode() {
        return parkingCode;
    }
    
    public void setParkingCode(String parkingCode) {
        this.parkingCode = parkingCode;
    }
    
    public String getSubscriberName() {
        return subscriberName;
    }
    
    public void setSubscriberName(String subscriberName) {
        this.subscriberName = subscriberName;
    }
    
    public String getOrderType() {
        return orderType;
    }
    
    public void setOrderType(String orderType) {
        this.orderType = orderType;
    }
    
    public LocalDateTime getEntryTime() {
        return entryTime;
    }
    
    public void setEntryTime(LocalDateTime entryTime) {
        this.entryTime = entryTime;
    }
    
    public LocalDateTime getExitTime() {
        return exitTime;
    }
    
    public void setExitTime(LocalDateTime exitTime) {
        this.exitTime = exitTime;
    }
    
    public LocalDateTime getExpectedExitTime() {
        return expectedExitTime;
    }
    
    public void setExpectedExitTime(LocalDateTime expectedExitTime) {
        this.expectedExitTime = expectedExitTime;
    }
    
    public boolean isLate() {
        return isLate;
    }
    
    public void setLate(boolean late) {
        isLate = late;
    }
    
    public boolean isExtended() {
        return isExtended;
    }
    
    public void setExtended(boolean extended) {
        isExtended = extended;
    }
    
    public String getStatus() {
        return status;
    }
    
    public void setStatus(String status) {
        this.status = status;
    }
    
    public String getSpotNumber() {
        return spotNumber;
    }
    
    public void setSpotNumber(String spotNumber) {
        this.spotNumber = spotNumber;
    }
    
    // Utility methods
    public String getFormattedEntryTime() {
        if (entryTime != null) {
            return entryTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        }
        return "";
    }
    
    public String getFormattedExitTime() {
        if (exitTime != null) {
            return exitTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        }
        return "Still parked";
    }
    
    public String getFormattedExpectedExitTime() {
        if (expectedExitTime != null) {
            return expectedExitTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        }
        return "";
    }
    
    public long getParkingDurationMinutes() {
        if (entryTime != null) {
            LocalDateTime endTime = exitTime != null ? exitTime : LocalDateTime.now();
            return ChronoUnit.MINUTES.between(entryTime, endTime);
        }
        return 0;
    }
    
    public String getParkingDurationFormatted() {
        long minutes = getParkingDurationMinutes();
        long hours = minutes / 60;
        long remainingMinutes = minutes % 60;
        return String.format("%d hours, %d minutes", hours, remainingMinutes);
    }
    
    public boolean isCurrentlyParked() {
        return exitTime == null && "Active".equals(status);
    }
    
    public boolean isReservation() {
        return "ordered".equals(orderType);
    }
    
    @Override
    public String toString() {
        return "ParkingOrder{" +
                "orderID=" + orderID +
                ", parkingCode='" + parkingCode + '\'' +
                ", subscriberName='" + subscriberName + '\'' +
                ", orderType='" + orderType + '\'' +
                ", entryTime=" + entryTime +
                ", exitTime=" + exitTime +
                ", expectedExitTime=" + expectedExitTime +
                ", isLate=" + isLate +
                ", isExtended=" + isExtended +
                ", status='" + status + '\'' +
                ", spotNumber='" + spotNumber + '\'' +
                '}';
    }
}