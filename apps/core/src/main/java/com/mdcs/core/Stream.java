package com.mdcs.core;

/*
 * Architecture note:
 *
 * This class is the single communication layer used by the Core module.
 *
 * The Core never performs console I/O directly and never knows whether it is running standalone
 * or as a child process. It simply sends and receives typed messages through this stream.
 *
 * Standalone mode: Stream <-> Console
 * Child process mode: Core <-> Stream <-> IPC Pipe <-> Manager
 *
 * The Manager interprets message Service types (LOG, AUTH, UPDATE, etc.) and decides how to
 * fulfill them. It may delegate to a CLI, GUI, or any other interface, but that decision is
 * completely outside the Core.
 *
 * Therefore, message types represent services/capabilities requested by the Core, not UI actions.
 * The Core only tells what it needs, while the Manager decides how to work on the request.
 *
 * This class is responsible only for serializing/deserializing the protocol, not for implementing
 * any business or UI logic.
 */

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.atomic.AtomicInteger;

/*
 * Stream logging structure: 
 *  Got inspirations from HTTP. Because, it is language independent, easy to parse, and more
 *  importantly, it scales well.
 * 
 *  This protocol separates transport metadata from process related information (payload) which
 *  makes receiving process stay completely unaware of intent of sender process.
 * 
 *  Request structure [format to send to manager]:
 *      <ID> <SERVICE> <ACTION>
 *      [Header-Count]
 *      <Key>: <Value>
 *      ...
 *      [Length]<Payload>
 * 
 *  Response structure [format expected to receive from manager]:
 *      <ID> <STATUS> <STATUS_CODE>
 *      [Header-Count]
 *      <Key>: <Value>
 *      ...
 *      [Length]<Payload>
 * 
 * Payload-Length specifies the number of bytes encoded in the payload, not number of characters.
 * A response with certain ID is expected to have a request with same ID, if not, it will be
 * ignored.
 */

/**
 * Includes methods to read and write into the buffer (IPC pipes / console)
 * 
 * When launched as a child process, writes / reads into / from the IPC pipe. And when launched
 * as an independent process, it writes or reads from console.
 * 
 * Defines different type of output streams which can be parsed by the parent.
 */

public class Stream {
    private BlockingDeque<Message> oque;
    private ConcurrentHashMap<Integer, CompletableFuture<Response>> promises;

    /**
     * Services exposed by the Manager/Core over the IPC protocol.
     * 
     * Each service tag corresponds to predefined set of actions possible. Any action that is not
     * in the domain of a service must be ignored.
     * 
     * Services and Actions are part of Stream only. Because, Stream is the one that serializes
     * and deserializes the messages.
     */
    public enum Service{
        AUTH,
        LOG,
        UPDATE,
    }

    /**
     * Every action must be associated with a service. This is to ensure that the parent process
     * can identify the service and delegate the action to the concerned sub-process.
     */
    public interface Action{ Service service(); }

    public enum AuthAct implements Action{
        LOGIN,
        REGISTER,
        AUTH_CHOICE,
        FIR_ENROLL,
        ADD_ENROLL,
        CHOICE_ENROLL,
        OTP,
        RETRY;

        @Override
        public Service service(){ return Service.AUTH; }
    }

    public enum LogAct implements Action{
        INFO,
        WARN,
        ERROR,
        CRITICAL;

        @Override
        public Service service(){ return Service.LOG; }
    }

    public enum UpdateAct implements Action{
        CRITICAL,
        OPTIONAL,
        PLUGIN;

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

        private int id;
        private String service;
        private String action;
        private Map<String, String> headers;
        private String payload;
        private String msg;

        String get() { return this.msg; }

        int getId() { return this.id; }
        
        private void build(){
            StringBuilder builder = new StringBuilder();

            // Request line
            builder.append(id)
                .append(" ")
                .append(service)
                .append(" ")
                .append(action)
                .append("\n");

            // Headers
            builder.append("[").append(headers.size()).append("]\n");

            for (Map.Entry<String, String> header : headers.entrySet()) {
                builder.append(header.getKey())
                    .append(": ")
                    .append(header.getValue())
                    .append("\n");
            }

            // Payload
            builder.append("[")
                .append(payload.getBytes().length)
                .append("]")
                .append(payload);

            /**
             * Trailing newline to indicate end of message. This is important for the reader to
             * know when the message ends, especially when the payload is empty.
             */
            builder.append("\n");

            this.msg = builder.toString();
        }

