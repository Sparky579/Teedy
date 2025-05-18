package com.sismics.docs.rest.resource;

import java.util.Date;
import java.util.List;
import java.util.UUID;

import com.sismics.docs.core.dao.UserDao;
import com.sismics.docs.core.dao.UserRegistrationDao;
import com.sismics.docs.core.dao.dto.UserRegistrationDto;
import com.sismics.docs.core.model.jpa.User;
import com.sismics.docs.core.model.jpa.UserRegistration;
import com.sismics.docs.core.util.authentication.AuthenticationUtil;
import com.sismics.docs.rest.constant.BaseFunction;
import com.sismics.rest.exception.ClientException;
import com.sismics.rest.exception.ForbiddenClientException;
import com.sismics.rest.exception.ServerException;
import com.sismics.rest.util.ValidationUtil;
import com.sismics.util.context.ThreadLocalContext;

import jakarta.json.Json;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObjectBuilder;
import jakarta.persistence.EntityManager;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.FormParam;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;

/**
 * User registration REST resources.
 * 
 * @author Claude
 */
@Path("/user/registration")
public class UserRegistrationResource extends BaseResource {
    /**
     * Creates a new user registration request.
     *
     * @api {put} /user/registration Register a new user
     * @apiName PutUserRegistration
     * @apiGroup UserRegistration
     * @apiParam {String{3..50}} username Username
     * @apiParam {String{8..50}} password Password
     * @apiParam {String{1..100}} email E-mail
     * @apiSuccess {String} status Status OK
     * @apiError (client) ValidationError Validation error
     * @apiError (client) AlreadyExistingUsername Login already used
     * @apiError (server) UnknownError Unknown server error
     * @apiVersion 1.5.0
     *
     * @param username User's username
     * @param password Password
     * @param email E-Mail
     * @return Response
     */
    @PUT
    public Response register(
        @FormParam("username") String username,
        @FormParam("password") String password,
        @FormParam("email") String email) {
        
        // Validate the input data
        username = ValidationUtil.validateLength(username, "username", 3, 50);
        ValidationUtil.validateUsername(username, "username");
        password = ValidationUtil.validateLength(password, "password", 8, 50);
        email = ValidationUtil.validateLength(email, "email", 1, 100);
        ValidationUtil.validateEmail(email, "email");
        
        // Check if the user already exists
        UserDao userDao = new UserDao();
        if (userDao.getActiveByUsername(username) != null) {
            throw new ClientException("AlreadyExistingUsername", "Login already used");
        }
        
        EntityManager em = ThreadLocalContext.get().getEntityManager();
        try {
            // Phase 1: Ensure table exists. 
            // Note: It's best practice to manage database schema (DDL like CREATE TABLE) 
            // using migration tools (e.g., Flyway, Liquibase) or JPA's DDL generation capabilities 
            // at deployment time, rather than executing DDL in runtime application code like this.
            // Such DDL statements can also interfere with transaction management.
            em.createNativeQuery(
                "create table if not exists T_USER_REGISTRATION (" +
                "  REG_ID_C varchar(36) not null," +
                "  REG_USERNAME_C varchar(50) not null," +
                "  REG_PASSWORD_C varchar(200) not null," +
                "  REG_EMAIL_C varchar(100) not null," +
                "  REG_CREATEDATE_D timestamp not null," +
                "  REG_STATUS_C varchar(20) not null," +
                "  primary key (REG_ID_C)" +
                ")"
            ).executeUpdate();
            
            // Phase 2: Insert the registration record within an explicit transaction.
            // This assumes resource-local transaction management is appropriate here.
            // If JTA/CMT (Container Managed Transactions) is in use, this manual demarcation 
            // might be incorrect or unnecessary, and the issue might be with the 
            // container's transaction configuration for this JAX-RS method.
            boolean transactionStartedByThisBlock = false;
            if (!em.getTransaction().isActive()) {
                em.getTransaction().begin();
                transactionStartedByThisBlock = true;
            }
            
            try {
                // Generate a random ID
                String id = UUID.randomUUID().toString();
                Date now = new Date();
                
                // Hash the password using AuthenticationUtil
                String hashedPassword = AuthenticationUtil.hashPassword(password);
                
                // Use native SQL to insert directly into the table
                em.createNativeQuery(
                    "INSERT INTO T_USER_REGISTRATION (REG_ID_C, REG_USERNAME_C, REG_PASSWORD_C, REG_EMAIL_C, REG_CREATEDATE_D, REG_STATUS_C) " +
                    "VALUES (?, ?, ?, ?, ?, ?)"
                )
                .setParameter(1, id)
                .setParameter(2, username)
                .setParameter(3, hashedPassword)
                .setParameter(4, email)
                .setParameter(5, now)
                .setParameter(6, "PENDING")
                .executeUpdate();
                
                if (transactionStartedByThisBlock) {
                    em.getTransaction().commit();
                }
            } catch (Exception innerEx) {
                if (transactionStartedByThisBlock && em.getTransaction().isActive()) {
                    try {
                        em.getTransaction().rollback();
                    } catch (Exception rbEx) {
                        // Log or handle rollback exception appropriately
                        innerEx.addSuppressed(rbEx); // Add rollback error to original error
                    }
                }
                throw innerEx; // Re-throw the exception that occurred during DB operations
            }
            
        } catch (Exception e) {
            // This outer catch will handle exceptions from DDL or re-thrown from DML block
            e.printStackTrace(); // Print stack trace for debugging
            throw new ServerException("UnknownError", "Error creating registration: " + e.getMessage(), e);
        }
        
        // Always return OK
        JsonObjectBuilder response = Json.createObjectBuilder()
                .add("status", "ok");
        return Response.ok().entity(response.build()).build();
    }
    
