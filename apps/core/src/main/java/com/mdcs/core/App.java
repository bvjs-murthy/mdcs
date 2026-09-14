package com.mdcs.core;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.mdcs.core.Stream.Message;
import com.mdcs.core.Stream.Response;
import com.mdcs.core.auth.Enroll;
import com.mdcs.core.auth.Login;
import com.mdcs.core.auth.Register;
import com.mdcs.core.bootstrap.Supervise;
import com.mdcs.core.bootstrap.UserState;
import com.mdcs.shared.models.Report;
import com.mdcs.shared.models.Report.AppState;
import com.mdcs.shared.models.State;
import com.mdcs.shared.models.State.AuthState;
import com.mdcs.shared.network.ProtoMet;

/*
New implementation of Report (core -> manager)

This implementation has some tweaks on the previous Report and Envelop mechanisms.

    - Logs:
    previously: Log module
    changed to: Stream (to the manager)

    - App state
    previously: AppState module
    changed to: exit codes

    - Completely removed JobType and Summary. Manager doesn't need to know master - worker
    architecture of Core.

Point to be noted is, this stream is just a communication between Core and Manager, and the logs
are not for UI purpose but mainly for internal use. UI module will decide what/how to show.
*/

class App {

    /*
    There is no fnctionaity to write the logs in this module, the logs are written into stream of
    the manager, which then writes them.
    */

    /**
     * Need to define a set of exit codes based on the status of sub routine (bootstrap, auth and
     * user state).
     */

    private Stream stream;
    private ProtoMet server;
    private Properties APP, VER;
    private Report report;

    private static Properties config(String filename)
    throws IOException{
        Properties property = new Properties();

        try (
            InputStream reader = App.class.getClassLoader().getResourceAsStream(filename)
        ){
            if (reader == null) throw new IOException();

            property.load(reader);
        }

        return property;
    }

    private int bootstrap(){
        new Supervise(this.server, this.VER, this.stream, this.report).run();
        
        return switch (this.report.getAppState()){
            case AppState.BLOCK -> 22;
            case AppState.TERMINATE -> 23;
            default -> 0;
        };
    }

    private int auth(String option){
        option = option.toLowerCase();
        State state = new State();

        if (option.equals("login"))
            new Login(this.server, this.stream, state).run();

        else if (option.equals("register")){
            try { new Register(this.server, this.stream, state).run(); }
            catch (JsonProcessingException e) { return 3; }
            catch (InterruptedException e) { return 11; }
            catch (ExecutionException e) { return 20; }
        }

        else return 2;
        
        if (state.get() == AuthState.TERMINATE) return 23;

        else if (state.get() == AuthState.RETRY) return 24;

        else if (state.get() == AuthState.RECOVER) return this.auth("login");

        return 0;
    }

    private int enroll(String option){
        option = option.toLowerCase();
        State state = new State();
        Enroll enr = new Enroll(this.server, state, this.stream);

        if (option.equals("genkey")) enr.genkey();

        else if (option.equals("first")){

            try { enr.first(); }
            catch (InterruptedException e) { return 11; }
        }

        else if (option.equals("additional")){

            try { enr.additional(); }
            catch (InterruptedException e) { return 11; }
        }

        else return 2;
        
        if (state.get() == AuthState.TERMINATE) return 23;

        else if (state.get() == AuthState.RETRY || state.get() == AuthState.RECOVER)
            return 24;

        return 0;
    }

    private int flow(){
        int code = this.bootstrap();

        if (code != 0) return code;

        if (new UserState(this.stream).resolve().equals("LOGGED_OUT")){
            // Get the auth choice from user and perform the respective flow.
            CompletableFuture<Response> promise;
            String choice;

            try {
                promise = this.stream.request(
                    new Message(Stream.AuthAct.AUTH_CHOICE, null, null)
                );

                choice = promise.get().getPayload();
            }
            catch (InterruptedException e) { return 11; }
            catch (ExecutionException e) { return 20; }

            if (choice.toLowerCase().equals("login"))
                return this.auth("login");

            else if (choice.toLowerCase().equals("register"))
                return this.auth("register");

            else return 2;
        }

        // Enrollment is implicitly handled by login or register sub-routines itself.

        return 0;
    }

    /**
     * Gets the command line arguments in the intended structure and maps those commands into
     * respective functions.
     * 
     * Returns respective exit code based on how the sub routine has ended/executed.
     */
    protected int handle(String args[]){

        if (args.length == 0) return 2;

        switch (args[0]){
            case "flow":
                if (args.length != 1) return 2;

                return this.flow();

            case "bootstrap":
                if (args.length != 1) return 2;

                return this.bootstrap();

            case "auth":
                if (args.length != 2) return 2;

                return this.auth(args[1]);

            case "enroll":
                if (args.length != 2) return 2;

                return this.enroll(args[1]);

            default: // Eat 5-star, do nothing.
        }

        return 0;
    }
    
    protected App(Stream stream){
        this.stream = stream;
        this.report = new Report();
        
        try{
            this.APP = config("application.properties");
            this.VER = config("versions.properties");
        } catch (Exception e){
            /**
             * If the application.properties or versions.properties file is not found, then the
             * application cannot proceed further reliably.
             */

            System.exit(4);
        }

        this.server = new ProtoMet(this.APP);
    }
}
