package com.example.renchunlin.screenrecording;

import android.app.ActivityManager;
import android.app.ActivityManager.RunningTaskInfo;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.MediaRecorder;
import android.media.projection.MediaProjection;
import android.os.Binder;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.widget.Toast;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

/**
 * 项目名：   Screenrecording
 * 包名：     package com.example.renchunlin.screenrecording;
 * 文件名：   FloatWindowService
 * 创建者：   RCL
 * 创建时间： 2017/8/10 16:12
 * 描述：     FloatWindowService
 */

public class FloatWindowService extends Service {

	private MediaProjection mediaProjection;
	private MediaRecorder mediaRecorder;
	private VirtualDisplay virtualDisplay;

	private boolean running;
	private int width = 720;
	private int height = 1080;
	private int dpi;

	/**
	 * 用于在线程中创建或移除悬浮窗。
	 */
	private Handler handler = new Handler();

	/**
	 * 定时器，定时进行检测当前应该创建还是移除悬浮窗。
	 */
	private Timer timer;

	@Override
	public IBinder onBind(Intent intent) {
		return new RecordBinder();
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		// 开启定时器，每隔0.5秒刷新一次
		if (timer == null) {
			timer = new Timer();
			timer.scheduleAtFixedRate(new RefreshTask(), 0, 500);
		}
		return super.onStartCommand(intent, flags, startId);
	}

	@Override
	public void onCreate() {
		super.onCreate();
		HandlerThread serviceThread = new HandlerThread("service_thread",
				android.os.Process.THREAD_PRIORITY_BACKGROUND);
		serviceThread.start();
		running = false;
		mediaRecorder = new MediaRecorder();
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		// Service被终止的同时也停止定时器继续运行
		timer.cancel();
		timer = null;
	}

	public void setMediaProject(MediaProjection project) {
		mediaProjection = project;
	}

	public boolean isRunning() {
		return running;
	}

	public void setConfig(int width, int height, int dpi) {
		this.width = width;
		this.height = height;
		this.dpi = dpi;
	}

	public boolean startRecord() {
		if (mediaProjection == null || running) {
			return false;
		}
		initRecorder();
		createVirtualDisplay();
		mediaRecorder.start();
		running = true;
		return true;
	}

	public boolean stopRecord() {
		if (!running) {
			return false;
		}
		running = false;
		mediaRecorder.stop();
		mediaRecorder.reset();
		virtualDisplay.release();
		mediaProjection.stop();

		return true;
	}

	class RefreshTask extends TimerTask {

		@Override
		public void run() {
			// 当前界面是桌面，且没有悬浮窗显示，则创建悬浮窗。
			if (isHome() && !MyWindowManager.isWindowShowing()) {
				handler.post(new Runnable() {
					@Override
					public void run() {
						MyWindowManager.createSmallWindow(getApplicationContext());
					}
				});
			}
			// 当前界面不是桌面，且有悬浮窗显示，则移除悬浮窗。
			else if (!isHome() && MyWindowManager.isWindowShowing()) {
				handler.post(new Runnable() {
					@Override
					public void run() {
						MyWindowManager.removeSmallWindow(getApplicationContext());
						MyWindowManager.removeBigWindow(getApplicationContext());
					}
				});
			}
			// 当前界面是桌面，且有悬浮窗显示，则更新内存数据。
			else if (isHome() && MyWindowManager.isWindowShowing()) {
				handler.post(new Runnable() {
					@Override
					public void run() {
						//MyWindowManager.updateUsedPercent(getApplicationContext());
					}
				});
			}
		}

	}

	/**
	 * 判断当前界面是否是桌面
	 */
	private boolean isHome() {
		ActivityManager mActivityManager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
		List<RunningTaskInfo> rti = mActivityManager.getRunningTasks(1);
		return getHomes().contains(rti.get(0).topActivity.getPackageName());
	}

	/**
	 * 获得属于桌面的应用的应用包名称
	 * 
	 * @return 返回包含所有包名的字符串列表
	 */
	private List<String> getHomes() {
		List<String> names = new ArrayList<String>();
		PackageManager packageManager = this.getPackageManager();
		Intent intent = new Intent(Intent.ACTION_MAIN);
		intent.addCategory(Intent.CATEGORY_HOME);
		List<ResolveInfo> resolveInfo = packageManager.queryIntentActivities(intent,
				PackageManager.MATCH_DEFAULT_ONLY);
		for (ResolveInfo ri : resolveInfo) {
			names.add(ri.activityInfo.packageName);
		}
		return names;
	}

	//创建虚拟屏幕，通过 MediaProject 录制屏幕
	private void createVirtualDisplay() {
		virtualDisplay = mediaProjection.createVirtualDisplay("MainScreen", width, height, dpi,
				DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, mediaRecorder.getSurface(), null, null);
	}

	//录制屏幕数据，这里利用 MediaRecord 将屏幕内容保存下来，当然也可以利用其它方式保存屏幕内容
	private void initRecorder() {
		mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
		mediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
		mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
		mediaRecorder.setOutputFile(getsaveDirectory() + System.currentTimeMillis() + ".mp4");
		mediaRecorder.setVideoSize(width, height);
		mediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
		mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
		mediaRecorder.setVideoEncodingBitRate(5 * 1024 * 1024);
		mediaRecorder.setVideoFrameRate(30);
		try {
			mediaRecorder.prepare();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public String getsaveDirectory() {
		if (Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
			String rootDir = Environment.getExternalStorageDirectory().getAbsolutePath() + "/" + "ScreenRecord" + "/";

			File file = new File(rootDir);
			if (!file.exists()) {
				if (!file.mkdirs()) {
					return null;
				}
			}

			Toast.makeText(getApplicationContext(), rootDir, Toast.LENGTH_SHORT).show();

			return rootDir;
		} else {
			return null;
		}
	}

	public class RecordBinder extends Binder {
		public FloatWindowService getRecordService() {
			return FloatWindowService.this;
		}
	}
}
