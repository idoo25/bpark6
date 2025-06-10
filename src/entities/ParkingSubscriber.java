package entities;

import java.io.Serializable;
import java.util.ArrayList;

/**
 * Represents a parking subscriber in the ParkB system.
 * Contains subscriber information and parking history.
 */
public class ParkingSubscriber implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private int subscriberID;
    private String subscriberCode; // Unique subscriber code
    private String firstName;
    private String phoneNumber;
    private String email;
    private String carNumber;
    private String userType; // sub, emp, mng
    private ArrayList<ParkingOrder> parkingHistory;
    
    // Constructors
    public ParkingSubscriber() {
        this.parkingHistory = new ArrayList<>();
    }
    
    public ParkingSubscriber(int subscriberID, String subscriberCode, String firstName, 
                           String phoneNumber, String email, String carNumber, String userType) {
        this.subscriberID = subscriberID;
        this.subscriberCode = subscriberCode;
        this.firstName = firstName;
        this.phoneNumber = phoneNumber;
        this.email = email;
        this.carNumber = carNumber;
        this.userType = userType;
        this.parkingHistory = new ArrayList<>();
    }
    
    // Getters and Setters
    public int getSubscriberID() {
        return subscriberID;
    }
    
    public void setSubscriberID(int subscriberID) {
        this.subscriberID = subscriberID;
    }
    
    public String getSubscriberCode() {
        return subscriberCode;
    }
    
    public void setSubscriberCode(String subscriberCode) {
        this.subscriberCode = subscriberCode;
    }
    
    public String getFirstName() {
        return firstName;
    }
    
    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }
    
    public String getPhoneNumber() {
        return phoneNumber;
    }
    
    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }
    
    public String getEmail() {
        return email;
    }
    
    public void setEmail(String email) {
        this.email = email;
    }
    
    public String getCarNumber() {
        return carNumber;
    }
    
    public void setCarNumber(String carNumber) {
        this.carNumber = carNumber;
    }
    
    public String getUserType() {
        return userType;
    }
    
    public void setUserType(String userType) {
        this.userType = userType;
    }
    
    public ArrayList<ParkingOrder> getParkingHistory() {
        return parkingHistory;
    }
    
    public void setParkingHistory(ArrayList<ParkingOrder> parkingHistory) {
        this.parkingHistory = parkingHistory;
    }
    
    public void addParkingOrder(ParkingOrder order) {
        this.parkingHistory.add(order);
    }
    
    @Override
    public String toString() {
        return "ParkingSubscriber{" +
                "subscriberID=" + subscriberID +
                ", subscriberCode='" + subscriberCode + '\'' +
                ", firstName='" + firstName + '\'' +
                ", phoneNumber='" + phoneNumber + '\'' +
                ", email='" + email + '\'' +
                ", carNumber='" + carNumber + '\'' +
                ", userType='" + userType + '\'' +
                '}';
    }
}