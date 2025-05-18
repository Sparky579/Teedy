package com.sismics.docs.core.dao;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import com.sismics.docs.core.constant.AuditLogType;
import com.sismics.docs.core.dao.dto.UserRegistrationDto;
import com.sismics.docs.core.model.jpa.UserRegistration;
import com.sismics.docs.core.util.AuditLogUtil;
import com.sismics.util.context.ThreadLocalContext;

import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;
import jakarta.persistence.Query;

/**
 * User registration DAO.
 * 
 * @author Claude
 */
public class UserRegistrationDao {
    /**
     * Creates a new user registration request.
     * 
     * @param userRegistration User registration
     */
    public void create(UserRegistration userRegistration) {
        // Create the UUID
        userRegistration.setId(UUID.randomUUID().toString());
        
        // Set default values
        userRegistration.setCreateDate(new Date());
        userRegistration.setStatus("PENDING");
        
        // Create the user registration
        EntityManager em = ThreadLocalContext.get().getEntityManager();
        em.persist(userRegistration);
        
        // Create audit log
        AuditLogUtil.create(userRegistration, AuditLogType.CREATE, null);
    }
    
    /**
     * Gets a registration request by its ID.
     * 
     * @param id Registration ID
     * @return User registration
     */
    public UserRegistration getById(String id) {
        EntityManager em = ThreadLocalContext.get().getEntityManager();
        try {
            Query q = em.createQuery("select r from com.sismics.docs.core.model.jpa.UserRegistration r where r.id = :id");
            q.setParameter("id", id);
            return (UserRegistration) q.getSingleResult();
        } catch (NoResultException e) {
            return null;
        }
    }
    
    /**
     * Gets a pending registration request by username.
     * 
     * @param username Username
     * @return User registration
     */
    public UserRegistration getByUsername(String username) {
        EntityManager em = ThreadLocalContext.get().getEntityManager();
        try {
            Query q = em.createQuery("select r from com.sismics.docs.core.model.jpa.UserRegistration r where r.username = :username and r.status = 'PENDING'");
            q.setParameter("username", username);
            return (UserRegistration) q.getSingleResult();
        } catch (NoResultException e) {
            return null;
        }
    }
    
    /**
     * Updates a registration request status.
     * 
     * @param id Registration ID
     * @param status New status
     * @param userId User ID who updates the status
     */
    public void updateStatus(String id, String status, String userId) {
        EntityManager em = ThreadLocalContext.get().getEntityManager();
        
        // Get the registration request
        UserRegistration registration = getById(id);
        if (registration == null) {
            return;
        }
        
        // Update the status
        Query q = em.createQuery("update com.sismics.docs.core.model.jpa.UserRegistration r set r.status = :status where r.id = :id");
        q.setParameter("id", id);
        q.setParameter("status", status);
        q.executeUpdate();
        
        // Set the updated status for the audit log
        registration.setStatus(status);
        
        // Create audit log
        AuditLogUtil.create(registration, AuditLogType.UPDATE, userId);
    }
    
    /**
     * Returns all pending registration requests.
     * 
     * @return List of registration requests
     */
    public List<UserRegistrationDto> findAllPending() {
        EntityManager em = ThreadLocalContext.get().getEntityManager();
        Query q = em.createQuery("select r from com.sismics.docs.core.model.jpa.UserRegistration r order by r.createDate desc");
        
        @SuppressWarnings("unchecked")
        List<UserRegistration> registrationList = q.getResultList();
        
        List<UserRegistrationDto> registrationDtoList = new ArrayList<>();
        for (UserRegistration registration : registrationList) {
            UserRegistrationDto registrationDto = new UserRegistrationDto();
            registrationDto.setId(registration.getId());
            registrationDto.setUsername(registration.getUsername());
            registrationDto.setEmail(registration.getEmail());
            registrationDto.setCreateDate(registration.getCreateDate());
            registrationDtoList.add(registrationDto);
        }
        
        return registrationDtoList;
    }
    
    /**
     * Deletes a registration request.
     * 
     * @param id Registration ID
     * @param userId User ID who deletes
     */
    public void delete(String id, String userId) {
        // Get the registration request
        UserRegistration registration = getById(id);
        if (registration == null) {
            return;
        }
        
        EntityManager em = ThreadLocalContext.get().getEntityManager();
        Query q = em.createQuery("delete from com.sismics.docs.core.model.jpa.UserRegistration r where r.id = :id");
        q.setParameter("id", id);
        q.executeUpdate();
        
        // Create audit log
        AuditLogUtil.create(registration, AuditLogType.DELETE, userId);
    }
} 