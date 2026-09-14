package com.mdcs.shared.models.auth;

import com.mdcs.shared.models.network.Http.Request;
import com.mdcs.shared.models.network.Http.Response;

public class Network {
    
    public static class CreateUsrReq extends Request<CreateUsrReq.Body>{

        public static class Body{
            public String username;
            public String email;
            public String password;

            public Body(String uname, String email, String pswd){
                this.username = uname;
                this.email = email;
                this.password = pswd;
            }
        }

        public CreateUsrReq(){
            this.endpoint = "/auth/user/signup";
            this.addHeader("Content-type", "application/json");
        }
    }

    public static class CreateUsrRes extends Response<CreateUsrRes.Body>{
        
        public static class Body{
            public String user_id;
            public String username;
            public String email;
        }
    }

    public static class ValidateUsrReq extends Request<ValidateUsrReq.Body>{

        public static class Body{
            public String user_id;
            public String email;
            public String otp;

            public Body(String id, String email, String otp){
                this.user_id = id;
                this.email = email;
                this.otp = otp;
            }
        }

        public ValidateUsrReq(){
            this.endpoint = "/auth/user/verify-otp";
            this.addHeader("Content-type", "application/json");
        }
    }

    public static class ValidateUsrRes extends Response<ValidateUsrRes.Body>{

        public static class Body{
            public String auth_tok;
            public String refresh_tok;
        }
    }

    public static class EnrollFirReq extends Request<EnrollFirReq.Body>{

        public static class Body{
            public String device_name;
            public String workspace_name;

            public Body(String dname, String wname){
                this.device_name = dname;
                this.workspace_name = wname;
            }
        }

        public EnrollFirReq(){
            this.endpoint = "/auth/device/first-enroll";
            this.addHeader("Content-type", "application/json");
        }
    }

    public static class EnrollAddReq extends Request<EnrollAddReq.Body>{

        public static class Body{
            public String device_name;
            public String workspace_name;
            public String pairing_key;

            public Body(String dname, String wname, String pkey){
                this.device_name = dname;
                this.workspace_name = wname;
                this.pairing_key = pkey;
            }
        }

        public EnrollAddReq(){
            this.endpoint = "/auth/device/additional-enroll";
            this.addHeader("Content-type", "application/json");
        }
    }

    public static class EnrollRes extends Response<EnrollRes.Body>{

        public static class Body{
            public String device_id;
            public String device_name;
            public String workspace_id;
            public String workspace_name;
        }
    }

    public static class LoginReq extends Request<LoginReq.Body>{

        public static class Body{
            public String email;
            public String password;
            public String workspace_id;
            public String device_id;

            public Body(
                String email, 
                String password,
                String workspace_id, 
                String device_id
            ){
                this.email = email;
                this.password = password;
                this.workspace_id = workspace_id;
                this.device_id = device_id;
            }
        }

        public LoginReq(){
            this.endpoint = "/auth/user/login";
            this.addHeader("Content-type", "application/json");
        }
    }

    public static class LoginRes extends Response<LoginRes.Body>{

        public static class Body{
            public String phase;
            public String user_id;
            public String username;
            public String auth_tok;
            public String refresh_tok;
        }
    }
}
