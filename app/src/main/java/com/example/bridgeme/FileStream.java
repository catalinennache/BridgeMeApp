package com.example.bridgeme;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.EventListener;
import java.util.function.Consumer;
import java.util.function.Function;

public class FileStream extends Thread {
    public long timetosleep = 10; //ms s
    private InputStream target_stream;
    public int chunk_size;
    private ChunkReadyCallback callback;
    private ExceptionCallback error_callback;
    private Consumer<FileStream> read_complete_callback;
    private Context ctx;
    private boolean inited = false;
    private FileMeta fileMeta;
    private boolean reading;
    private final Object lock;

    public FileStream(Context context, FileMeta fm, long tts, int chunk_size, ChunkReadyCallback clb, ExceptionCallback error_callback, Consumer<FileStream> read_complete_callback, Object uploadLock) {
        ctx = context;
        fileMeta = fm;
        Uri uri = Uri.parse(fm.getUri());
        callback = clb;
        this.chunk_size = chunk_size;
        this.error_callback = error_callback;
        lock = uploadLock;
        this.timetosleep = tts;
        try {

            target_stream = (ctx.getContentResolver().openInputStream(uri));
            inited = true;
        } catch (Exception e) {
            Log.d("FileStream", e.getLocalizedMessage());
            error_callback.execute(e.getMessage());
        }

        this.read_complete_callback = read_complete_callback;
    }

    public String echo(String value) {
        return value;
    }

    public FileMeta getFileMeta(){
        return this.fileMeta;
    }

    public boolean isReading(){return  this.reading; }

    public void pause_read() throws InterruptedException {
        synchronized (lock){
            reading = false;
            lock.wait();
        }
    }

    public void resume_read(){
        synchronized (lock){
            lock.notifyAll();
            reading = true;
        }
    }

    @Override
    public void run() {
        super.run();
        if (!inited)
            return;
        reading = true;
        try {
            synchronized (this.lock) {
                int read_bytes = 0;
                boolean done = false;
                byte[] arraybuffer = new byte[chunk_size];
                int status = -2;
                int chunk_number = 0;
                do {

                    status = target_stream.read(arraybuffer);
                    if (status == -1)
                        break;
                    if (status < chunk_size) {
                        byte[] temp = new byte[status];
                        for (int i = 0; i < status; i++) {
                            temp[i] = arraybuffer[i];
                        }
                        arraybuffer = temp;
                        done = true;
                        chunk_number++;
                        boolean result = callback.execute(arraybuffer, done, this.fileMeta.getUuid(), chunk_number);
                        break;
                    } else {
                        chunk_number++;
                        boolean result = callback.execute(arraybuffer, done, this.fileMeta.getUuid(), chunk_number);
                        arraybuffer = new byte[chunk_size];
                    }
                    Thread.sleep(timetosleep);
                    //Log.d("FILESTREAM","CHUNK PROCESSED "+chunk_number);
                } while (status != -1);

                target_stream.close();

            }
            try {
                read_complete_callback.accept(this);
            } catch (Exception e) {
                e.printStackTrace();
            }
        } catch (Exception e) {
            e.printStackTrace();
            error_callback.execute(e.getMessage());
        }

    }
}

//FileInputStream importdb = new FileInputStream(_context.getContentResolver().openFileDescriptor(uri, "r").getFileDescriptor());
