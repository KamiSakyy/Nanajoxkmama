package com.kamisakyy.nanajoxkmama;

import android.view.View;

/** A page rendered into the shell content area. */
public interface Screen {
    View view();
    void onShow();
    void onPlayerChanged();
}
