package com.example.bridgeme;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.content.ClipData;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;

public class MainActivity extends AppCompatActivity {


    private ArrayList<FileMeta> fileMetas = new ArrayList<>();
    private Thread netThread;
    private RecyclerView recyclerView;
    private FileMetaAdapter mAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);


        Intent intent = getIntent();
        String action = intent.getAction();
        String type = intent.getType();


        if (Intent.ACTION_SEND.equals(action) || Intent.ACTION_SEND_MULTIPLE.equals(action) && type != null) {
            //
                findViewById(R.id.sendfiles).setVisibility(View.GONE);
                findViewById(R.id.receivefiles).setVisibility(View.GONE);
                findViewById(R.id.recyclerView).setVisibility(View.VISIBLE);
            //
            ClipData cd = intent.getClipData();
            int files_shared_nr = cd.getItemCount();
            for(int i = 0; i<files_shared_nr; i++) {
                ClipData.Item cdi = cd.getItemAt(i);
                Uri uri = cdi.getUri();
                String mime = cd.getDescription().getMimeType(0);
                String name = "unknown";
                long size = 0;
                try {
                    Log.d("TARGET_URI", (new URI(uri.toString().replace("content:", "file:/"))).toString());
                    name = FileMeta.resolveName(uri, getContentResolver());
                    size = FileMeta.resolveSize(uri, getContentResolver());

                } catch (URISyntaxException e) {
                    e.printStackTrace();
                }

                FileMeta fm = new FileMeta(uri, name, mime, size);
                fileMetas.add(fm);
            }

            recyclerView = findViewById(R.id.recyclerView);
            recyclerView.setHasFixedSize(true);
            LinearLayoutManager mLayoutManager = new LinearLayoutManager(this);
            mAdapter = new FileMetaAdapter(fileMetas);
            recyclerView.setLayoutManager(mLayoutManager);
            recyclerView.setAdapter(mAdapter);

            Button btn = (Button)findViewById(R.id.trigger_qr);
            btn.setVisibility(View.VISIBLE);

            Log.d("ACTION_SEND ", intent.getDataString()+" ");


            if ("text/plain".equals(type)) {
                //handleSendText(intent); // Handle text being sent
            } else if (type.startsWith("image/")) {
               // handleSendImage(intent); // Handle single image being sent
            }

        } else if (Intent.ACTION_SEND_MULTIPLE.equals(action) && type != null) {

            if (type.startsWith("image/")) {
               //  handleSendMultipleImages(intent); // Handle multiple images being sent
            }
        } else {
            // Handle other intents, such as being started from the home screen
        }


    }


    public void onClick(View view){
        startActivityForResult(new Intent("com.example.bridgeme.QrScan"), 1);
    }

    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        Log.d("TEST","CALLBACK TRIGGERED");
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == 1) {
            Log.d("callback intent",""+resultCode);
            if (resultCode == 66) {
                String token = data.getStringExtra("qr");
                if(token != null && token.length() > 0){
                    triggerNetActivity(token);
                    Button btn = (Button)findViewById(R.id.trigger_qr);
                    btn.setVisibility(View.GONE);
                }else{
                    Log.d("CALLBACK", "qr problem "+token);
                }
            }
        }


    }

    private void triggerNetActivity(String token){

       netThread =  new Thread(()-> {
            NetActivity net_activity = new NetActivity("http://192.168.1.151:3000/", getApplicationContext());

            //replace with queUpload
            //add init config req
            this.fileMetas.forEach(net_activity::addFutureUpload);

            net_activity.setEventCallback("APP_INITED", (data)->{
                Log.d("NETACTIVITY","APP_INITED TRIGGERED");
                    try {
                        boolean pt = data.getBoolean("parallel_transfer");
                        net_activity.setSupportsParallelTransfer(pt);
                    }catch (Exception e){
                        e.printStackTrace();
                    }
                net_activity.sendUploadRequest();
            });

            net_activity.setEventCallback("STREAM_REQUEST",(data)->{

                    new Thread(()-> {
                        try {
                            String upload_uid = (String) data.get("uuid");

                            if (net_activity.supportsParallelTransfer()) {
                                net_activity.startUpload(upload_uid);
                            } else {
                                synchronized (net_activity.getUploadLock()) {
                                 net_activity.startUpload(upload_uid);
                                 //net_activity.getUploadLock().wait(); //do we really need this?
                                }
                            }
                        }catch (Exception e){e.printStackTrace();}

                    }).start();
                    //net_activity.queUpload(upload_uid);
            });

            net_activity.setEventCallback("UPLOAD_PROGRESS", data -> {
                try{
                    String uuid = (String) data.get("uuid");
                    double progress = data.getDouble("percentage_uploaded");
                    AtomicInteger i = new AtomicInteger();
                    fileMetas.forEach(fm->{
                        i.getAndIncrement();
                        if(fm.getUuid().equals(uuid)){
                            Log.d("PROGRESS",i.toString());
                            fm.setUploadProgress(progress);
                        }
                    });
                    runOnUiThread(new Runnable() {

                        @Override
                        public void run() {
                            mAdapter.notifyDataSetChanged();
                        }
                    });
                    //do something in GUI with this

                }catch (Exception e){e.printStackTrace();}
            });

            net_activity.setEventCallback("FILE_TRANSFERED", (data)->{
                try {
                    String uuid = (String) data.get("uuid");
                    fileMetas.forEach(fm->{
                        
                    });
                    //do whatever the fuck you want with this information

                }catch (Exception e){e.printStackTrace();}
            });

            net_activity.setEventCallback("CONFIG_UPDATE",(data)->{
                try {
                    boolean pt = data.getBoolean("parallel_transfer");
                    long tts = data.getLong("tts");
                    int chunk_s = data.getInt("chunk_size");

                    net_activity.setNewConfig(tts,chunk_s,pt);

                }catch (Exception e){
                    e.printStackTrace();
                }
            });


            net_activity.connect(token);


        });

       netThread.start();
       Log.d("NETACTIVITY", "Triggered");
    }
}