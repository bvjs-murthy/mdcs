package com.mdcs.core.bootstrap;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mdcs.core.Stream;
import com.mdcs.core.Stream.LogAct;
import com.mdcs.core.Stream.Message;
import com.mdcs.shared.fileio.FileIO;
import com.mdcs.shared.fileio.DataClasses.*;
import com.mdcs.shared.models.Report;
import com.mdcs.shared.models.Report.AppState;

/*
Validates schema and format of the application files, tries recovery or attempts backup or creates
default files in case of failure
*/

public class Schema implements Runnable{
    private Stream stream;
    private Report report;
    private Map<Class<? extends HasPath>, List<FieldRules>> rules;

    private <Template extends HasPath> boolean recover(Class<Template> template){
        // Will be implemented later
        
        return false;
    }

    private <Template extends HasPath> boolean validSchema(
        Class<Template> template, 
        ObjectNode node
    ){
        boolean valid = true;

        for (FieldRules rule : this.rules.get(template)){
            JsonNode field = node.get(rule.name);

            switch (rule.type){
                case "int":
                    if (field == null || field.isNull() || !field.isInt()){
                        node.put(rule.name, 0);
                        valid = false;
                    }

                    break;
            
                case "string":
                    if (field == null || field.isNull() || !field.isTextual()){
                        node.put(rule.name, "");
                        valid = false;
                    }

                    break;
            
                case "boolean":
                    if (field == null || field.isNull() || !field.isBoolean()){
                        node.put(rule.name, false);
                        valid = false;
                    }
            }
        }
        
        return valid;
    }

    private void validate()
    throws Exception{
        /*
        A file's schema is said to be invalid if data in a field is not of expected Service. Example,
        {"username": 123}

        And a file's format is invalid if it can't be parsed into the template DTO (invalid JSON)
        */

        for (Class<? extends HasPath> template : this.rules.keySet()){
            String tem_name = template.getSimpleName();

            try{
                JsonNode raw = FileIO.getJsonNode(template);

                if (raw == null || !raw.isObject()) throw new IOException();

                ObjectNode node = (ObjectNode) raw;

                if (!this.validSchema(template, node)){
                    this.stream.send(
                        new Message(
                            LogAct.ERROR, 
                            null, 
                            "Invalid file schema for " + tem_name + "\n"
                        )
                    );

                    try{
                        // node would be updated if schema is invalid. So we need to write those
                        // updates back into the file

                        FileIO.writeJsonNode(template, node);
                        
                        this.stream.send(
                            new Message(
                                LogAct.ERROR, 
                                null, 
                                "Defaulted invalid file content for " + tem_name + "\n"
                            )
                        );
                    } catch (Exception e){
                        // Failed to write file. Stop application startup

                        this.stream.send(
                            new Message(
                                LogAct.ERROR, 
                                null, 
                                "Failed to default invalid file, " + tem_name + "\n"
                            )
                        );

                        this.report.setAppState(AppState.TERMINATE);

                        return;
                    }
                }
            } catch (IOException e){
                // Invalid format.
                // This could be caused due to user tinkering files or corrupted file write.
                // Try backup restore first and then default file write if it fails

                this.stream.send(
                    new Message(
                        LogAct.ERROR, 
                        null, 
                        "Invalid file format for " + tem_name + "\n"
                    )
                );

                if (!recover(template)){
                    // Recovery failed. Create default files
                    
                    try{
                        FileIO.createAndWrite(template);

                        this.stream.send(
                            new Message(
                                LogAct.INFO, 
                                null, 
                                "Created default file for " + tem_name + "\n"
                            )
                        );
                    } catch (Exception f){
                        // Failed to write defaults. Stop application startup

                        this.stream.send(
                            new Message(
                                LogAct.ERROR,
                                null,
                                "Failed to recover/create " + tem_name + ".\n"
                            )
                        );

                        this.report.setAppState(AppState.TERMINATE);

                        return;
                    }
                } else{
                    // Backups recovered
                    this.stream.send(
                        new Message(
                            LogAct.INFO, 
                            null, 
                            "Recovered " + tem_name + " from backups.\n"
                        )
                    );
                }
            }
        }
    }

    private void fieldRules(){
        /*
        Initially, we will get the classes which implement HasPath interface, that means, the
        classes which are file templates.

        For each such class, we create field rules object, skipping static fields (which doesn't
        include 'path')
        */
        
        List<Class<? extends HasPath>> templates = List.of(
            Accounts.class,
            Device.class,
            ModulePaths.class,
            Configs.class,
            Data.class,
            Plugins.class,
            Cikey.class
        );

        // Non JSON files are also possible here like that Cikey file.
       
        for (Class<? extends HasPath> template : templates){

            if (!HasPath.class.isAssignableFrom(template)) continue;

            this.rules.put(template, new ArrayList<>());
            Field fields[] = template.getDeclaredFields();

            for (Field field : fields){

                if (Modifier.isStatic(field.getModifiers())) continue;

                this.rules.get(template).add(
                    new FieldRules(
                        field.getName(),
                        field.getType().getSimpleName().toLowerCase()
                    )
                );
            }
        }
    }
    
    @Override
    public void run(){
        this.stream.send(
            new Message(
                LogAct.INFO, 
                null, 
                "Validating file system schema and format...\n"
            )
        );
        
        // Makes sure directories exist already
        FileIO.createAppFileDirs();

        // Set field rules for each file template
        this.fieldRules();

        try{ this.validate(); }
        catch (Exception e) {
            /*
            This is development issue, not any exception or error related to user and/or their
            environment. Chances of reaching this are low but never zero !!!
            */

            this.stream.send(
                new Message(
                    LogAct.CRITICAL, 
                    null, 
                    "Expected data field doesn't exist or can't be accessed in UNKNOWN file.\n"
                )
            );

            this.report.setAppState(AppState.TERMINATE);
        }

        this.rules.clear();
    }

    protected Schema(Stream stream, Report report){
        this.stream = stream;
        this.report = report;

        this.rules = new HashMap<>();
    }
}