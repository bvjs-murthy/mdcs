package com.mdcs.core;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.atomic.AtomicInteger;

public class Stream {
    private BlockingDeque<Message> oque;
    private BlockingDeque<String> eque;
    private ConcurrentHashMap<Integer, CompletableFuture<Response>> promises;
    private IPC ipc;
    
    private static class IPC{
        private BlockingDeque<Message> oque;
        private BlockingDeque<String> eque;
        private ConcurrentHashMap<Integer, CompletableFuture<Response>> promises;
        private volatile boolean connected = true;

        /**
         * Reads/watches the stdin pipe continuosly. Parses the message string based on the format
         * and completes the promise.
        */
        private static class In implements Runnable {
            private final BufferedInputStream istream;
            private final ConcurrentHashMap<Integer, CompletableFuture<Response>> promises;
            private IPC ipc;

            private String readLine() throws IOException {
                StringBuilder builder = new StringBuilder();

                while (true) {
                    int chr = this.istream.read();

                    if (chr == -1) throw new EOFException();
                    if (chr == '\n') return builder.toString();
                    if (chr != '\r') builder.append((char) chr);
                }
            }

            private String readBytes(int len)
            throws EOFException, IOException {
                byte[] bytes = this.istream.readNBytes(len);

                if (bytes.length != len) throw new EOFException();

                return new String(bytes, StandardCharsets.UTF_8);
            }

            @Override
            public void run() {
                
                try {
                    while (true) {
                        String statln = readLine();
                        String[] status = statln.split(" ", 3);

                        int headc = Integer.parseInt(
                            readLine()
                                .replace("[", "")
                                .replace("]", "")
                        );

                        Map<String, String> heads = new HashMap<>();

                        for (int i = 0; i < headc; i++) {
                            String head = readLine();
                            int separator = head.indexOf(':');

                            if (separator == -1) throw new IOException();

                            String key = head.substring(0, separator).trim();
                            String value = head.substring(separator + 1).trim();

                            heads.put(key, value);
                        }

                        String lenline = readLine();

                        int len = Integer.parseInt(
                            lenline.substring(1, lenline.indexOf(']'))
                        );

                        String payload = readBytes(len);

                        Response res = new Response(
                            Integer.parseInt(status[0]),
                            status[1],
                            Integer.parseInt(status[2]),
                            heads,
                            payload
                        );

                        CompletableFuture<Response> promise = this.promises.remove(res.getId());

                        if (promise != null) promise.complete(res);
                    }

                } catch (NumberFormatException | IOException e) {
                    /**
                     * This exception could have been occured due to either parsing of the response
                     * or due to pipeline being closed (EOFException) else, invalid integer format.
                     * 
                     * In any of the cases, they are protocol level issues. And the process bound
                     * to the requests can't proceed further safely. So mark them complete
                     * exceptionally and clear the promises map. The parent process is expected to
                     * handle this
                     */

                    this.ipc.fail();
                }
            }

            In(ConcurrentHashMap<Integer, CompletableFuture<Response>> promises, IPC ipc) {
                this.istream = new BufferedInputStream(System.in);
                this.promises = promises;
                this.ipc = ipc;
            }
        }

        /**
         * Watches output queue to write (atomic) to stdout continuously.
         */
        private static class Out implements Runnable{
            private BlockingDeque<Message> oque;
            private BufferedOutputStream ostream;
            private IPC ipc;

            @Override
            public void run(){

                try{
                    while (true){
                        Message msg = oque.take();

                        this.ostream.write(msg.get().getBytes(StandardCharsets.UTF_8));
                        ostream.flush();
                    }

                } catch(InterruptedException e){
                    this.ipc.fail();
                    Thread.currentThread().interrupt();
                } catch (IOException e) {
                    /**
                     * This means, the writer pipe failed. In such cases, we can't write the stdout.
                     * So we should exit this thread. Other processes are expected to acknowledge
                     * this and stop sending messages.
                     */

                    this.ipc.fail();
                }
            }

            Out(BlockingDeque<Message> oque, IPC ipc){
                this.oque = oque;
                this.ostream = new BufferedOutputStream(System.out);
                this.ipc = ipc;
            }
        }

        private static class Err implements Runnable{
            private BlockingDeque<String> eque;
            private BufferedOutputStream estream;
            private IPC ipc;

            @Override
            public void run(){

                try{

                    while (true){
                        String msg = eque.take();

                        this.estream.write(msg.getBytes(StandardCharsets.UTF_8));
                        estream.flush();
                    }
                } catch(InterruptedException e){
                    this.ipc.fail();
                    Thread.currentThread().interrupt();
                } catch (IOException e) {
                    // This shouldn't fail the IPC, as it only means the path that leads to stderr
                    // is closed. NOt the stdin or stdout. That can be managed by In, Out.

                    Thread.currentThread().interrupt();
                }
            }

            Err(BlockingDeque<String> eque, IPC ipc){
                this.eque = eque;
                this.estream = new BufferedOutputStream(System.err);
                this.ipc = ipc;
            }
        }

        private void fail(){

            if (!this.connected) return;

            this.connected = false;

            for (CompletableFuture<Response> promise : this.promises.values())
                promise.completeExceptionally(new IOException("IPC connection failed"));

            this.promises.clear();
            this.oque.clear();
            this.eque.clear();
        }

        private boolean status(){ return this.connected; }

        private IPC(
            BlockingDeque<Message> oque,
            BlockingDeque<String> eque,
            ConcurrentHashMap<Integer, CompletableFuture<Response>> promises
        ){
            this.oque = oque;
            this.eque = eque;
            this.promises = promises;
        }
    }

