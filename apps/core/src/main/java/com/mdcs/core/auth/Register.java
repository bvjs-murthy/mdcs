package com.mdcs.core.auth;

import java.io.IOException;
import java.net.http.HttpResponse;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import com.mdcs.shared.models.State;
import com.mdcs.shared.models.State.AuthState;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.mdcs.core.Stream;
import com.mdcs.core.Stream.AuthAct;
import com.mdcs.core.Stream.LogAct;
import com.mdcs.core.Stream.Message;
import com.mdcs.core.Stream.Response;
import com.mdcs.shared.fileio.FileIO;
import com.mdcs.shared.fileio.DataClasses.Accounts;
import com.mdcs.shared.fileio.DataClasses.Device;
import com.mdcs.shared.models.auth.Network.CreateUsrReq;
import com.mdcs.shared.models.auth.Network.CreateUsrRes;
import com.mdcs.shared.models.auth.Network.ValidateUsrReq;
import com.mdcs.shared.models.auth.Network.ValidateUsrRes;
import com.mdcs.shared.network.ProtoMet;
import com.mdcs.shared.security.TokCipher;
import com.mdcs.shared.utils.NetErrors;

/**
 * Manages account creation, user validation, and enrollment of the user's first device.
 *
 * The first device is automatically trusted and marked as the primary device,
 * which is later used for enrolling additional devices and other account operations.
 *
 * Registration phases:
 * - Account creation
 * - Account validation
 * - First device enrollment
 */

public class Register{

    /*
    What if user creation and validation succeeded but first device enrollment failed? User won't
    be able to register the device during signup.

    In such cases, we have two ideas.
        1. Immediately trigger the login state and let user sign in to trust their device.
        2. Mark user as VERIFIED and let them manually enroll the device later.

    2nd one is good as it gives full control of which device to make primary to user. It will be
    implemented in future versions.

    For now, we maintain 3 states for a user account.
        UNVERIFIED -> After user creation
        VERIFIED   -> After user validation
        ONBOARDED  -> After first device enrollment

    So that if:
        User was created and not validated => OTP verification in next login.
        User created and validated but device not enrolled => Device enrollment in next login.

    That means, we should make the login flow capable of getting the state codes from server for
    this case :)
    */

    private ProtoMet server;
    private Stream stream;
    private State state;
    private Accounts user;
    private Device device;
    private Callbacks.Register callbacks;

    protected void validateUsr()
    throws IOException, InterruptedException, ExecutionException{
        this.stream.send(
            new Message(
                LogAct.INFO, null,
                "Initializing user account validation flow...\n"
            )
        );

        String otp = this.stream.request(
            new Message(AuthAct.OTP, null, "")
        ).get().getPayload();

        ValidateUsrReq message = new ValidateUsrReq();

        message.body = new ValidateUsrReq.Body(
            this.user.user_id, 
            this.user.email, 
            otp
        );

        HttpResponse<String> res = this.server.post(message);

        if (res.statusCode() >= 500){
            // Some internal server error has occured. Return recovery code because, need to
            // recover from Unverified state

            this.stream.send(
                new Message(
                    LogAct.ERROR, null,
                    "Account validation failed due to an internal server error\n"
                )
            );

            this.state.set(AuthState.RECOVER);

            return;
        }

        ValidateUsrRes payload = FileIO.toObject(
            res.body().toString(),
            ValidateUsrRes.class
        );

        /*
        If the OTP is incorrect, need to let the user try again. Current sequence discards the
        OTP entirely, requests new one. That is bad UX. Need to work on that.
        */

        if (!payload.status){
            // Auth failed due to some user / environment related issue

            this.stream.send(
                new Message(
                    LogAct.ERROR, null,
                    "Account validation failed due to user/environment issue {"
                        + NetErrors.err.get(payload.error)
                        + "}\n"
                )
            );
            
            this.state.set(AuthState.RECOVER);

            return;
        }

        this.user.auth_token = TokCipher.encrypt(payload.body.auth_tok);
        this.user.refresh_token = TokCipher.encrypt(payload.body.refresh_tok);
        
        this.stream.send(
            new Message(
                LogAct.INFO, null,
                "Account validation completed with no issues.\n"
            )
        );
    }

