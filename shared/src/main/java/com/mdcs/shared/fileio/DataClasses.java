package com.mdcs.shared.fileio;

import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnore;

import com.mdcs.shared.utils.SystemUtils;

// Maintains blue print of file structures mapped to java classes

public class DataClasses{

    // Generic field rules class
    public static class FieldRules{
        public String name;
        public String type;

        public FieldRules(String name, String type){
            this.name = name;
            this.type = type;
        }
    }

    public interface HasPath{ String getPath(); }

    // Template for Accounts.json file
    public static class Accounts implements HasPath{   

        private static final String path = Paths.get(
            SystemUtils.getAppDataDirectory(), 
            "entities", "Accounts.json"
        ).toString();

        public String
            user_id,
            username,
            email,
            auth_token,
            refresh_token;

        public boolean logged_in;

        @JsonIgnore
        public String getPath(){ return path; }

        // When data is sent
        public Accounts(String details[]){
            this.user_id = details[0];
            this.username = details[1];
            this.email = details[2];
            this.auth_token = details[3];
            this.refresh_token = details[4];
            this.logged_in = details[5].equals("true");
        }

        public Accounts(){}
    }

    // Template for Device.json
    public static class Device implements HasPath{

        private static final String path = Paths.get(
            SystemUtils.getAppDataDirectory(), 
            "entities", "Device.json"
        ).toString();

        public String
            device_id,
            device_name,
            workspace_id,
            workspace_name;

        @JsonIgnore
        public String getPath(){ return path; }

        // When data is sent
        public Device(String details[]){
            this.device_id = details[0];
            this.device_name = details[1];
            this.workspace_id = details[2];
            this.workspace_name = details[3];
        }

        public Device(){}
    }

    // Template for Configs.json
    public static class Configs implements HasPath{

        private static final String path = Paths.get(
            SystemUtils.getAppDataDirectory(), 
            "application", "Configs.json"
        ).toString();

        @JsonIgnore
        public String getPath(){ return path; }

        // Just a place holder, this will be update during UI modules
        public String temp_field;
    }

    // Template for ModulePaths.json
    public static class ModulePaths implements HasPath{

        private static final String path = Paths.get(
            SystemUtils.getAppDataDirectory(), 
            "application", "ModulePaths.json"
        ).toString();

        public String
            clipboard,
            file_share,
            folder_sync,
            protocols,
            application_acess,
            scheduler,
            host,
            client,
            mesh;

        @JsonIgnore
        public String getPath(){ return path; }

        // When data is sent
        public ModulePaths(String details[]){
            this.clipboard = details[0];
            this.file_share = details[1];
            this.folder_sync = details[2];
            this.protocols = details[3];
            this.application_acess = details[4];
            this.scheduler = details[5];
            this.host = details[6];
            this.client = details[7];
            this.mesh = details[8];
        }

        public ModulePaths(){}
    }

    // Template for Data.json
    public static class Data implements HasPath{

        private static final String path = Paths.get(
            SystemUtils.getAppDataDirectory(), 
            "application", "Data.json"
        ).toString();

        @JsonIgnore
        public String getPath(){ return path; }

        // Just a place holder, this will be update during later modules/services/plugins
        public String temp_field;
    }

    // Template for plugin internal strucutre
    public static class Plugin{
        public String path;
        public String avai_ver;
        public boolean compatible;
    }

    // Template for Plugins.json
    public static class Plugins implements HasPath{

        private static final String path = Paths.get(
            SystemUtils.getAppDataDirectory(), 
            "plugins", "Plugin.json"
        ).toString();

        @JsonIgnore
        public String getPath(){ return path; }

        // plugin name -> Plugin
        public Map<String, Plugin> plugins = new HashMap<>();
    }

    // Template for Cikey.key
    public class Cikey implements HasPath{

        private static final String path = Paths.get(
            SystemUtils.getAppDataDirectory(), 
            "secrets", "Cikey.key"
        ).toString();
        
        public String getPath(){ return path; }

        public static String getCiPath(){ return path; }
    }
}