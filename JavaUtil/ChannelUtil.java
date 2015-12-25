package com.czt.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Build;
import android.preference.PreferenceManager;
import android.text.TextUtils;

public class ChannelUtil {
	//在多个地方用到时提供标识
	private static final String PACKAGE_ID = "czt";//"myapp_"
	//保存的安装包渠道
	private static final String CHANNEL_LAST_KEY = PACKAGE_ID + "channel";
	//保存的安装包版本
	private static final String CHANNEL_LAST_VERSION_KEY = PACKAGE_ID + "channel_version";
	//保存的安装包更新时间(SDK >= 9)
	private static final String CHANNEL_LAST_UPDATE_TIME = PACKAGE_ID + "channel_last_update_time";
	//初次安装渠道
	private static final String CHANNEL_INSTALL_KEY = PACKAGE_ID + "channel_install";
	//缓存安装包渠道
	private static String sChannel;
	//缓存初次安装渠道
	private static String sInstallChannel;
	/**
	 * 返回渠道号。  如果获取失败返回""
	 * @param context
	 * @return
	 */
	public static String getChannel(Context context){
		return getChannel(context, "");
	}
	/**
	 * 返回渠道号。  如果获取失败返回defaultChannel
	 * @param context
	 * @param defaultChannel
	 * @return
	 */
	public static String getChannel(Context context, String defaultChannel) {
		//内存中获取
		if(!TextUtils.isEmpty(sChannel)){
			return sChannel;
		}
		//sp中获取
		sChannel = getChannelBySharedPreferences(context);
		if(!TextUtils.isEmpty(sChannel)){
			return sChannel;
		}
		//从apk中获取
		sChannel = getChannelFromApk(context, CHANNEL_LAST_KEY);
		if(!TextUtils.isEmpty(sChannel)){
			//保存sp中备用
			saveChannelBySharedPreferences(context, sChannel);
			return sChannel;
		}
		//全部获取失败
		return defaultChannel;
	}
	/**
	 * 返回初装渠道号。  如果获取失败返回""
	 * @param context
	 * @return
	 */
	public static String getInstallChannel(Context context){
		return getInstallChannel(context, "");
	}
	/**
	 * 返回初装渠道号。  如果获取失败返回defaultChannel
	 * @param context
	 * @param defaultChannel
	 * @return
	 */
	public static String getInstallChannel(Context context, String defaultChannel) {
		//内存中获取
		if(!TextUtils.isEmpty(sInstallChannel)){
			return sInstallChannel;
		}
		//sp中获取
		SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);
		sInstallChannel = sp.getString(CHANNEL_INSTALL_KEY, "");
		if(!TextUtils.isEmpty(sInstallChannel)){
			return sInstallChannel;
		}
		//全部获取失败
		sInstallChannel = getChannel(context, defaultChannel);
		return sInstallChannel;
	}
	/**
	 * 从apk中获取渠道号
	 * @param context
	 * @param channelKey
	 * @return
	 */
	private static String getChannelFromApk(Context context, String channelKey) {
		//从apk包中获取
		ApplicationInfo appinfo = context.getApplicationInfo();
		String sourceDir = appinfo.sourceDir;
		//默认放在META-INF/里， 所以需要再拼接一下
		String key = "META-INF/" + channelKey;
		String channel = "";
		ZipFile zipfile = null;
		BufferedReader br = null;
		try {
			zipfile = new ZipFile(sourceDir);
			ZipEntry entry = zipfile.getEntry(key);
			if (entry != null) {
				br = new BufferedReader(new InputStreamReader(zipfile.getInputStream(entry), "UTF-8"));
				channel = br.readLine();
			}
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			if (br != null) {
				try {
					br.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
			if (zipfile != null) {
				try {
					zipfile.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
		return channel;
	}
	/**
	 * 本地保存channel & 对应版本号
	 * @param context
	 * @param channel
	 */
	private static void saveChannelBySharedPreferences(Context context, String channel){
		SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);
		Editor editor = sp.edit();
		editor.putString(CHANNEL_LAST_KEY, channel);
		editor.putInt(CHANNEL_LAST_VERSION_KEY, getVersionCode(context));
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.GINGERBREAD)
			editor.putLong(CHANNEL_LAST_UPDATE_TIME, getLastUpdateTime(context));
		if (TextUtils.isEmpty(sp.getString(CHANNEL_INSTALL_KEY, "")))
			editor.putString(CHANNEL_INSTALL_KEY, channel);
		editor.commit();
	}
	/**
	 * 从sp中获取channel(NOTE: 同版本升级渠道号会缓存, 不会被识别)
	 * @param context
	 * @return 为空表示获取异常、sp中的值已经失效、sp中没有此值
	 */
	private static String getChannelBySharedPreferences(Context context){
		if (canUseSP(context)) {
			SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);
			return sp.getString(CHANNEL_LAST_KEY, "");
		}
		return "";
	}

	private static boolean canUseSP(Context context) {
		SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.GINGERBREAD) {
			//GINGERBREAD及更高版本尝试检测同VersionCode, 不同渠道间的升级
			long lastUpdateTime = getLastUpdateTime(context);
			if (lastUpdateTime == -1) {
				//不匹配或之前没有正确的时间
				return false;
			}
			long lastUpdateTimeSaved = sp.getLong(CHANNEL_LAST_UPDATE_TIME, -1);
			if(lastUpdateTime != lastUpdateTimeSaved) {
				//已
				return false;
			}

		}
		{
			int currentVersionCode = getVersionCode(context);
			if (currentVersionCode == -1) {
				//获取错误
				return false;
			}
			int versionCodeSaved = sp.getInt(CHANNEL_LAST_VERSION_KEY, -1);
			if (currentVersionCode != versionCodeSaved) {
				//不匹配或之前没有正确的版本号
				return false;
			}
		}
		return true;
	}

	/**
	 * 从包信息中获取版本号
	 * @param context
	 * @return
	 */
	private static int getVersionCode(Context context){
		try{
			return context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionCode;
		}catch(NameNotFoundException e) {
			e.printStackTrace();
		}
		return -1;
	}

	/**
	 * 从包信息中获取最后更新时间
	 * @param context
	 * @return
	 */
	@TargetApi(Build.VERSION_CODES.GINGERBREAD)
	private static long getLastUpdateTime(Context context){
		try{
			return context.getPackageManager().getPackageInfo(context.getPackageName(), 0).lastUpdateTime;
		}catch(NameNotFoundException e) {
			e.printStackTrace();
		}
		return -1;
	}
}