    /**
     * Create new user account with unverified state. This process will automatically send an OTP
     * to user email without the need of separate api.
     * 
     * In cases of business failures or internal server errors, the OTP is not sent, so it is fine
     * to neglect that case.
     * 
     * @throws IOException
     * @throws InterruptedException
     */
    private void createUsr()
    throws IOException, InterruptedException{
        this.stream.send(
            new Message(
                LogAct.INFO, null,
                "Initializing user account creation flow...\n"
            )
        );

        this.user.username = this.callbacks.username();
        this.user.email = this.callbacks.email();
        String password = this.callbacks.pswd();

        CreateUsrReq message = new CreateUsrReq(); 

        message.body = new CreateUsrReq.Body(
            this.user.username, 
            this.user.email, 
            password
        );

        HttpResponse<String> res = this.server.post(message);

        if (res.statusCode() >= 500){
            /*
            Some sort of internal server error has occured. User must be notified that this action
            cannot be performed now or till server has recovered.

            In this case, the response message from server doesn't conatin the payload. So, need
            to return early.
            */

            this.stream.send(
                new Message(
                    LogAct.ERROR, null,
                    "Account creation failed due to an internal server error\n"
                )
            );

            this.state.set(AuthState.TERMINATE);

            return;
        }

        CreateUsrRes payload = FileIO.toObject(res.body().toString(), CreateUsrRes.class);

        if (!payload.status){
            /*
            Means, account creation was failed due to some user or environment related error. In
            such cases, show the error message and prompt user to try again.
            */

            this.stream.send(
                new Message(
                    LogAct.ERROR, null,
                    "Account creation failed due to user/environment issue {"
                        + NetErrors.err.get(payload.error)
                        + "}\n"
                )
            );

            this.state.set(AuthState.RETRY);

            return;
        }

        this.user.user_id = payload.body.user_id;
        
        this.stream.send(
            new Message(
                LogAct.INFO, null,
                "Account creation completed with no issues.\n"
            )
        );
    }

    private void getCallbacks(){
        this.stream.send(
            new Message(
                LogAct.INFO, null,
                "Requesting account information for registration workflow...\n"
            )
        );

        CompletableFuture<Response> promise;

        try {
            promise = this.stream.request(
                new Message(AuthAct.REGISTER, null, "")
            );

            this.callbacks = FileIO.toObject(
                promise.get().getPayload(),
                Callbacks.Register.class
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

    public void run(){
        this.stream.send(
            new Message(
                LogAct.INFO, null, "Initializing registration workflow...\n"
            )
        );

        this.getCallbacks();
        
        try{
            Enroll enroll = new Enroll(this.server, this.state, this.stream);

            this.createUsr();

            if (this.state.get() == AuthState.SUCCESS)
                this.validateUsr();

            if (this.state.get() == AuthState.SUCCESS)
                enroll.first();

            if (this.state.get() == AuthState.RECOVER)
                this.user.logged_in = false;

            else
                this.user.logged_in = true;

        } catch (IOException e){
            /*
            This means, server couldn't be contacted. This can cause due to:
                No internet connection
                Connection timed out
                DNS lookup failed
                etc.

            In such cases, send the termination code.
            */

            this.stream.send(
                new Message(
                    LogAct.ERROR, null,
                    "Failed to contact the server <" + e.getMessage() + ">\n"
                )
            );
            
            this.state.set(AuthState.TERMINATE);
        } catch (InterruptedException e){
            /*
            This is more of an internal / system event. No need to prompt user about it. Because,
            it happens when another thread has interrrupted this thread (happens when application
            is being shut down).
            */

            this.stream.send(
                new Message(
                    LogAct.ERROR, null,
                    "Thread was interrupted while performing user registration\n"
                )
            );

            this.state.set(AuthState.TERMINATE);
        } catch (ExecutionException e) {
            /**
             * Happens when there was an exception with promise. In this case, we can't proceed
             * with registration workflow. So, terminate the workflow and prompt user to try again.
             */

            this.stream.send(
                new Message(
                    LogAct.ERROR, null,
                    "Failed to get account/device information for registration workflow\n"
                )
            );

            this.state.set(AuthState.TERMINATE);
        } catch (Exception e) {

            this.stream.send(
                new Message(
                    LogAct.ERROR, null,
                    "Failed to save device information\n"
                )
            );

            this.state.set(AuthState.TERMINATE);
        } finally{
            
            try {
                FileIO.fileWrite(this.user);
            } catch (Exception e) {
                /*
                User is signed in but we can't persist the data for the next time. In such cases,
                treat user as logged in temporarily and ask for log in again next time.
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

    /*
    As this is a worker (thread), i mean, run() can't take parameters or return values right, so
    we will get the State object from caller and fill it with state. This will also allow us to
    not worry about creating objects locally (-_-)
    */
    
    public Register(ProtoMet server, Stream stream, State state)
    throws InterruptedException, JsonProcessingException, ExecutionException{
        this.server = server;
        this.stream = stream;
        this.state = state;

        this.user = new Accounts();
        this.device = new Device();
    }

    public Register(
        ProtoMet server,
        Stream stream,
        State state,
        Accounts user,
        Device device
    ) throws InterruptedException, JsonProcessingException, ExecutionException{
        this.server = server;
        this.stream = stream;
        this.state = state;
        this.user = user;
        this.device = device;
    }
}