        public Message(
            Action action,
            Map<String, String> headers,
            String payload
        ){

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

    /**
     * Reads / watches the stdin pipe continuosly. Parses the message string based on the format
     * and completes the promise.
     * 
     * If there is no related promise pending, it simply ignores them. This is expected and not
     * any limitation, this is because, a response from Manager should follow a request from
     * manager.
    */
    private static class IPCReader implements Runnable {
        private final BufferedInputStream istream;
        private final ConcurrentHashMap<Integer, CompletableFuture<Response>> promises;

        private String readLine() throws IOException {
            StringBuilder builder = new StringBuilder();

            while (true) {
                int chr = this.istream.read();

                if (chr == -1) throw new EOFException();

                if (chr == '\n') return builder.toString();

                if (chr != '\r') builder.append((char) chr);
            }
        }

        private String readBytes(int length) throws IOException {
            StringBuilder builder = new StringBuilder(length);

            for (int i = 0; i < length; i++) {
                int chr = this.istream.read();

                if (chr == -1) throw new EOFException();

                builder.append((char) chr);
            }

            return builder.toString();
        }

        @Override
        public void run() {
            
            try {
                while (true) {

                    // <ID> <STATUS> <STATUS_CODE>
                    String statusline = readLine();

                    String[] status = statusline.split(" ", 3);

                    // [Header-Count]
                    int header_count = Integer.parseInt(
                        readLine()
                            .replace("[", "")
                            .replace("]", "")
                    );

                    // <Key>: <Value>
                    Map<String, String> headers = new HashMap<>();

                    for (int i = 0; i < header_count; i++) {
                        String header = readLine();
                        int separator = header.indexOf(':');

                        if (separator == -1)
                            throw new IOException("Invalid header: " + header);

                        String key = header.substring(0, separator).trim();
                        String value = header.substring(separator + 1).trim();

                        headers.put(key, value);
                    }

                    // [Length]<Payload>
                    String lenline = readLine();

                    int length = Integer.parseInt(
                        lenline.substring(1, lenline.indexOf(']'))
                    );

                    String payload = readBytes(length);

                    Response res = new Response(
                        Integer.parseInt(status[0]),
                        status[1],
                        Integer.parseInt(status[2]),
                        headers,
                        payload
                    );

                    CompletableFuture<Response> promise = this.promises.remove(res.getId());

                    if (promise != null) promise.complete(res);
                }

            } catch (IOException e) {
                // Will decide what to do here later.
            }
        }

        IPCReader(ConcurrentHashMap<Integer, CompletableFuture<Response>> promises) {
            this.istream = new BufferedInputStream(System.in);
            this.promises = promises;
        }
    }

    /**
     * Watches output queue to write (atomic) to stdout continuously.
    */
    private static class IPCWriter implements Runnable{
        private BlockingDeque<Message> oque;
        private BufferedOutputStream ostream;

        @Override
        public void run(){

            try{
                while (true){
                    Message msg = oque.take();

                    // write to stdout so parent/manager can read it
                    this.ostream.write(msg.get().getBytes(StandardCharsets.UTF_8));
                    ostream.flush();
                }

            } catch(InterruptedException e){
                Thread.currentThread().interrupt();
            } catch (IOException e) {
                // Will decide what to do here later.
            }
        }

        IPCWriter(BlockingDeque<Message> oque){
            this.oque = oque;
            this.ostream = new BufferedOutputStream(System.out);
        }
    }

    /**
     * This method is best for Fire-And-Forget kind of payloads. Best use cases are Logs, Updates,
     * and Notifications.
     */
    public void send(Message msg){

        try { this.oque.put(msg); }
        
        catch (InterruptedException e) {
            // Will decide what to do here later
        }
    }

    /**
     * Returns CompletableFuture for the request. Adds the request made by the sub process to the
     * queue provides promise for the response.
     * 
     * Best for requests which expect a response, eg., Auth.
     */
    public CompletableFuture<Response> request(Message msg)
    throws InterruptedException{
        CompletableFuture<Response> promise = new CompletableFuture<>();
        this.promises.put(msg.getId(), promise);
        this.oque.put(msg);

        return promise;
    }
    
    public Stream(){
        this.oque = new LinkedBlockingDeque<>();
        this.promises = new ConcurrentHashMap<>();

        Thread writer = new Thread(new IPCWriter(this.oque));
        writer.setDaemon(true);
        writer.start();
        
        Thread reader = new Thread(new IPCReader(this.promises));
        reader.setDaemon(true);
        reader.start();
    }
}
