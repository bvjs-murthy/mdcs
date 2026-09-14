package com.mdcs.core.auth;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class Callbacks {

    protected static class Register{
        private final String username;
        private final String email;
        private final String pswd;

        protected String username(){ return this.username; }
        protected String email(){ return this.email; }
        protected String pswd(){ return this.pswd; }

        @JsonCreator
        protected Register(
            @JsonProperty("username") String username,
            @JsonProperty("email") String email,
            @JsonProperty("pswd") String pswd
        ){
            this.username = username;
            this.email = email;
            this.pswd = pswd;
        }
    }

    protected static class Login{
        private final String email;
        private final String pswd;

        protected String email(){ return this.email; }
        protected String pswd(){ return this.pswd; }

        @JsonCreator
        protected Login(
            @JsonProperty("email") String email,
            @JsonProperty("pswd") String pswd
        ){
            this.email = email;
            this.pswd = pswd;
        }
    }

    protected static class Enroll{
        private String choice;
        private final String device_name;
        private final String workspace_name;
        private final String pairing_key;

        protected String choice(){ return this.choice; }
        protected String deviceName(){ return this.device_name; }
        protected String workspaceName(){ return this.workspace_name; }
        protected String pairingKey(){ return this.pairing_key; }

        @JsonCreator
        protected Enroll(
            @JsonProperty("choice") String choice,
            @JsonProperty("device_name") String device_name,
            @JsonProperty("workspace_name") String workspace_name,
            @JsonProperty("pairing_key") String pairing_key
        ){
            this.choice = choice;
            this.device_name = device_name;
            this.workspace_name = workspace_name;
            this.pairing_key = pairing_key;
        }
    }
}
