package com.mdcs.shared.models.bootstrap;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.mdcs.shared.models.network.Http.Request;
import com.mdcs.shared.models.network.Http.Response;

public class Network {

    /*
    'Response' class includes common fields for any response from server. We include 'Body' which
    changes per API.
    */

    public static class UpdReq extends Request<UpdReq.Body>{

        public static class App{
            public String current_version;
        }
        
        public static class Body{
            public App app = new App();
            public Map<String, String> plugins = new HashMap<>();
        }

        public UpdReq(){
            this.endpoint = "/version/check";
            this.addHeader("Content-type", "application/json");
        }
    }

    public static class UpdRes extends Response<UpdRes.Body>{

        public static class App{
            public String cur_ver;
            public String avail_ver;
            public boolean critical_update;
        }

        public static class Plugin{
            public String curr_ver;
            public String avail_ver;
            public boolean compatible;
            public boolean update_req;
        }

        public static class Body{
            public App app;

            // plugin name -> Plugin
            public Map<String, Plugin> plugins;
            
            public List<String> changes;
        }

        public Body body;
    }
}
