package entities;

import java.io.Serializable;

/**
 * This class represents a message between the server and the client for the ParkB system.
 * It contains the type of the message and its content.
 * 
 * @author ParkB Team
 * @version 1.0
 */
public class Message implements Serializable {
    private static final long serialVersionUID = 1L;

    // Class variables *************************************************
    /**
     * The message type for parking system operations
     */
    private MessageType type;

    /**
     * The content of the message, such as ParkingOrder objects, subscriber codes, etc.
     */
    private Serializable content;

    /**
     * The message type enumeration for parking system operations.
     */
    public enum MessageType {
    	/**
         * Register new subscriber request
         */
        REGISTER_SUBSCRIBER,
        /**
         * Registration response
         */
        REGISTRATION_RESPONSE,
        /**
         * Generate unique username request
         */
        GENERATE_USERNAME,
        /**
         * Generated username response
         */
        USERNAME_RESPONSE,
    	
    	
    	/**
         * Subscriber login request
         */
        SUBSCRIBER_LOGIN,
        /**
         * Subscriber login response
         */
        SUBSCRIBER_LOGIN_RESPONSE,
        /**
         * Check parking availability
         */
        CHECK_PARKING_AVAILABILITY,
        /**
         * Parking availability response
         */
        PARKING_AVAILABILITY_RESPONSE,
        /**
         * Reserve parking spot
         */
        RESERVE_PARKING,
        /**
         * Reservation response
         */
        RESERVATION_RESPONSE,
        /**
         * Enter parking request
         */
        ENTER_PARKING,
        /**
         * Enter parking response
         */
        ENTER_PARKING_RESPONSE,
        /**
         * Exit parking request
         */
        EXIT_PARKING,
        /**
         * Exit parking response
         */
        EXIT_PARKING_RESPONSE,
        /**
         * Extend parking time
         */
        EXTEND_PARKING,
        /**
         * Extend parking response
         */
        EXTEND_PARKING_RESPONSE,
        /**
         * Request lost parking code
         */
        REQUEST_LOST_CODE,
        /**
         * Lost code response
         */
        LOST_CODE_RESPONSE,
        /**
         * Get parking history
         */
        GET_PARKING_HISTORY,
        /**
         * Parking history response
         */
        PARKING_HISTORY_RESPONSE,
        /**
         * Manager login request
         */
        MANAGER_LOGIN,
        /**
         * Manager login response
         */
        MANAGER_LOGIN_RESPONSE,
        /**
         * Get active parkings (for attendant)
         */
        GET_ACTIVE_PARKINGS,
        /**
         * Active parkings response
         */
        ACTIVE_PARKINGS_RESPONSE,
        /**
         * Manager get reports request
         */
        MANAGER_GET_REPORTS,
        /**
         * Manager send reports response
         */
        MANAGER_SEND_REPORTS,
        /**
         * Update subscriber information
         */
        UPDATE_SUBSCRIBER_INFO,
        /**
         * Update subscriber response
         */
        UPDATE_SUBSCRIBER_RESPONSE,
        /**
         * Generate monthly reports
         */
        GENERATE_MONTHLY_REPORTS,
        /**
         * Monthly reports response
         */
        MONTHLY_REPORTS_RESPONSE,
        
        /**
         * Get available time slots for a date/time (15-minute precision)
         */
        GET_TIME_SLOTS,
        /**
         * Time slots response
         */
        TIME_SLOTS_RESPONSE,
        /**
         * Make pre-booking reservation (DATETIME format)
         */
        MAKE_PREBOOKING,
        /**
         * Pre-booking response
         */
        PREBOOKING_RESPONSE,
        /**
         * Spontaneous parking entry (immediate spot assignment)
         */
        SPONTANEOUS_PARKING,
        /**
         * Spontaneous parking response
         */
        SPONTANEOUS_RESPONSE,
        /**
         * Request parking extension (during last hour)
         */
        REQUEST_EXTENSION,
        /**
         * Extension response
         */
        EXTENSION_RESPONSE,
        /**
         * Get system status
         */
        GET_SYSTEM_STATUS,
        /**
         * System status response
         */
        SYSTEM_STATUS_RESPONSE,
        
        /**
         * Activate reservation when arriving
         */
        ACTIVATE_RESERVATION,
        /**
         * Activation response
         */
        ACTIVATION_RESPONSE,
        /**
         * Cancel reservation
         */
        CANCEL_RESERVATION,
        /**
         * Cancellation response  
         */
        CANCELLATION_RESPONSE
    }

    // Constructors ******************************************************
    /**
     * Constructs a new Message with the specified type and content.
     * 
     * @param type    the type of the message
     * @param content the content of the message
     */
    public Message(MessageType type, Serializable content) {
        this.setType(type);
        this.setContent(content);
    }

    // Methods ***********************************************************

    /**
     * Returns the type of the message.
     * 
     * @return the type of the message
     */
    public MessageType getType() {
        return type;
    }

    /**
     * Sets the type of the message.
     * 
     * @param type the new type of the message
     */
    public void setType(MessageType type) {
        this.type = type;
    }

    /**
     * Returns the content of the message.
     * 
     * @return the content of the message
     */
    public Serializable getContent() {
        return content;
    }

    /**
     * Sets the content of the message.
     * 
     * @param content the new content of the message
     */
    public void setContent(Serializable content) {
        this.content = content;
    }
}