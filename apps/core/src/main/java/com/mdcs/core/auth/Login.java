package com.mdcs.core.auth;

import java.net.http.HttpResponse;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.mdcs.core.Stream;
import com.mdcs.core.Stream.AuthAct;
import com.mdcs.core.Stream.LogAct;
import com.mdcs.core.Stream.Message;
import com.mdcs.core.Stream.Response;
import com.mdcs.shared.fileio.FileIO;
import com.mdcs.shared.fileio.DataClasses.Accounts;
import com.mdcs.shared.fileio.DataClasses.Device;
import com.mdcs.shared.models.State;
import com.mdcs.shared.models.State.AuthState;
import com.mdcs.shared.models.auth.Network.LoginReq;
import com.mdcs.shared.models.auth.Network.LoginRes;
import com.mdcs.shared.network.ProtoMet;

public class Login{
    private ProtoMet server;
    private Stream stream;
    private Callbacks.Login callbacks;
    private State state;
    private Accounts user;
    private Device device;

    private void getCallbacks(){
        this.stream.send(
            new Message(
                LogAct.INFO, null,
                "Requesting account information for login workflow...\n"
            )
        );

        CompletableFuture<Response> promise;

        try {
            promise = this.stream.request(
                new Message(AuthAct.LOGIN, null, "")
            );

            this.callbacks = FileIO.toObject(
                promise.get().getPayload(),
                Callbacks.Login.class
            );

            this.stream.send(
                new Message(
                    LogAct.INFO, null,
                    "Received account information successfully.\n"
                )
            );
        } catch (InterruptedException e) {
            // Will decide what to do later
        } catch (JsonProcessingException e) {
            // Will decide what to do later
        } catch (ExecutionException e) {
            // Will decide what to do later
        }
    }

    private void usrAuth()
    throws Exception{
        this.user.email = this.callbacks.email();
        String pswd = this.callbacks.pswd();

        LoginReq message = new LoginReq();
        
        message.body = new LoginReq.Body(
            this.user.email, 
            pswd,
            this.device.workspace_id,
            this.device.device_id
        );

        HttpResponse<String> res = this.server.post(message);

        if (res.statusCode() >= 500){
            /*
            Some sort of internal server error has occured. User must be notified that this action
            cannot be performed now or till server has recovered.
            */

            this.stream.send(
                new Message(
                    LogAct.CRITICAL, null,
                    "Login failed due to an internal server error.\n"
                )
            );

            this.state.set(AuthState.TERMINATE);

            return;
        }

        LoginRes payload = FileIO.toObject(res.body().toString(), LoginRes.class);

        if (!payload.status){
            /*
            Means, login was failed due to some user or environment related error. In such cases,
            show the error message and prompt user to try again.
            */

            this.stream.send(
                new Message(
                    LogAct.CRITICAL, null,
                    "Login failed due to user or environment issue.\n"
                )
            );

            this.state.set(AuthState.RETRY);

            return;
        }

        this.user.user_id = payload.body.user_id;
        this.user.username = payload.body.username;

        if (payload.body.phase.equals("UNVERIFIED")){
            /**
             * This means, user account has been created but their email was not verified. So, we
             * should trigger validate user workflow.
             * 
             * This can be an internal continuous process, we don't need to acknowledge the host
             * process about it.
             */

            this.stream.send(
                new Message(
                    LogAct.INFO, null,
                    "User account is unverified. Triggering validation workflow...\n"
                )
            );

            Register reg = new Register(
                this.server,
                this.stream,
                this.state,
                this.user
            );

            reg.validateUsr();

            return;
        }

        if (payload.body.phase.equals("VERIFIED")){
            /**
             * This means, user account has been created and their email was verified. So, we
             * should trigger device enrollment workflow.
             */

            this.stream.send(
                new Message(
                    LogAct.INFO, null,
                    "User account is verified. Triggering device enrollment workflow...\n"
                )
            );

            new Enroll(this.server, this.state, this.stream).process();

            if (this.state.get() != AuthState.SUCCESS) return;
        }

        this.user.auth_token = payload.body.auth_tok;
        this.user.refresh_token = payload.body.refresh_tok;

        this.stream.send(
            new Message(
                LogAct.INFO, null,
                "Login workflow completed with no issues.\n"
            )
        );
    }

    public void run(){
        this.stream.send(
            new Message(LogAct.INFO, null, "Initiating login workflow...\n")
        );

        this.getCallbacks();

        try { this.usrAuth(); }
        catch (Exception e){
            e.printStackTrace();
        } finally{
            
            try {
                FileIO.fileWrite(this.user);
            } catch (Exception e) {
                /*
                User is signed in but we can't persist the data for the next time. In such cases,
                treat user as signed in temporarily and ask for log in next time.
                */

                this.user.logged_in = false;

                this.stream.send(
                    new Message(
                        LogAct.ERROR, null,
                        "User logged in temporarily after failure to persist user/device data.\n"
                    )
                );
            }
        }
    }
    
    public Login(ProtoMet server, Stream stream, State state){
        this.server = server;
        this.stream = stream;
        this.state = state;

        /**
         * User data is overwritten in absolutely every scenario of login. Because, we can't say
         * whether the user intends to login to another account or is logging in because of expired
         * auth tokens.
         */
        this.user = new Accounts();

        /**
         * For device data, read the file first and if the file doesn't exist or throwing some error
         * (which ususally don't happen because of bootstrap process) then default the objects.
         * 
         * This helps us in two cases. If device is enrolled for the account user is trying to log
         * into, we can skip device enrollment and if device id is absent or is not associated with
         * the account, we will trigger the enrollment process.
        */
        try{
            this.device = FileIO.fileRead(Device.class);
        } catch (Exception e){
            this.device = new Device();
        }
    }
}
