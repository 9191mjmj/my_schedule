package com.cug.schedule;

import android.os.Bundle;
import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        registerPlugin(JwxtPlugin.class);
        super.onCreate(savedInstanceState);
    }
}

