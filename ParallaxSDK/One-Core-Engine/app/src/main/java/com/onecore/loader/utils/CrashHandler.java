package com.onecore.loader.utils;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Process;
import android.util.Log;

import com.onecore.loader.activity.CrashActivity;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Calendar;

public class CrashHandler implements Thread.UncaughtExceptionHandler {

  private final String newLine = "\n";
  private final StringBuilder errorMessage = new StringBuilder();
  private final StringBuilder softwareInfo = new StringBuilder();
  private final StringBuilder dateInfo = new StringBuilder();
  private final Context context;

  public CrashHandler(Context context) {
    this.context = context;
  }

  @Override
  public void uncaughtException(Thread thread, Throwable exception) {
    try {
      StringWriter stackTrace = new StringWriter();
      exception.printStackTrace(new PrintWriter(stackTrace));
      errorMessage.append(stackTrace);

      softwareInfo
          .append("SDK: ")
          .append(Build.VERSION.SDK_INT)
          .append(newLine)
          .append("Android: ")
          .append(Build.VERSION.RELEASE)
          .append(newLine)
          .append("Model: ")
          .append(Build.MODEL)
          .append(newLine);

      dateInfo.append(Calendar.getInstance().getTime()).append(newLine);

      Log.e("OneCoreCrash", errorMessage.toString());
      FLog.error("Crash captured\n" + dateInfo + softwareInfo + errorMessage);

      Intent intent = new Intent(context, CrashActivity.class);
      intent.putExtra("Error", errorMessage.toString());
      intent.putExtra("Software", softwareInfo.toString());
      intent.putExtra("Date", dateInfo.toString());

      // The default handler is installed from Application.attachBaseContext(), so the context is
      // often not an Activity. Starting CrashActivity without NEW_TASK would throw another
      // exception while handling the original startup failure.
      if (!(context instanceof Activity)) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
      }
      context.startActivity(intent);
    } catch (Throwable handlerError) {
      Log.e("OneCoreCrash", "Crash handler failed", handlerError);
    } finally {
      Process.killProcess(Process.myPid());
      System.exit(2);
    }
  }
}
