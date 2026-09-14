package com.mdcs.shared.models;

public class State{

    public enum AuthState{
        SUCCESS,
        RETRY,
        RECOVER,
        TERMINATE
    }

    private AuthState state = AuthState.SUCCESS;

    public void set(AuthState to){
        // Can only be downgraded (SUCCESS -> RETRY / RECOVER -> TERMINATE)

        if (this.state == AuthState.TERMINATE) return;

        if (this.state == AuthState.SUCCESS) {
            this.state = to;
            return;
        }

        if (
            this.state == AuthState.RETRY 
            || this.state == AuthState.RECOVER
            && to != AuthState.SUCCESS
        ) this.state = to;
    }

    public AuthState get(){ return this.state; }
}
