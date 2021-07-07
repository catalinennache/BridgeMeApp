package com.example.bridgeme;

import java.io.IOException;

public interface ChunkReadyCallback{

    boolean execute(byte[] arraybuffer, boolean done, String uuid, int chunk_number) throws IOException;
}