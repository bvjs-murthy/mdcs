package com.mdcs.core.bootstrap;

import java.io.IOException;

import com.mdcs.shared.fileio.FileIO;
import com.mdcs.core.Stream;
import com.mdcs.core.Stream.LogAct;
import com.mdcs.core.Stream.Message;
import com.mdcs.shared.fileio.DataClasses.Accounts;

/*
Determines the user state: 
    - logged in
    - logged out & auth required
    - logged out & auth not required

This is done based on status of Accounts.json file and the auth, refresh tokens availability or
correctness.
    - File exists & auth / refresh tokens are valid => logged in
    - File exists & auth & refresh tokens not valid => logged out
    - File doesn't exist => logged out
    - Manually logged out => logged out (implemented in future versions)
*/

public class UserState {
    // This phase of bootstrap is executed independent of other phases, not in parallel with them.

    private final Stream stream;

    private Accounts read(){
        /*
        If came to this phase, format of Accounts.json would have already validated before. So,
        assuming it is correct and neglecting exceptions here.
        */

        try{
            Accounts acc = FileIO.fileRead(Accounts.class);

            return acc;
        } catch (IOException e){
            /*
            Failed to read from the file. Then assume user is logged out. Because, this can due to
            user / environment issues like, file not found or corrupted.
            */

            return null;
        } catch (Exception e){
            // Eat 5-star, do nothing

            return null;
        }
    }

    private String state(){
        Accounts acc;
        
        if ((acc = this.read()) == null) return "LOGGED_OUT";

        if (!acc.logged_in) return "LOGGED_OUT";
        
        // If any of the below important fields are empty, that means, this is the first run of
        // the application on current device.
        if (
            (acc.user_id == null || acc.user_id.isEmpty())
            || (acc.username == null || acc.username.isEmpty())
            || (acc.email == null || acc.email.isEmpty())
            || (acc.auth_token == null || acc.auth_token.isEmpty())
        ) return "LOGGED_OUT";

        return "LOGGED_IN";
    }

    public String resolve(){
        String usr_state = this.state();

        this.stream.send(
            new Message(
                LogAct.INFO, 
                null, 
                "Resolved user state to " + usr_state + ".\n"
            )
        );
        
        return usr_state;
    }
    
    public UserState(Stream stream){ this.stream = stream; }
}
