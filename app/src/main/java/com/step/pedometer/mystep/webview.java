package com.step.pedometer.mystep;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;
import android.webkit.CookieSyncManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;
import android.util.Log;

import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;

import static java.lang.System.out;

import com.step.pedometer.mystep.config.Constant;
import com.step.pedometer.mystep.pojo.StepData;
import com.step.pedometer.mystep.service.StepDetector;
import com.step.pedometer.mystep.service.StepService;
import com.step.pedometer.mystep.utils.DbUtils;

public class webview extends AppCompatActivity {
    private List<StepData> mStepData;
    private static String CURRENTDATE="";   //当前的日期
    private boolean isNewDay=false;    //用于判断是否是新的一天，如果是新的一天则将之前的步数赋值给previousStep



    @SuppressLint("JavascriptInterface")
    @Override
    protected void onCreate(Bundle savedInstanceState) {


        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_webview);



        WebView webView=(WebView) findViewById(R.id.web_view);
//        webView.getSettings().setJavaScriptEnabled(true);
        WebSettings webSettings = webView.getSettings();

        String ua=webView.getSettings().getUserAgentString();
        webView.getSettings().setUserAgentString(ua+";sjtu-android");

        webSettings.setJavaScriptEnabled(true);


//        webSettings.setLayoutAlgorithm(WebSettings.LayoutAlgorithm.NARROW_COLUMNS);
        webSettings.setLayoutAlgorithm(WebSettings.LayoutAlgorithm.SINGLE_COLUMN);
//        webSettings.setLayoutAlgorithm(WebSettings.LayoutAlgorithm.NORMAL);
        webSettings.setUseWideViewPort(true);
        webSettings.setLoadWithOverviewMode(true);  //关键点
        webSettings.setBuiltInZoomControls(true); // 设置显示缩放按钮
        webSettings.setDisplayZoomControls(false);
        webSettings.setDefaultFontSize(18);
        webView.setWebContentsDebuggingEnabled(true);

        webView.setWebViewClient(new WebViewClient());
        webView.loadUrl("http://health.sjtu.edu.cn/myhome.php");
//        webView.loadUrl("http://health.sjtu.edu.cn/react-jdjbz/dist/#/");
//        webView.requestFocusFromTouch();       //支持获取手势焦点
        webView.addJavascriptInterface(this,"wv" );
//        webView.loadUrl("http://www.baidu.com");

        Toolbar toolbar=(Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

//        initTodayData();
//        Log.v("todaySteps","今日步数:"+StepDetector.CURRENT_STEP+" 步");

//        Log.v("ua","hello");


    }
    @android.webkit.JavascriptInterface
    public void PostDataFromWebToWebView(String msg){
        Log.v("dataToWebView",msg);
        parseJSONWithJSONObject(msg);
    }
    @android.webkit.JavascriptInterface
    public void parseJSONWithJSONObject(String msg2){
        try{
            initTodayData();
            Log.v("todaySteps","今日步数:"+StepDetector.CURRENT_STEP+" 步");
            JSONObject jsonData=new JSONObject(msg2);
            String userid=jsonData.getString("userid");
            String action=jsonData.getString("action");
            String name=jsonData.getString("name");
            String jaccount=jsonData.getString("jaccount");
            String token=jsonData.getString("token");
            URL url=new URL("https://health.sjtu.edu.cn/post-data-from-app.php?jaccount="+jaccount+"&userid="+userid+"&token="+token+"&CMD=current_steps&steps="+StepDetector.CURRENT_STEP);
            Log.v("webhttpsrequest",url.toString());
//            URL url1=new URL("http://health.sjtu.edu.cn/something/music.html");
//            HttpURLConnection connection=(HttpURLConnection) url.openConnection();
//            connection.setRequestMethod("GET");

//            connection.setConnectTimeout(8000);
//            connection.setReadTimeout(8000);
//            InputStream in=connection.getInputStream();
//            connection.disconnect();
//            URLConnection urlConnection=url.openConnection();
//            InputStream in=urlConnection.getInputStream();
//            copyInputStreamToOutputStream(in,System.out);
            HttpsURLConnection urlConnection=(HttpsURLConnection) url.openConnection();
            urlConnection.setRequestProperty("User-agent","sjtu-android");
            InputStream in=new BufferedInputStream(urlConnection.getInputStream());
//
        }
        catch(Exception e){
            e.printStackTrace();
        }
    }

//    private void copyInputStreamToOutputStream(InputStream in, PrintStream out) {
//        IOUtils.copy(in,out);
//        in.close();
//        out.close();
//    }


    //    public boolean onCreateOptionsMenu(Menu menu){
//        getMenuInflater().inflate(R.menu.main,menu);
//        return true;
//    }
//    public boolean onOptionsItemSelected(MenuItem item){
//        switch (item.getItemId()){
//            case R.id.item1:
//                Intent intent=new Intent(webview.this,MainActivity.class);
//                startActivity(intent);
////                Toast.makeText(this,"YOU clicked item1",Toast.LENGTH_SHORT).show();
//                break;
//            case R.id.item2:
////                Intent intent=new Intent(MainActivity.this,webview.class);
////                startActivity(intent);
//                Toast.makeText(this,"YOU clicked item2",Toast.LENGTH_LONG).show();
//                break;
//
//            default:
//        }
//        return true;
//    }
public boolean onCreateOptionsMenu(Menu menu){
    getMenuInflater().inflate(R.menu.toolbar,menu);
    return true;
}
    public boolean onOptionsItemSelected(MenuItem item){
        switch (item.getItemId()){
            case R.id.jdjbz:
                Toast.makeText(this,"YOU clicked item1",Toast.LENGTH_SHORT).show();
                break;
            case R.id.stepCounter:
                Intent intent=new Intent(webview.this,MainActivity.class);
                startActivity(intent);
                break;

            default:
        }
        return true;
    }

    /**
     * 获得今天的日期
     */
    private String getTodayDate(){
        Date date=new Date(System.currentTimeMillis());
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        return sdf.format(date);
    }

    /**
     * 初始化当天的日期
     */
    private void initTodayData(){
        CURRENTDATE=getTodayDate();
        //在创建方法中有判断，如果数据库已经创建了不会二次创建的
        DbUtils.createDb(this, Constant.DB_NAME);

        //获取当天的数据
        List<StepData> list=DbUtils.getQueryByWhere(StepData.class,"today",new String[]{CURRENTDATE});
        if(list.size()==0||list.isEmpty()){
            //如果获取当天数据为空，则步数为0
            StepDetector.CURRENT_STEP=0;
            isNewDay=true;  //用于判断是否存储之前的数据，后面会用到
        }else if(list.size()==1){
            isNewDay=false;
            //如果数据库中存在当天的数据那么获取数据库中的步数
            StepDetector.CURRENT_STEP=Integer.parseInt(list.get(0).getStep());
        }else{
            Log.e("initTodayData", "出错了！");
        }
    }

}



