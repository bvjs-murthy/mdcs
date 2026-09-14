package com.mdcs.shared.models;

// DTO for job report to the master.

// Submitted to the master by workers after completing their execution
public class Report {
    
    public enum AppState {
        TERMINATE,
        BLOCK,
        CONTINUE
    }

    private AppState app_state = AppState.CONTINUE;

    public void setAppState(AppState state){
        // Can only be stepped up
        // CONTINUE -> BLOCK -> TERMINATE

        if (this.app_state == AppState.TERMINATE)
            return;

        if (
            this.app_state == AppState.BLOCK
            && state == AppState.CONTINUE
        ) return;

        this.app_state = state;
    }

    public AppState getAppState(){ return this.app_state; }
}
