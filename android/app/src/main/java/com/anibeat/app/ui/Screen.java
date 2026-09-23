package com.anibeat.app.ui;

import android.view.View;

/** Экран приложения: своя вьюха + хуки жизненного цикла. */
public interface Screen {

    View view();

    default void onShow() {
    }

    default void onHide() {
    }

    default String title() {
        return "";
    }

    default void release() {
    }

    /** true — экран сам обработал возврат. */
    default boolean onBack() {
        return false;
    }
}
