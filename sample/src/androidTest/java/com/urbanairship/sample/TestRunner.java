package com.urbanairship.sample;

import android.app.Application;
import android.content.Context;

import com.urbanairship.Autopilot;

import androidx.test.runner.AndroidJUnitRunner;

public class TestRunner extends AndroidJUnitRunner {

    @Override
    public Application newApplication(ClassLoader cl, String className, Context context) throws ClassNotFoundException, IllegalAccessException, InstantiationException {
        Autopilot.setAutopilotInstance(new TestAutopilot());
        return super.newApplication(cl, className, context);
    }

}