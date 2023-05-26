/* Licensed under MIT 2023. */
package edu.kit.kastel.mcse.ardoco.docker;

import java.io.Closeable;
import java.io.IOException;
import java.util.concurrent.Semaphore;

import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.model.Frame;

public class ResultAwaitCallback implements ResultCallback<Frame> {
    private final Semaphore lock = new Semaphore(0);

    @Override
    public void onStart(Closeable closeable) {

    }

    @Override
    public void onNext(Frame object) {

    }

    @Override
    public void onError(Throwable throwable) {

    }

    @Override
    public void onComplete() {
        lock.release();
    }

    @Override
    public void close() throws IOException {

    }

    public void awaitCompletion() {
        lock.acquireUninterruptibly(1);
    }
}