    /**
     * Services or functionalities Core expects Manager to be capable of directly or from others.
     * 
     * Each service tag corresponds to predefined set of actions possible. Any action that is not
     * in the domain of a service must be ignored.
     */
    public enum Service{
        AUTH, UPDATE
    }

    public interface Action{ Service service(); }

    public enum AuthAct implements Action{
        LOGIN, REGISTER, AUTH_CHOICE, FIR_ENROLL, ADD_ENROLL, CHOICE_ENROLL, OTP, RETRY;

        @Override
        public Service service(){ return Service.AUTH; }
    }

    public enum UpdateAct implements Action{
        CRITICAL, OPTIONAL, PLUGIN;

        @Override
        public Service service(){ return Service.UPDATE; }
    }

    /**
     * Temporarily stores the parsed message as received from the IPC stdin, before utilised by
     * the concerned sub-process.
     */
    public static class Response{
        private final int id;
        private final String status;
        private final int status_code;
        private final Map<String, String> headers;
        private final String payload;

        public int getId(){ return this.id; }
        
        public String getStatus(){ return this.status; }

        public int getStatusCode(){ return this.status_code; }

        public Map<String, String> getHeaders(){ return this.headers; }
        
        public String getPayload(){ return this.payload; }

        public Response(
            int id,
            String status,
            int st_code,
            Map<String, String> headers,
            String payload
        ){
            this.id = id;
            this.status = status;
            this.status_code = st_code;
            this.headers = headers != null ? headers : new HashMap<>();
            this.payload = payload;
        }
    }

    /**
     * Builds the stream message which could be sent to an IPC stdout.
    */
    public static class Message {
        private static AtomicInteger id_count = new AtomicInteger();
        private final int id;
        private final String service;
        private final String action;
        private final Map<String, String> headers;
        private final String payload;
        private String msg;

        String get() { return this.msg; }

        int getId() { return this.id; }
        
        private void build(){
            StringBuilder builder = new StringBuilder();

            builder.append(this.id)
                .append(" ")
                .append(this.service)
                .append(" ")
                .append(this.action)
                .append("\n");

            builder.append("[").append(headers.size()).append("]\n");

            for (Map.Entry<String, String> header : headers.entrySet()) {
                builder.append(header.getKey())
                    .append(": ")
                    .append(header.getValue())
                    .append("\n");
            }

            builder.append("[")
                .append(this.payload.getBytes(StandardCharsets.UTF_8).length)
                .append("]\n")
                .append(this.payload);

            this.msg = builder.toString();
        }

        public Message(
            Action action,
            Map<String, String> headers,
            String payload
        ) throws IllegalArgumentException {

            if (action == null)
                throw new IllegalArgumentException("Action/Service cannot be null");

            this.id = id_count.incrementAndGet();
            this.service = action.service().name();
            this.action = action.toString();
            this.headers = headers != null ? headers : new HashMap<>();
            this.payload = payload;

            this.build();
        }
    }

    public static class Log{
        private static final DateTimeFormatter F = DateTimeFormatter.ofPattern(
            "yyyy-MM-dd HH:mm:ss.SSS"
        ).withZone(ZoneId.systemDefault());

        private final long timestamp = System.currentTimeMillis();
        private String level;
        private final String producer;
        private String msg;

        public String get(){
            String ts = F.format(Instant.ofEpochMilli(this.timestamp));

            return "[ " + ts + " ][ " + this.level + " ] " + this.producer + ": " + this.msg;
        }

        public Log(Object producer, String level, String msg)
        throws IllegalArgumentException {

            if (producer == null)
                throw new IllegalArgumentException("Producer cannot be null");

            if (level == null || level.isBlank())
                throw new IllegalArgumentException("Log level cannot be null or empty");

            this.producer = producer.getClass().getSimpleName().toUpperCase();
            this.level = level.toUpperCase();
            this.msg = msg;
        }
    }

    /**
     * This method is best for Fire-And-Forget kind of payloads. Best use cases are Updates,
     * Notifications etc.
     */
    public void send(Message msg){

        if (!this.ipc.status())
            throw new IllegalStateException("IPC connection failed");

        try { this.oque.put(msg); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }

    /**
     * Returns CompletableFuture for the request. Adds the request made by the sub process to the
     * queue provides promise for the response.
     * 
     * Best for requests which expect a response, eg., Auth.
     */
    public CompletableFuture<Response> request(Message msg)
    throws InterruptedException{

        if (!this.ipc.status())
            throw new IllegalStateException("IPC connection failed");

        CompletableFuture<Response> promise = new CompletableFuture<>();
        this.promises.put(msg.getId(), promise);
        
        try { this.oque.put(msg); }
        catch (InterruptedException e) {
            this.promises.remove(msg.getId());
            Thread.currentThread().interrupt();
            throw e;
        }

        return promise;
    }

    public void log(Object producer, String level, String msg){

        try {
            this.eque.put(new Log(producer, level, msg).get());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
    
    public Stream(){
        this.oque = new LinkedBlockingDeque<>();
        this.eque = new LinkedBlockingDeque<>();
        this.promises = new ConcurrentHashMap<>();
        this.ipc = new IPC(this.oque, this.eque, this.promises);

        Thread stdout = new Thread(new IPC.Out(this.oque, this.ipc));
        stdout.setDaemon(true);
        stdout.start();
        
        Thread stdin = new Thread(new IPC.In(this.promises, this.ipc));
        stdin.setDaemon(true);
        stdin.start();

        Thread stderr = new Thread(new IPC.Err(this.eque, this.ipc));
        stderr.setDaemon(true);
        stderr.start();
    }
}
