package com.lumina.engine;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.app.Application;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.util.Log;
import com.github.anrwatchdog.ANRError;
import com.github.anrwatchdog.ANRWatchDog;
import com.google.android.material.color.DynamicColors;
import com.google.android.material.elevation.SurfaceColors;
import com.lumina.engine.DebugActivity;
import com.lumina.engine.Helpers.EngineSettings;
import com.lumina.engine.Helpers.FileUtil;
import com.lumina.engine.Utils;

public class NebulonApp extends Application {
	private static Thread.UncaughtExceptionHandler uncaughtExceptionHandler;
	private static Context mApplicationContext;
	public static Context getContext() {
		return mApplicationContext;
	}
	public static Thread.UncaughtExceptionHandler getUncaughtExceptionHandler(){
		return uncaughtExceptionHandler;
	}
	public void onCreate() {
		mApplicationContext = this.getApplicationContext();
		EngineSettings.init(this);
		Utils.setLanguage(this);
		com.lumina.engine.ui.Snack.install(this);
		com.lumina.engine.ui.tutorial.Tutorial.install(this);

		if (isCSharpProcess()) {
			// A crash in the C# runner must only kill :csharp. Never start
			// DebugActivity with CLEAR_TASK from here: that would wipe the
			// editor task and drop the user on the projects home screen.
			Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
				Log.e("NebulonCSharp", "Fallo en el proceso C#", throwable);
				Process.killProcess(Process.myPid());
				System.exit(10);
			});
			super.onCreate();
			return;
		}

		new com.github.anrwatchdog.ANRWatchDog(7000).setANRListener(new ANRWatchDog.ANRListener() {
    		@Override
    		public void onAppNotResponding(ANRError error) {
        		error.fillInStackTrace();
				try {
					FileUtil.writeFile(getExternalFilesDir("logs")+"/not.respond.txt","");
					error.printStackTrace(new java.io.PrintStream(new java.io.File(getExternalFilesDir("logs")+"/not.respond.txt")));
				} catch(Exception e){}
        		Process.killProcess(Process.myPid());
				System.exit(1);
    		}
		}).start();
		// Keep editor crashes available as a readable report too. The exported
		// runtime installs its own CrashLog; the main app used to discard this report.
		com.lumina.engine.runtime.CrashLog.install(this);
		if(uncaughtExceptionHandler==null)
			uncaughtExceptionHandler = Thread.getDefaultUncaughtExceptionHandler();
		Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler(){
			
			public void uncaughtException(Thread thread, Throwable throwable) {
				final Intent intent = new Intent(mApplicationContext, DebugActivity.class);
				intent.putExtra("error", Log.getStackTraceString(throwable));
				intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
				int x=0;
				String str=FileUtil.getPackageDataDir(NebulonApp.this)+"/logs/log"+"%1$s"+".txt";
				while(FileUtil.isExistFile(FileUtil.getPackageDataDir(NebulonApp.this)+"/logs/log"+x+".txt")){
					x++;
				}
				String log=Log.getStackTraceString(throwable);
				if(!FileUtil.readFile(String.format(str,(x-1)+"")).equals(log))
				FileUtil.writeFile(String.format(str,x+""),log);
				new Handler(Looper.getMainLooper()).postDelayed(()->mApplicationContext.startActivity(intent),1500);
				NebulonApp.uncaughtExceptionHandler.uncaughtException(thread, throwable);
			}
		});
        
           
		super.onCreate();
	}

	private boolean isCSharpProcess() {
		try {
			String name = null;
			if (Build.VERSION.SDK_INT >= 28) name = getProcessName();
			if (name == null) {
				try (java.io.BufferedReader reader = new java.io.BufferedReader(
						new java.io.FileReader("/proc/self/cmdline"))) {
					name = reader.readLine();
				}
			}
			return name != null && name.replace("\u0000", "").endsWith(":csharp");
		} catch (Throwable ignored) {
			return false;
		}
	}

}
