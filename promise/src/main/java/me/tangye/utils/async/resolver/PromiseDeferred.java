package me.tangye.utils.async.resolver;

import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.atomic.AtomicBoolean;

import me.tangye.utils.async.Promise;

/**
 * Created by coffee3689 on 16/8/23.
 * 通过制造一个Deferred对象，创建一个Promise.Locker，同事派生Promise
 */
public class PromiseDeferred<T> extends Promise.Locker<T> {
    private final AtomicBoolean done = new AtomicBoolean(false);
    private final Handler handler;
    private final Promise<T> internalPromise;
    private Promise.Locker<T> internalLocker;

    public static <D> PromiseDeferred<D> make(Looper looper) {
        return new PromiseDeferred<>();
    }

    public static <D> PromiseDeferred<D> make() {
        return make(Looper.myLooper());
    }

    private PromiseDeferred(Looper looper) {
        handler = new Handler(looper);
        internalPromise = Promise.make(new Promise.DirectFunction<T>() {
            @Override
            public void run(Promise.Locker<T> locker) {
                internalLocker = locker;
            }
        }, looper);
    }

    private PromiseDeferred() {
        this(Looper.myLooper());
    }

    public Promise<T> promise() {
        return internalPromise.clone();
    }

    @Override
    public boolean done() {
        return done.get();
    }

    @Override
    public Void resolve(final T result) {
        if (done.compareAndSet(false, true)) {
            Runnable r = new Runnable() {
                @Override
                public void run() {
                    internalLocker.resolve(result);
                }
            };
            Promise.runForHandler(r, handler);
        }
        return null;
    }

    @Override
    public Void reject(final Exception exception) {
        if (done.compareAndSet(false, true)) {
            Runnable r = new Runnable() {
                @Override
                public void run() {
                    internalLocker.reject(exception);
                }
            };
            Promise.runForHandler(r, handler);
        }
        return null;
    }

    @Override
    public void post(Runnable runnable) {
        if (!done.get()) {
            handler.post(runnable);
        }
    }

    @Override
    public void postDelayed(Runnable runnable, long delay) {
        if (!done.get()) {
            handler.postDelayed(runnable, delay);
        }
    }

    @Override
    public void removeCallbacks(Runnable runnable) {
        handler.removeCallbacks(runnable);
    }
}
