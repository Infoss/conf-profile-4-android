package com.getbase.android.db.loaders;

import android.content.Context;
import android.support.v4.content.AsyncTaskLoader;

/**
 * Base class for {@link android.support.v4.content.Loader} which caches
 * the results.
 */
public abstract class AbstractLoader<T> extends AsyncTaskLoader<T> {

  T mResult;

  public AbstractLoader(Context context) {
    super(context);
  }

  @Override
  public void deliverResult(T result) {
    if (isReset()) {
      releaseResources(result);
      return;
    }

    T oldResult = mResult;
    mResult = result;

    if (isStarted()) {
      super.deliverResult(result);
    }

    if (oldResult != result) {
      releaseResources(oldResult);
    }
  }

  @Override
  public void onCanceled(T result) {
    super.onCanceled(result);
    releaseResources(result);
  }

  @Override
  protected void onReset() {
    super.onReset();

    // Ensure the loader is stopped
    onStopLoading();

    releaseResources(mResult);
    mResult = null;
  }

  @Override
  protected void onStartLoading() {
    if (mResult != null) {
      deliverResult(mResult);
    }
    if (takeContentChanged() || mResult == null) {
      forceLoad();
    }
  }

  @Override
  protected void onStopLoading() {
    cancelLoad();
  }

  /**
   * Subclasses may implement this to take care of releasing resources held
   * by {@code result}.
   * This will always be called from the process's main thread.
   */
  protected void releaseResources(T result) {
  }
}