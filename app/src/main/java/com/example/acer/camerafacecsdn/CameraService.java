package com.example.acer.camerafacecsdn;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.support.annotation.Nullable;

public class CameraService extends Service {
    private static final String TAG = CameraService.class.getSimpleName();



    @Nullable
    @Override
    public IBinder onBind(Intent intent) {

        return null;
    }
}
