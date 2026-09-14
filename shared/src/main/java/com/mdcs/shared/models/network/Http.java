package com.mdcs.shared.models.network;

// Contains DTOs for HTTP requests and responses

/**
 * These DTOs are meant to be extended. These provide basic members that are common in any
 * transaction, and remaining members/methods are to be included in the child.
 * 
 * Suggestion is, create a class for a particular api which extends these abstract classes and 
 * 'Body' DTO in the respective package in shared/models.
 */

public class Http {
   
    public static abstract class Request<T>{
        public String endpoint;
        public String headers[] = new String[0];
        public T body;

        public void addHeader(String key, String value){
            String headers[] = new String[this.headers.length + 2];
            System.arraycopy(this.headers, 0, headers, 0, this.headers.length);

            headers[this.headers.length] = key;
            headers[this.headers.length + 1] = value;

            this.headers = headers;
        }
    }

    public static abstract class Response<T>{
        public boolean status;
        public String error;
        public String message;
        public T body;
    }
}
