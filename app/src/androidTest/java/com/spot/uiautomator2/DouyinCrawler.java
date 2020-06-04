package com.spot.uiautomator2;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.support.test.filters.SdkSuppress;
import android.util.Log;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.rule.GrantPermissionRule;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.UiObjectNotFoundException;
import androidx.test.uiautomator.UiWatcher;
import androidx.test.uiautomator.Until;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileFilter;
import java.time.LocalDateTime;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import static androidx.test.core.app.ApplicationProvider.getApplicationContext;
import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.Assert.assertThat;
@RunWith(AndroidJUnit4.class)
@SdkSuppress(minSdkVersion = 18)
public class DouyinCrawler {

    private static final String BASIC_SAMPLE_PACKAGE
            = "com.ss.android.ugc.aweme";

    private static final String Launcher_Activity = "/.splash.SplashActivity";
    private static final int LAUNCH_TIMEOUT = 10000;

    private OkHttpClient http=new OkHttpClient();

    private final static String uploadhost="http://192.168.1.68/file/uploadFile";

    private final static String bucketname="tiktok.crawler";

    private static final String STRING_TO_BE_TYPED = "UiAutomator";

    private static final String tag = "Douyin_crawler";

    private UiDevice mDevice;

    private Context ctx = getApplicationContext();

    private final static int fileToDownloads = 10;

    @Rule
    public GrantPermissionRule mRuntimePermissionRule = GrantPermissionRule.grant(Manifest.permission.WRITE_EXTERNAL_STORAGE,Manifest.permission.INTERNET);

    private final static File savepath= new File("/sdcard/DCIM/camera/");

    private int initFileCounts=0;

    private final static long Test_Start_Time=System.currentTimeMillis();

    @Before
    public void startMainActivityFromHomeScreen() {
        // Initialize UiDevice instance

        mDevice = UiDevice.getInstance(getInstrumentation());


        // Start from the home screen
        //mDevice.pressHome();

        // Wait for launcher
        final String launcherPackage = getLauncherPackageName();

        assertThat(launcherPackage, notNullValue());
        Log.i(tag, "Launcher Package is " + launcherPackage);
        mDevice.wait(Until.hasObject(By.pkg(launcherPackage).depth(0)), LAUNCH_TIMEOUT);

//        // Launch the app
//        Context context = getApplicationContext();
//
//        final Intent intent = context.getPackageManager()
//                .getLaunchIntentForPackage(BASIC_SAMPLE_PACKAGE);
//        Log.i(tag,"begin start activity with intent: "+intent);
//        intent.addFlags(Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT);    // Clear out any previous instances
//        context.startActivity(intent);
//        // Wait for the app to appear
//        mDevice.wait(Until.hasObject(By.pkg(BASIC_SAMPLE_PACKAGE).depth(0)), LAUNCH_TIMEOUT);


        try {
            initFileCounts=getInitFileCounts(savepath);
            mDevice.executeShellCommand("am start-activity " + BASIC_SAMPLE_PACKAGE + Launcher_Activity);
            Log.i(tag, "Douyin started,init file counts is "+initFileCounts);
        } catch (Exception e) {
            e.printStackTrace();
            Log.i(tag,"exception before start activity:"+e.toString());
        }
        clickIknow();
        sleep(5000);
        mDevice.waitForWindowUpdate(null,5000);
    }

    @Test
    public void DownloadRecommend() {

        int i = 0;
        int height = mDevice.getDisplayHeight();
        int width = mDevice.getDisplayWidth();
        while (i < fileToDownloads) {
            try {
                saveToLocal();
            } catch (Exception e) {
                String filepath="/sdcard/exception"+LocalDateTime.now()+".jpg";
                mDevice.takeScreenshot(new File(filepath));
                e.printStackTrace();
            }finally{
                i++;
                Log.i(tag, "begin device swipe");
                mDevice.swipe( width / 2, height / 2, width / 2, height / 8,10);//100step=0.5 seconds
                Log.i(tag, "device swipe end");
                sleep(1000);
            }
        }
    }

