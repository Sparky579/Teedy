package com.sismics.docs.core.util.authentication;

import java.util.List;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.sismics.docs.core.constant.Constants;
import com.sismics.docs.core.model.jpa.User;
import com.sismics.util.ClasspathScanner;

import at.favre.lib.crypto.bcrypt.BCrypt;

/**
 * User utilities.
 */
public class AuthenticationUtil {
    /**
     * Logger.
     */
    private static final Logger log = LoggerFactory.getLogger(AuthenticationUtil.class);
    
    /**
     * List of authentication handlers scanned in the classpath.
     */
    private static final List<AuthenticationHandler> AUTH_HANDLERS = Lists.newArrayList(
            new ClasspathScanner<AuthenticationHandler>().findClasses(AuthenticationHandler.class, "com.sismics.docs.core.util.authentication")
                    .stream()

                    .map(clazz -> {
                try {
                    return clazz.getDeclaredConstructor().newInstance();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }).collect(Collectors.toList()));

    

    /**
     * Authenticate a user.
     *
     * @param username Username
     * @param password Password
     * @return Authenticated user
     */
    public static User authenticate(String username, String password) {
        for (AuthenticationHandler authenticationHandler : AUTH_HANDLERS) {
            User user = authenticationHandler.authenticate(username, password);
            if (user != null) {
                return user;
            }
        }
        
        return null;
    }
    
    /**
     * Hash a password using BCrypt.
     * 
     * @param password Password to hash
     * @return Hashed password
     */
    public static String hashPassword(String password) {
        int bcryptWork = Constants.DEFAULT_BCRYPT_WORK;
        String envBcryptWork = System.getenv(Constants.BCRYPT_WORK_ENV);
        if (!Strings.isNullOrEmpty(envBcryptWork)) {
            try {
                int envBcryptWorkInt = Integer.parseInt(envBcryptWork);
                if (envBcryptWorkInt >= 4 && envBcryptWorkInt <= 31) {
                    bcryptWork = envBcryptWorkInt;
                } else {
                    log.warn(Constants.BCRYPT_WORK_ENV + " needs to be in range 4...31. Falling back to " + Constants.DEFAULT_BCRYPT_WORK + ".");
                }
            } catch (NumberFormatException e) {
                log.warn(Constants.BCRYPT_WORK_ENV + " needs to be a number in range 4...31. Falling back to " + Constants.DEFAULT_BCRYPT_WORK + ".");
            }
        }
        return BCrypt.withDefaults().hashToString(bcryptWork, password.toCharArray());
    }
}
