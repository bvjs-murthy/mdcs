package com.mdcs.core.bootstrap;

import java.util.Properties;

import com.mdcs.core.Stream;
import com.mdcs.core.Stream.LogAct;
import com.mdcs.core.Stream.Message;
import com.mdcs.shared.models.Report;
import com.mdcs.shared.models.Report.AppState;
import com.mdcs.shared.network.ProtoMet;

// Initializes and prepares the application before starting

/*
Phases in bootstrap:
    - Schema validation
    - Version validation / Update check
    - User state resolution

It may be noted that user state resolution phase is executed after the remaining phases of
bootstrap. It is called independently by the master and not as a parallel worker.
*/

public class Supervise{

    /*
    Processes like schema validation, version validation are independent of each other and thus 
    can be executed in parallel.

    Since, they are mix of CPU bound, I/O bound and Network bound processes, they won't cause much
    context switching for CPU.
    */

    private ProtoMet server;
    private Properties vers;
    private Stream stream;
    public Report report;

    public void run(){
        this.stream.send(
            new Message(
                LogAct.INFO, 
                null, 
                "Initiating application bootstrap.\n"
            )
        );

        Schema schema = new Schema(this.stream, this.report);
        Thread sch_worker = new Thread(schema);
        sch_worker.setDaemon(true);

        Version version = new Version(this.stream, this.server, this.vers, this.report);
        Thread ver_worker = new Thread(version);
        ver_worker.setDaemon(true);

        ver_worker.start();
        sch_worker.start();

        try { sch_worker.join(); } 
        catch (InterruptedException e) { sch_worker.interrupt(); }
        
        try { ver_worker.join(); } 
        catch (InterruptedException e) { ver_worker.interrupt(); }

        if (this.report.getAppState() == AppState.CONTINUE)
            this.stream.send(
                new Message(
                    LogAct.INFO, 
                    null, 
                    "Application bootstrap reported with no severity.\n"
                )
            );

        else if (this.report.getAppState() == AppState.BLOCK)
            this.stream.send(
                new Message(
                    LogAct.CRITICAL, 
                    null, 
                    "Application startup blocked after bootstrap.\n"
                )
            );

        else if (this.report.getAppState() == AppState.TERMINATE)
            this.stream.send(
                new Message(
                    LogAct.CRITICAL, 
                    null, 
                    "Application startup aborted after bootstrap.\n"
                )
            );
    }

    public Supervise(ProtoMet server, Properties vers, Stream stream, Report report){
        this.server = server;
        this.vers = vers;
        this.stream = stream;
        this.report = report;
    }
}