    @After
    public void uploadVideosToS3() {
        Log.i(tag, "begin upload video file to s3");
        File[] downloadedFiles=savepath.listFiles(new FileFilter() {
            @Override
            public boolean accept(File file) {
                if(file.lastModified()>Test_Start_Time&&file.isFile()){
                    return true;
                }
                return false;
            }
        });
        for(File video:downloadedFiles){
            Log.i(tag, "begin upload file "+video.getName()+" file size:"+video.length());
            try {
                uploadFile(video);
            } catch (Exception e) {
                e.printStackTrace();
                Log.i(tag,"failed to upload file:"+e.toString());
            }
            video.delete();
        }
        Log.i(tag,"upload all files finished");
    }

    public void saveToLocal() throws Exception {
        int height = mDevice.getDisplayHeight();
        int width = mDevice.getDisplayWidth();
        Log.i(tag, "begin longclick on "+width/2+" "+height/2);
        mDevice.drag(width / 2, height / 2, width / 2, height / 2, 200);//
        Log.i(tag, "begin longclick finish ");
        UiObject2 save = mDevice.wait(Until.findObject(By.text("保存本地")), 5000);
        if (save != null) {
            save.click();
            Log.i(tag, "click 保存本地");

            boolean filedownloaded=waitforFileDownload(savepath,15000);
            if (!filedownloaded ) {
                Log.i(tag,"没有检测到已保存");
                throw new UiObjectNotFoundException("没有检测到已保存");
            }else{
                Log.i(tag,"已保存完毕");
            }
        } else {
            mDevice.click(width / 2, height / 2);//缩回弹框
            Log.i(tag,"没找到保存本地按钮");
            throw new UiObjectNotFoundException("没找到保存本地按钮");
        }
    }

    public void clickIknow() {
        mDevice.registerWatcher("i know",new UiWatcher(){
            @Override
            public boolean checkForCondition() {
                UiObject2 iknow = mDevice.findObject(By.res("com.ss.android.ugc.aweme:id/eev"));
                if (iknow != null) {
                    iknow.click();
                    Log.i(tag, "click 我知道了");
                    return true;
                }
                return false;
            }
        });
        mDevice.runWatchers();
    }

    /**
     * Uses package manager to find the package name of the device launcher. Usually this package
     * is "com.android.launcher" but can be different at times. This is a generic solution which
     * works on all platforms.`
     */
    private String getLauncherPackageName() {
        // Create launcher Intent
        final Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_HOME);

        // Use PackageManager to get the launcher package name

        PackageManager pm = ctx.getPackageManager();

        ResolveInfo resolveInfo = pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY);
        return resolveInfo.activityInfo.packageName;
    }

    public void sleep(long ms){
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public boolean waitforFileDownload(File dir,long timeout){
        long starttime=System.currentTimeMillis();
        while(true){
            sleep(1000);
            int counts=dir.listFiles().length;
            if(counts>initFileCounts){
                Log.i(tag,"Download finished,now file counts is "+counts);
                initFileCounts=counts;
                return true;
            }
            else{
                if(System.currentTimeMillis()-starttime>timeout){
                    Log.i(tag,"waiting for file download timeout");
                    return false;
                }
            }
        }
    }

    public int getInitFileCounts(File savepath) throws Exception{
        if(savepath.isDirectory()){
            return savepath.listFiles().length;
        }
        else{
            throw new Exception(savepath.getAbsolutePath()+" is not a Dir!");
        }
    }

    public void uploadFile(File file) throws Exception{

        RequestBody formBody = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", file.getName(),
                        RequestBody.Companion.create(file,MediaType.parse("video/mpeg")))
                .addFormDataPart("bucketname", bucketname)
                .addFormDataPart("s3key",file.getName())
                .build();
        okhttp3.Request request = new Request.Builder().url(uploadhost).post(formBody).build();
        Response response = http.newCall(request).execute();
        Log.i(tag,response.toString());
    }
}


