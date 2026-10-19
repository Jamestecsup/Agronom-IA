package com.agronomia.backend.ai;

import java.io.Closeable;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public final class ChatCancellation {
    private final AtomicBoolean cancelled = new AtomicBoolean();
    private final AtomicReference<Closeable> activeStream = new AtomicReference<>();

    public boolean isCancelled() { return cancelled.get(); }

    public void register(Closeable stream) {
        activeStream.set(stream);
        if (cancelled.get()) closeStream();
    }

    public void clear(Closeable stream) {
        activeStream.compareAndSet(stream, null);
    }

    public void cancel() {
        cancelled.set(true);
        closeStream();
    }

    private void closeStream() {
        Closeable stream = activeStream.getAndSet(null);
        if (stream != null) {
            try { stream.close(); } catch (IOException ignored) { }
        }
    }
}
