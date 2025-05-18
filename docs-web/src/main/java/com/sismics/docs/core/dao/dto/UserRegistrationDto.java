package com.sismics.docs.core.dao.dto;

import java.util.Date;

/**
 * User registration DTO.
 * 
 * @author Claude
 */
public class UserRegistrationDto {
    /**
     * Registration ID.
     */
    private String id;
    
    /**
     * Username.
     */
    private String username;
    
    /**
     * Email.
     */
    private String email;
    
    /**
     * Creation date.
     */
    private Date createDate;

    /**
     * @return the id
     */
    public String getId() {
        return id;
    }

    /**
     * @param id the id to set
     */
    public void setId(String id) {
        this.id = id;
    }

    /**
     * @return the username
     */
    public String getUsername() {
        return username;
    }

    /**
     * @param username the username to set
     */
    public void setUsername(String username) {
        this.username = username;
    }

    /**
     * @return the email
     */
    public String getEmail() {
        return email;
    }

    /**
     * @param email the email to set
     */
    public void setEmail(String email) {
        this.email = email;
    }

    /**
     * @return the createDate
     */
    public Date getCreateDate() {
        return createDate;
    }

    /**
     * @param createDate the createDate to set
     */
    public void setCreateDate(Date createDate) {
        this.createDate = createDate;
    }
} 