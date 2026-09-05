package com.creditwise.app;

import android.app.Application;

import com.creditwise.app.data.local.ThemeStore;
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader;

/** Application entry point. Initialises the on-device PDF library and the saved theme. */
public class App extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        ThemeStore.applyStartup(this);
        PDFBoxResourceLoader.init(getApplicationContext());
    }
}
