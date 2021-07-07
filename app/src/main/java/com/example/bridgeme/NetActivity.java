package com.example.bridgeme;

import android.content.Context;
import android.util.Base64;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.DataOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.PriorityQueue;
import java.util.Queue;

import io.socket.client.IO;
import io.socket.client.Socket;


public class NetActivity {

    private Socket mWebSocketClient;
    private String S_ADDR;
    private HashMap<String, ArrayList<EventCallback>> callbackRegistry;
    private Context ctx;
    private ArrayList<FileMeta> unlabeledFiles = new ArrayList<>();
    private HashMap<String, FileStream> fileMap = new HashMap<>();
    private String token;
    private boolean supportsParallelTransfer = false;
    private final Object uploadLock;

    NetActivity(String S_ADDR, Context c) {
        this.S_ADDR = S_ADDR;
        ctx = c;
        this.callbackRegistry = new HashMap<>();
        this.uploadLock = new Object();

    }

    public Object getUploadLock(){return this.uploadLock;}

    public void setSupportsParallelTransfer(boolean pt){
        this.supportsParallelTransfer = pt;
    }

    public void connect(String token) {
        Log.d("NETACTIVITY", "Connecting");
        try {
            mWebSocketClient = IO.socket(S_ADDR);
            mWebSocketClient.on("connect", (final Object... args) -> {
                try {
                    Log.d("NETACTIVITY", "connected");

                    JSONObject jsonObject = new JSONObject();
                    jsonObject.put("token", token);
                    mWebSocketClient.emit("APP_INIT", jsonObject.toString());
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
            mWebSocketClient.on("message", this::onMessage);
            mWebSocketClient.on("UPLOAD_REQUEST_RESPONSE", this::onUploadRequestResponse);
            mWebSocketClient.on("PAUSE_UPLOAD", this::pauseUpload);
            mWebSocketClient.on("RESUME_UPLOAD", this::resumeUpload);
            mWebSocketClient.on("CONFIG_UPDATE", this::updateConfigs);
            mWebSocketClient.connect();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    public void setEventCallback(String ev, EventCallback ec) {
        ArrayList<EventCallback> callbacks = this.callbackRegistry.getOrDefault(ev, new ArrayList<>());
        callbacks.add(ec);
        this.callbackRegistry.put(ev, callbacks);
    }

    private void onMessage(final Object... args) {
        Log.d("NETACTIVITY", "MESSAGE RCVD");
        JSONObject data = (JSONObject) args[0];
        try {
            String event_type = (String) data.get("event");
            Log.d("NETACTIVITY", " " + event_type);
            if (event_type != null) {

                ArrayList<EventCallback> callbacks = this.callbackRegistry.get(event_type);
                if (callbacks != null) {
                    callbacks.forEach(callback -> {
                        try {
                            callback.trigger(data);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    });
                } else
                    Log.d("NETACTIVITY", "ONMESSAGE CALLBACKS IS NULL");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void emitJsonToServer(String event, JSONObject obj) throws JSONException {
        if (obj == null || event == null)
            return;

        this.mWebSocketClient.emit(event, obj.toString());
    }

    public void emitJsonToPeer(String event, JSONObject obj) throws JSONException {
        if (obj == null || event == null)
            return;

        obj.put("event", event);
        this.mWebSocketClient.emit("message", obj.toString());
    }

    public boolean uploadDataChunk(byte[] chunk, boolean last_chunk, String uuid, int chunk_number) throws IOException {
        DataOutputStream dos = null;
        String lineEnd = "\r\n";
        String twoHyphens = "--";
        String boundary = "*****";
        String name = (uuid + "___" + chunk_number);
        int bytesRead, bytesAvailable, bufferSize;
        // open a URL connection to the Servlet
        URL url = new URL("http://192.168.1.151:3000/uploadchunk");

        // Open a HTTP  connection to  the URL
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        Log.d("UPLOADCHNK","connection oppened");
        conn.setDoInput(true); // Allow Inputs
        conn.setDoOutput(true); // Allow Outputs
        conn.setUseCaches(false); // Don't use a Cached Copy
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Authorization", uuid);
        conn.setRequestProperty("Connection", "Keep-Alive");
        conn.setRequestProperty("ENCTYPE", "multipart/form-data");
        conn.setRequestProperty("Content-Type", "multipart/form-data;boundary=" + boundary);
        conn.setRequestProperty("uploaded_file", name);

        dos = new DataOutputStream(conn.getOutputStream());

        dos.writeBytes(twoHyphens + boundary + lineEnd);
        dos.writeBytes("Content-Disposition: form-data; name=" + name + ";filename=\""
                + name + "\"" + lineEnd);

        dos.writeBytes(lineEnd);

        dos.write(chunk, 0, chunk.length);


        // send multipart form data necesssary after file data...
        dos.writeBytes(lineEnd);
        dos.writeBytes(twoHyphens + boundary + twoHyphens + lineEnd);

        Log.d("UPLOADCHUNK","AWAITING SERVER RESPONSE");
        // Responses from the server (code and message)
        int serverResponseCode = conn.getResponseCode();
        Log.d("UPLOADCHUNK","response code received");

        String serverResponseMessage = conn.getResponseMessage();

        Log.d("UPLOADCHUNK", "HTTP Response is : "
                + serverResponseMessage + ": " + serverResponseCode);

        dos.flush();
        dos.close();
        if(serverResponseCode == 200) {
            try {
                JSONObject jso = new JSONObject();
                jso.put("uuid", uuid);
                jso.put("chunk_number", chunk_number);
                jso.put("last_chunk", last_chunk);
                this.emitJsonToPeer("CHUNK_UPLOAD", jso);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        return serverResponseCode == 200;


    }



    public boolean emitDataChunk(byte[] chunk, boolean last_chunk, String uuid, int chunk_number) {
        try {
            JSONObject payload = new JSONObject();
            String chunk_str = Base64.encodeToString(chunk, Base64.DEFAULT);
            payload.put("chunk", chunk_str.replace("==\n", "=="));
            payload.put("done", last_chunk);
            payload.put("uuid", uuid);
            payload.put("chunk_number", chunk_number);
            this.emitJsonToPeer("CHUNK", payload);

            if (last_chunk) {
                ArrayList<EventCallback> ecs = this.callbackRegistry.get("FILE_UPLOADED");
                if (ecs != null)
                    ecs.forEach(callback -> {
                        try {
                            JSONObject obj = new JSONObject();
                            obj.put("file", this.fileMap.get(uuid));
                            callback.trigger(obj);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    });
            }
            return true;
        } catch (JSONException e) {
            e.printStackTrace();
            return false;
        }
    }

    public void setNewConfig(long tts, int chunk_size, boolean pt) {
        this.fileMap.forEach((k, v) -> {
            v.timetosleep = tts;
            v.chunk_size = chunk_size;
        });

        this.supportsParallelTransfer = pt;

    }

    public boolean supportsParallelTransfer(){return this.supportsParallelTransfer;}

    public void addFutureUpload(FileMeta fm) {
        this.unlabeledFiles.add(fm);
    }

    public void sendUploadRequest() {
        try {
            JSONObject wrapper = new JSONObject();
            JSONArray metas = new JSONArray();
            this.unlabeledFiles.forEach(meta -> {
                try {
                    JSONObject meta_json = new JSONObject();
                    meta_json.put("name", meta.getName());
                    meta_json.put("size", meta.getSize());
                    meta_json.put("type", meta.getType());
                    metas.put(meta_json);
                } catch (Exception e) {
                }

            });

            wrapper.put("metas", metas);
            this.emitJsonToServer("UPLOAD_REQUEST", wrapper);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void onUploadRequestResponse(final Object... args) {
        Log.d("NETACTIVITY","UPLOAD_REQ_RESPONSE");
        JSONObject data = (JSONObject) args[0];
        try {
            JSONArray uuids = (JSONArray) data.get("uuids");
            int tts = data.getInt("tts");
            int chunk_size = data.getInt("chunk_size");

            for (int i = 0; i < uuids.length(); i++) {
                String uuid = uuids.getString(i);
                FileMeta fileMeta = this.unlabeledFiles.get(i);
                if (uuid.equals("null")) {
                    rejectFile(fileMeta, "File is fucked up");
                    continue;
                }

                fileMeta.setUuid(uuid);
                FileStream fileStream = new FileStream(this.ctx, fileMeta, tts, chunk_size, this::uploadDataChunk, (err) -> {Log.d("FILESTREAM",err);},this::onFileResourceDepleted,this.uploadLock);
                this.fileMap.put(uuid, fileStream);
            }
            this.unlabeledFiles.clear();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }



    public void onFileResourceDepleted(FileStream file){
        synchronized (this.uploadLock) {
            this.uploadLock.notify();
        }
    }



    public void startUpload(String uuid) throws Exception {
        FileStream fs = this.fileMap.get(uuid);
        if (fs != null) {
            try {
                fs.start();
            }catch (IllegalThreadStateException e){
                throw new Exception("File is already being transfered or has already been transferred");
            }
        }
    }

    private void rejectFile(FileMeta fm, String reason) {
        try {
            JSONObject json = new JSONObject();
            json.put("rejected_file", fm);
            json.put("reason", reason);
            this.callbackRegistry.get("FILE_REJECTED").forEach(ev -> {
                try {
                    ev.trigger(json);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void pauseUpload(final Object... args) {
        try {
            JSONObject data = (JSONObject) args[0];
            String uuid = data.getString("uuid");
            this.fileMap.get(uuid).pause_read();
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    public void resumeUpload(final Object... args) {
        try {
            JSONObject data = (JSONObject) args[0];
            String uuid = data.getString("uuid");
            this.fileMap.get(uuid).resume_read();
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    private void updateConfigs(final Object... args) {
        try {
            JSONObject data = (JSONObject) args[0];


        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    public void connectWebSocket() throws InterruptedException {
        URI uri;
        try {
            uri = new URI("ws://192.168.1.11:3000/");
            mWebSocketClient = IO.socket("http://192.168.1.11:3000/");
            mWebSocketClient.on("connect", (final Object... args) -> {
                Log.d("Connected", "test");
                JSONObject obj = new JSONObject();
                try {
                    obj.put("token", "test");
                    obj.put("event", "appinit");
                    mWebSocketClient.send(obj.toString());
                    Log.d("WEBSOCKET ", "init message sent");
                } catch (JSONException e) {
                    e.printStackTrace();
                }

                mWebSocketClient.emit("appinit", obj.toString());
            });

            mWebSocketClient.on("message", (final Object... args) -> {
                JSONObject data = (JSONObject) args[0];
                try {
                    Log.d("WEBSOCKET EVENT", (String) data.get("event"));
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            });

            mWebSocketClient.connect();
        } catch (URISyntaxException e) {
            e.printStackTrace();
            return;
        }

       /* {
            @Override
            public void onOpen(ServerHandshake serverHandshake) {
                Log.i("Websocket", "Opened");
                JSONObject obj = new JSONObject();
                try {
                    obj.put("token","test");
                    obj.put("event","appinit");
                    mWebSocketClient.send(obj.toString());
                    Log.d("WEBSOCKET ","init message sent");
                } catch (JSONException e) {
                    e.printStackTrace();
                }
               // mWebSocketClient.send("Hello from " + Build.MANUFACTURER + " " + Build.MODEL);
            }

            @Override
            public void onMessage(String s) {
                final String message = s;
                Log.d("WEBSOCKET MESSAGE",message);
            }

            @Override
            public void onClose(int i, String s, boolean b) {
                Log.i("Websocket", "Closed " + s);
            }

            @Override
            public void onError(Exception e) {
                Log.i("Websocket", "Error " + e.getMessage());
            }*/

        //  Log.d("result",""+mWebSocketClient.connectBlocking());
    }
}
