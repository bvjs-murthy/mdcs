package com.mdcs.core.bootstrap;

import java.io.IOException;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import com.mdcs.shared.models.Report;
import com.mdcs.shared.models.Report.AppState;
import com.mdcs.shared.models.bootstrap.Network;
import com.mdcs.shared.models.bootstrap.Network.UpdReq;
import com.mdcs.shared.models.bootstrap.Network.UpdRes;
import com.mdcs.shared.network.ProtoMet;
import com.mdcs.core.Stream;
import com.mdcs.core.Stream.LogAct;
import com.mdcs.core.Stream.Message;
import com.mdcs.core.Stream.UpdateAct;
import com.mdcs.shared.fileio.DataClasses;
import com.mdcs.shared.fileio.FileIO;

/*
Validates version format, checks for update and plugin compatibilty between plugins and
application version.
*/

/*
For now only plugin to application compatibility is checked. Assuming if a plugin is compatible
with the application, they are compatible with another plugin which is compatible with the 
application aswell.
*/

public class Version implements Runnable{
    private Stream stream;
    private Report report;
    private ProtoMet server;
    private Properties ver;
    private Network.UpdRes ver_meta;
    private DataClasses.Plugins plugins;
    
    private void pluginUpate(){
        /*
        Plugin update availabiity can be of 2 types.
        One is when new version is available but the current version is still supported by the
        application.
        Another is when current version has become incompatible with the application. In such
        cases, respective plugin is marked incompatible and its existence is neglected.
        */

        Map<String, UpdRes.Plugin> plugins = this.ver_meta.body.plugins;

        for (String name : plugins.keySet()){
            UpdRes.Plugin plugin = plugins.get(name);
            String curr_ver = plugin.curr_ver;
            String avail_ver = plugin.avail_ver;

            // Set compatibility
            this.plugins.plugins.get(name).compatible = plugin.compatible;

            if (plugin.update_req){
                // Pass plugin name, currnt version, available version and continue to application

                this.stream.send(
                    new Message(
                        LogAct.INFO,
                        null,
                        "Plugin update available [name=" + name
                            + " current=" + curr_ver
                            + " available=" + avail_ver + "]\n"
                    )
                );

                this.stream.send(
                    new Message(
                        UpdateAct.PLUGIN,
                        null,
                        name + " " + curr_ver + " " + avail_ver
                    )
                );
            }
        }
        
        try{
            FileIO.fileWrite(this.plugins);
        } catch (Exception e){
            /*
            When can't write the plugin compatibility back to file, can't say if that plugin is
            valid or not further in application. So, treating all the plugins incompatible. But
            wait, what is even the purpose of the application alone when don't have any plugins
            => Terminate application startup. May change this later...
            */

            this.stream.send(
                new Message(
                    LogAct.ERROR,
                    null,
                    "Failed to persist plugin compatibility\n"
                )
            );
            this.report.setAppState(AppState.TERMINATE);
        }
    }
    
    private void appUpdate(){
        /*
        From the metadata we get from the server, will decide if app update is available or not.

        An app update is classified into:
            - Critical update
            - Minor update
            - Patch update
    
        In case of critical update, will block the main app execution (May include modular block
        in future updates).
        In any other cases, will continue to app after noticing the user about the update.
        */

        String curr_ver = this.ver_meta.body.app.cur_ver;
        String avail_ver = this.ver_meta.body.app.avail_ver;
    
        // Checking for critical update
        if (this.ver_meta.body.app.critical_update){
            // Block the app startup and inform user

            this.stream.send(
                new Message(
                    LogAct.CRITICAL, 
                    null, 
                    "Critical update required. Startup cannot continue.\n"
                )
            );

            this.stream.send(
                new Message(
                    UpdateAct.CRITICAL, 
                    null, 
                    curr_ver + " " + avail_ver
                )
            );
            this.report.setAppState(AppState.BLOCK);

            return;
        }
    
        // Checking for other available updates
        String avail[] = avail_ver.split("\\.");
        String curr[] = curr_ver.split("\\.");
    
        if (
            Integer.parseInt(avail[0]) > Integer.parseInt(curr[0])
            || Integer.parseInt(avail[1]) > Integer.parseInt(curr[1])
            || Integer.parseInt(avail[2]) > Integer.parseInt(curr[2])
        ){
            // New update available => Notify user and continue app execution

            this.stream.send(
                new Message(
                    LogAct.INFO, 
                    null, 
                    "Optional application update available.\n"
                )
            );
            this.stream.send(
                new Message(
                    UpdateAct.OPTIONAL, 
                    null, 
                    curr_ver + " " + avail_ver
                )
            );
        }
    }

