package com.mdcs.shared.utils;

import java.util.HashMap;

/*
Contains all the network prone error codes and their respective definitions.
*/

public class NetErrors {
    
    public static HashMap<String, String> err = new HashMap<>();

    static{
        err.put(
            "MISSING_LOWERCASE", 
            "Password must contain atleast one lower-case letter"
        );

        err.put(
            "MISSING_UPPERCASE", 
            "Passwort must contain atleast one upper-case letter"
        );

        err.put(
            "MISSING_DIGIT", 
            "Password must contain atleast one digit"
        );

        err.put(
            "MISSING_SPECIAL", 
            "Password must contain atleast one special character"
        );

        err.put(
            "SHORT_PASSWORD", 
            "Password should be atleast 6 characters long"
        );

        err.put(
            "INVALID_EMAIL", 
            "Invalid email"
        );

        err.put(
            "PASSWORD_HASH_ERROR", 
            "Internal server error"
        );

        err.put(
            "DUPLICATE_USR", 
            "User with this email already exists"
        );

        err.put(
            "REGISTRATION_FAILED", 
            "Failed to process the request"
        );
    }
}
