package com.mdcs.core.auth;

import java.net.http.HttpResponse;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import com.mdcs.shared.fileio.FileIO;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.mdcs.core.Stream;
import com.mdcs.core.Stream.AuthAct;
import com.mdcs.core.Stream.LogAct;
import com.mdcs.core.Stream.Message;
import com.mdcs.core.Stream.Response;
import com.mdcs.shared.fileio.DataClasses.Device;
import com.mdcs.shared.models.State;
import com.mdcs.shared.models.State.AuthState;
import com.mdcs.shared.models.auth.Network.EnrollFirReq;
import com.mdcs.shared.models.auth.Network.EnrollAddReq;
import com.mdcs.shared.models.auth.Network.EnrollRes;
import com.mdcs.shared.models.network.Http.Request;
import com.mdcs.shared.network.ProtoMet;

/**
 * Manages device enrollment, first device enrollment, device trusting, primary device tagging
 */

public class Enroll{
    private ProtoMet server;
    private Device device;
    private State state;
    private Stream stream;
    private Callbacks.Enroll callbacks;

    private void getCallbacks(AuthAct action){
        this.stream.send(
            new Message(
                LogAct.INFO, null,
                "Requesting device & workspace information for enrollment workflow...\n"
            )
        );

        CompletableFuture<Response> promise;

        try {
            promise = this.stream.request(new Message(action, null, ""));

            this.callbacks = FileIO.toObject(
                promise.get().getPayload(),
                Callbacks.Enroll.class
            );

            this.stream.send(
                new Message(
                    LogAct.INFO, null,
                    "Received device & workspace information successfully.\n"
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

    private void enroll(Request<?> msg, String log)
    throws Exception{
        HttpResponse<String> res = this.server.post(msg);

        if (res.statusCode() >= 500){
            /*
            Internal server error has occured. Device is not enrolled. Return RECOVER code so
            that login is triggered.
            */

            this.stream.send(
                new Message(
                    LogAct.CRITICAL, null,
                    "Device enrollment failed due to an internal server error.\n"
                )
            );

            this.state.set(AuthState.RECOVER);

            return;
        }

        EnrollRes payload = FileIO.toObject(res.body().toString(), EnrollRes.class);

        if (!payload.status){
            // Device enrollment failed due to user / environment related issue

            this.stream.send(
                new Message(
                    LogAct.CRITICAL, null,
                    "Device enrollment failed due to user or environment issue.\n"
                )
            );
            
            this.state.set(AuthState.RETRY);

            return;
        }

        this.device.device_id = payload.body.device_id;
        this.device.workspace_id = payload.body.workspace_id;

        this.stream.send(new Message(LogAct.INFO, null, log));
        
        FileIO.fileWrite(this.device);
    }

    /**
     * If this method has been provoked, it is assumed that user has been already created and
     * validated.
     * 
     * Populates the supplied Device instance with the enrolled device details.
     */
    protected void first()
    throws Exception{
        this.stream.send(
            new Message(
                LogAct.INFO, null,
                "Initiating first device enrollment...\n"
            )
        );

        this.getCallbacks(AuthAct.FIR_ENROLL);

        this.device.device_name = this.callbacks.deviceName();
        this.device.workspace_name = this.callbacks.workspaceName();

        EnrollFirReq msg = new EnrollFirReq();
        msg.body = new EnrollFirReq.Body(this.device.device_name, this.device.workspace_name);

        this.enroll(msg, "Device enrolled successfully and marked as primary.\n");
    }

    /**
     * This methods is usually paired with login workflows or so, when user account has already
     * been created and user is trying to enroll an additional device under the same workspace.
     * 
     * Generally process() will provoke this.
     */
    public void additional()
    throws Exception{
        this.stream.send(
            new Message(
                LogAct.INFO, null,
                "Initiating additional device enrollment...\n"
            )
        );

        this.getCallbacks(AuthAct.ADD_ENROLL);

        this.device.device_name = this.callbacks.deviceName();
        this.device.workspace_name = this.callbacks.workspaceName();

        EnrollAddReq msg = new EnrollAddReq();
        msg.body = new EnrollAddReq.Body(
            this.device.device_name, 
            this.device.workspace_name,
            this.callbacks.pairingKey()
        );
        
        this.enroll(msg, "Device enrolled successfully under the workspace.\n");
    }

    protected void process()
    throws Exception{
        this.getCallbacks(AuthAct.CHOICE_ENROLL);

        switch (this.callbacks.choice().toLowerCase()){
            case "first": this.first();
                
            case "additional": this.additional();
            
            default: throw new IllegalStateException("Invalid enrollment choice.");
        }
    }

    public void genkey(){}
    
    public Enroll(ProtoMet server, State state, Stream stream){
        this.server = server;
        this.state = state;
        this.stream = stream;

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
