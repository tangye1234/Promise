package me.tangye.utils.async.resolver;


import me.tangye.utils.async.Promise;

public abstract class FinalResolver<D> implements DirectResolver<D,D> {

    @Override
    public final D resolve(D newValue) {
        onFinal(true);
        return newValue;
    }

    @Override
    public final D reject(Exception exception) {
        onFinal(false);
        Promise.throwException(exception);
        return null;
    }

    public abstract void onFinal(boolean success);
}