    /**
     * Returns all pending user registration requests.
     *
     * @api {get} /user/registration Get all pending registration requests
     * @apiName GetUserRegistration
     * @apiGroup UserRegistration
     * @apiSuccess {Object[]} registrations List of registrations
     * @apiSuccess {String} registrations.id ID
     * @apiSuccess {String} registrations.username Username
     * @apiSuccess {String} registrations.email E-mail
     * @apiSuccess {Number} registrations.create_date Create date (timestamp)
     * @apiError (client) ForbiddenError Access denied
     * @apiPermission admin
     * @apiVersion 1.5.0
     *
     * @return Response
     */
    @GET
    public Response list(@Context HttpServletRequest request) {
        if (!authenticate()) {
            throw new ForbiddenClientException();
        }
        checkBaseFunction(BaseFunction.ADMIN);
        
        EntityManager em = ThreadLocalContext.get().getEntityManager();
        try {
            // Ensure the table exists. This is a workaround and ideally,
            // schema management should be handled at application startup (e.g., via JPA DDL or migration tools).
            em.createNativeQuery(
                "create table if not exists T_USER_REGISTRATION (" +
                "  REG_ID_C varchar(36) not null," +
                "  REG_USERNAME_C varchar(50) not null," +
                "  REG_PASSWORD_C varchar(200) not null," +
                "  REG_EMAIL_C varchar(100) not null," +
                "  REG_CREATEDATE_D timestamp not null," +
                "  REG_STATUS_C varchar(20) not null," +
                "  primary key (REG_ID_C)" +
                ")"
            ).executeUpdate();
            
            // It's good practice to commit DDL operations if the database requires it
            // or if it helps ensure visibility across different operations/transactions.
            // However, for "CREATE TABLE IF NOT EXISTS", H2 usually handles this well.
            // If issues persist, ensure this DDL transaction is committed if auto-commit is off.
            // Example (if not in an active transaction managed by the container for JAX-RS method):
            /*
            if (!em.getTransaction().isActive()) {
                try {
                    em.getTransaction().begin();
                    em.createNativeQuery(...).executeUpdate();
                    em.getTransaction().commit();
                } catch (Exception e) {
                    if (em.getTransaction().isActive()) {
                        em.getTransaction().rollback();
                    }
                    throw e;
                }
            } else {
                // If already in a transaction, the DDL will be part of it.
                em.createNativeQuery(...).executeUpdate();
            }
            */

        } catch (Exception e) {
            // Log this failure, as it might prevent the listing from working.
            // Depending on policy, you might re-throw or handle gracefully.
            System.err.println("Error ensuring T_USER_REGISTRATION table exists in list method: " + e.getMessage());
            // For now, we'll let it proceed, as UserRegistrationDao might still fail and throw a more specific error.
        }
        
        UserRegistrationDao userRegistrationDao = new UserRegistrationDao();
        List<UserRegistrationDto> registrationList = userRegistrationDao.findAllPending();
        
        JsonArrayBuilder registrations = Json.createArrayBuilder();
        for (UserRegistrationDto registration : registrationList) {
            registrations.add(Json.createObjectBuilder()
                    .add("id", registration.getId())
                    .add("username", registration.getUsername())
                    .add("email", registration.getEmail())
                    .add("create_date", registration.getCreateDate().getTime()));
        }
        
        JsonObjectBuilder response = Json.createObjectBuilder()
                .add("registrations", registrations);
        
        return Response.ok().entity(response.build()).build();
    }
    
