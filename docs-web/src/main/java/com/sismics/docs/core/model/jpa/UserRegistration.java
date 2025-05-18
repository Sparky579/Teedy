package com.sismics.docs.core.model.jpa;

import java.util.Date;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * User registration request entity.
 * 
 * @author Claude
 */
@Entity
@Table(name = "T_USER_REGISTRATION")
public class UserRegistration implements Loggable {
    /**
     * Registration request ID.
     */
    @Id
    @Column(name = "REG_ID_C", length = 36)
    private String id;
    
    /**
     * Username.
     */
    @Column(name = "REG_USERNAME_C", nullable = false, length = 50)
    private String username;
    
    /**
     * Password (hashed).
     */
    @Column(name = "REG_PASSWORD_C", nullable = false, length = 200)
    private String password;
    
    /**
     * Email address.
     */
    @Column(name = "REG_EMAIL_C", nullable = false, length = 100)
    private String email;
    
    /**
     * Creation date.
     */
    @Column(name = "REG_CREATEDATE_D", nullable = false)
    private Date createDate;
    
    /**
     * Registration status.
     * PENDING, APPROVED, REJECTED
     */
    @Column(name = "REG_STATUS_C", nullable = false, length = 20)
    private String status;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public Date getCreateDate() {
        return createDate;
    }

    public void setCreateDate(Date createDate) {
        this.createDate = createDate;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
    
    @Override
    public String toMessage() {
        return username;
    }

    @Override
    public Date getDeleteDate() {
        return null; // Registration requests are not soft-deletable
    }
} 