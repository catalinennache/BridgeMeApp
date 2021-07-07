package com.example.bridgeme;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.util.ArrayList;

public class FileMetaAdapter extends RecyclerView.Adapter<FileMetaAdapter.FileMetaViewHolder> {
    private ArrayList<FileMeta> mExampleList;
    public static class FileMetaViewHolder extends RecyclerView.ViewHolder {

        public TextView mTextView1;
        public TextView mTextView2;
        public ProgressBar progress;
        public FileMetaViewHolder(View itemView) {
            super(itemView);
            progress = itemView.findViewById(R.id.progressBar);
            mTextView1 = itemView.findViewById(R.id.filename);
            mTextView2 = itemView.findViewById(R.id.filesize);
        }
    }
    public FileMetaAdapter(ArrayList<FileMeta> exampleList) {
        mExampleList = exampleList;
    }
    @Override
    public FileMetaViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.cardviewfile, parent, false);
        FileMetaViewHolder evh = new FileMetaViewHolder(v);
        return evh;
    }
    @Override
    public void onBindViewHolder(FileMetaViewHolder holder, int position) {
        FileMeta currentItem = mExampleList.get(position);

        holder.mTextView1.setText("Name: "+currentItem.getName());
        holder.mTextView2.setText("Size: "+currentItem.getSize());
        holder.progress.setProgress(Double.valueOf(currentItem.getUploadProgress()).intValue());
    }
    @Override
    public int getItemCount() {
        return mExampleList.size();
    }
}