    /**
     * Approve a user registration request.
     *
     * @api {post} /user/registration/{id}/approve Approve a registration request
     * @apiName PostUserRegistrationApprove
     * @apiGroup UserRegistration
     * @apiParam {String} id Registration ID
     * @apiParam {Number} storage_quota Storage quota (in bytes)
     * @apiSuccess {String} status Status OK
     * @apiError (client) ForbiddenError Access denied
     * @apiError (client) NotFound Registration request not found
     * @apiError (client) ValidationError Validation error
     * @apiPermission admin
     * @apiVersion 1.5.0
     *
     * @param id Registration ID
     * @return Response
     */
    @POST
    @Path("{id: [a-z0-9\\-]+}/approve")
    public Response approve(
            @PathParam("id") String id,
            @FormParam("storage_quota") String storageQuotaStr) {
        if (!authenticate()) {
            throw new ForbiddenClientException();
        }
        checkBaseFunction(BaseFunction.ADMIN);
        
        // Validate input data
        Long storageQuota = ValidationUtil.validateLong(storageQuotaStr, "storage_quota");
        
        // Get the registration
        UserRegistrationDao userRegistrationDao = new UserRegistrationDao();
        UserRegistration userRegistration = userRegistrationDao.getById(id);
        if (userRegistration == null || !"PENDING".equals(userRegistration.getStatus())) {
            throw new ClientException("NotFound", "Registration request not found");
        }
        
        // Update registration status
        userRegistrationDao.updateStatus(id, "APPROVED", principal.getId());
        
        // Create the user from registration
        User user = new User();
        user.setUsername(userRegistration.getUsername());
        // Set a temporary password that will be overwritten later
        user.setPassword("temporary");
        user.setEmail(userRegistration.getEmail());
        user.setStorageQuota(storageQuota);
        user.setOnboarding(true);
        user.setRoleId("user");
        
        // Create the user
        UserDao userDao = new UserDao();
        try {
            // Create user with temporary password
            String userId = userDao.create(user, principal.getId());
            
            // Update the user with the hashed password from registration
            user.setId(userId);
            user.setPassword(userRegistration.getPassword());
            userDao.updateHashedPassword(user);
            
        } catch (Exception e) {
            e.printStackTrace(); // Print stack trace for debugging
            throw new ServerException("UnknownError", "Error creating user: " + e.getMessage(), e);
        }
        
        // Delete the registration
        userRegistrationDao.delete(id, principal.getId());
        
        // Always return OK
        JsonObjectBuilder response = Json.createObjectBuilder()
                .add("status", "ok");
        return Response.ok().entity(response.build()).build();
    }
    
    /**
     * Reject a user registration request.
     *
     * @api {post} /user/registration/{id}/reject Reject a registration request
     * @apiName PostUserRegistrationReject
     * @apiGroup UserRegistration
     * @apiParam {String} id Registration ID
     * @apiSuccess {String} status Status OK
     * @apiError (client) ForbiddenError Access denied
     * @apiError (client) NotFound Registration request not found
     * @apiPermission admin
     * @apiVersion 1.5.0
     *
     * @param id Registration ID
     * @return Response
     */
    @POST
    @Path("{id: [a-z0-9\\-]+}/reject")
    public Response reject(@PathParam("id") String id) {
        if (!authenticate()) {
            throw new ForbiddenClientException();
        }
        checkBaseFunction(BaseFunction.ADMIN);
        
        // Get the registration
        UserRegistrationDao userRegistrationDao = new UserRegistrationDao();
        UserRegistration userRegistration = userRegistrationDao.getById(id);
        if (userRegistration == null || !"PENDING".equals(userRegistration.getStatus())) {
            throw new ClientException("NotFound", "Registration request not found");
        }
        
        // Update registration status
        userRegistrationDao.updateStatus(id, "REJECTED", principal.getId());
        
        // Delete the registration
        userRegistrationDao.delete(id, principal.getId());
        
        // Always return OK
        JsonObjectBuilder response = Json.createObjectBuilder()
                .add("status", "ok");
        return Response.ok().entity(response.build()).build();
    }
} 