    private void metadata()
    throws RuntimeException{
        /*
        It may be noted that this metadata is only for version validation and update check during
        bootstrap. Later when user wants to update the applcation or plugins, they will use update
        manager which will require another kind of metadata. Thus, there is no need to persist
        this out of this class.
        */
        
        try{
            // Data as written by local plugins available on user device
            this.plugins = FileIO.fileRead(DataClasses.Plugins.class);
            Map<String, DataClasses.Plugin> plg_data;

            if ((plg_data = this.plugins.plugins) == null) plg_data = new HashMap<>();

            // Final object to send to server
            UpdReq message = new UpdReq();
            message.body = new UpdReq.Body();
            
            message.body.app.current_version = this.ver.getProperty("app.version");

            for (String name : plg_data.keySet())
                message.body.plugins.put(
                    name,
                    plg_data.get(name).avai_ver
                );

            HttpResponse<String> res = server.post(message);

            // Need implementation to handle internal errors
            
            this.ver_meta = FileIO.toObject(res.body(), Network.UpdRes.class);
        } catch (IOException e){
            /*
            Means, failed to write/read from a file (user/environment related issue). In this case
            can't reliably move forward with plugins update checks, but can check for application
            updates.
            */

            this.stream.send(
                new Message(
                    LogAct.ERROR,
                    null,
                    "Failed to persist plugin compatibility\n"
                )
            );

            throw new RuntimeException();

        } catch (InterruptedException e){
            /*
            This implies that server request was interrupted while in process. So, will continue
            to application without update check
            */

            this.stream.send(
                new Message(
                    LogAct.ERROR,
                    null,
                    "Failed to fetch version metadata.\n"
                )
            );

            Thread.currentThread().interrupt();

        } catch (NoSuchFieldException | IllegalAccessException e){
            /*
            This error is not user/environment caused. This is a development bug. Generally
            application is terminated because, this may cause unexpected behaviors
            */

            this.stream.send(
                new Message(
                    LogAct.ERROR,
                    null,
                    "Failed to fetch version metadata due to some internal error.\n"
                )
            );

            this.report.setAppState(AppState.TERMINATE);
        }
    }

    private boolean format() {
        /*
        A particular module's version is valid if it matches the RegEx below. If a particular
        version string is not valid, we abort the startup of the application (for now). In future
        versions, can suspend afected modules and continue to application.
        */

        for (String key : this.ver.stringPropertyNames()) {
            String version = this.ver.getProperty(key);

            if (!version.matches("^[0-9]+\\.[0-9]+\\.[0-9]$")) {
                // The version of this module is not in the valid form.
                
                this.stream.send(
                    new Message(
                        LogAct.ERROR,
                        null,
                        "Invalid version string for '" + key + "' {" + version + "}\n"
                    )
                );

                return false;
            }
        }

        return true;
    }

    @Override
    public void run(){
        this.stream.send(
            new Message(
                LogAct.INFO, 
                null, 
                "Verifying installed version and checking for updates...\n"
            )
        );

        if (!this.format()){
            // Stop application startup
            this.report.setAppState(AppState.TERMINATE);
            return;
        }

        this.metadata();
        this.appUpdate();
        this.pluginUpate();
    }
    
    protected Version(Stream stream, ProtoMet server, Properties ver, Report report){
        this.stream = stream;
        this.server = server;
        this.ver = ver;
        this.report = report;
    }
}
