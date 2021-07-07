package com.example.bridgeme;

import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;
import android.provider.MediaStore;
import android.provider.OpenableColumns;

import org.json.JSONObject;

public class FileMeta {
    private Uri uri;
    private String name;
    private String type;
    private String uuid;
    private double upload_progress;
    private long size;
    private EventCallback fileRejectedCallback;

    FileMeta(Uri u, String n, String t, long s){
        uri = u;
        type = t;
        name = n;
        size = s;
    }

    String getUri(){
        return this.uri.toString();
    }
    String getName(){
        return this.name;
    }

    String getType(){
        return this.type;
    }
    long getSize(){
        return this.size;
    }

    void setUuid(String uuid){
        this.uuid = uuid;
    }

    String getUuid(){
        return uuid;
    }

    void setFileRejectedCallback(EventCallback ec){
        this.fileRejectedCallback = ec;
    }

    void rejectFile(String reason){
        try {
            if (fileRejectedCallback != null) {
                JSONObject obj = new JSONObject();
                obj.put("reason",reason);
                fileRejectedCallback.trigger(obj);
            }
        }catch (Exception e){e.printStackTrace();}
    }

    public static String resolveName(Uri uri, ContentResolver cr){
        //understand what's a projection
        String[] projection = {MediaStore.MediaColumns.DISPLAY_NAME};
        Cursor metaCursor = cr.query(uri, projection, null, null, null);
        if (metaCursor != null) {
            try {
                if (metaCursor.moveToFirst()) {
                    return metaCursor.getString(0);
                }
            } finally {
                metaCursor.close();
            }
        }

        return "unknown";
    }

    public static long resolveSize(Uri uri, ContentResolver cr){
        Cursor returnCursor =  cr.query(uri, null, null, null, null);
        int sizeIndex = returnCursor.getColumnIndex(OpenableColumns.SIZE);
        returnCursor.moveToFirst();
        long size = returnCursor.getLong(sizeIndex);
        returnCursor.close();
        return size;
    }

    public void setUploadProgress(double p){
        this.upload_progress = p;
    }
    public double getUploadProgress(){
        return this.upload_progress;
    }
}
