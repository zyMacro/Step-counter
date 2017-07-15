package com.step.pedometer.mystep.service;

import android.annotation.TargetApi;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.PowerManager;
import android.os.RemoteException;
import android.support.annotation.Nullable;
import android.support.v7.app.NotificationCompat;
import android.util.Log;

import com.step.pedometer.mystep.MainActivity;
import com.step.pedometer.mystep.R;
import com.step.pedometer.mystep.config.Constant;
import com.step.pedometer.mystep.pojo.StepData;
import com.step.pedometer.mystep.utils.CountDownTimer;
import com.step.pedometer.mystep.utils.DbUtils;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
//头天晚上不关机的时候还是有些问题，比如当屏幕灭的时候和屏幕亮的时候都不能调用清零的函数，只有当发生操作发出广播的时候才会调用。

/**
 * 计步服务
 *
 * Macro:原作者的计步想法是在一天的开头记录一个步数，记作为previousStep,在进行中记录一个为event.values[0]，然后把两个相减，得到这段时间内走的步数。但这是错误的！
 * 原作者对TYPE_STEP_COUNTER这个API的理解不对，这个并不是一个月内steps的和，而是开机这段时间的步数，关机会重置的，所以需要在计步的时候，判断这是不是重启后的第一次计步，如果是的话，需要在关机时候的步数的基础上加。
 */
@TargetApi(Build.VERSION_CODES.CUPCAKE)
public class StepService extends Service implements SensorEventListener {

    SharedPreferences.Editor editor;
    SharedPreferences pref;

    SharedPreferences.Editor editor2;
    SharedPreferences pref2;

    private final String TAG="TAG_StepService";   //"StepService";
    //默认为30秒进行一次存储
    private static int duration=1000;       //我改为1000ms进行一次存储
    private static String CURRENTDATE="";   //当前的日期
    private SensorManager sensorManager;    //传感器管理者
    private StepDetector stepDetector;
    private NotificationManager nm;
    private NotificationCompat.Builder builder;
    private Messenger messenger=new Messenger(new MessengerHandler());
    //广播
    private BroadcastReceiver mBatInfoReceiver;
    private PowerManager.WakeLock mWakeLock;
    private TimeCount time;

    //计步传感器类型 0-counter 1-detector 2-加速度传感器
    private static int stepSensor = -1;
    private List<StepData> mStepData;

    //用于计步传感器
    private int previousStep;    //用于记录之前的步数
    private boolean isNewDay=false;    //用于判断是否是新的一天，如果是新的一天则将之前的步数赋值给previousStep

    //Macro
    private boolean isFirstOpen=false;
    public boolean isZeroHour=false;
    public boolean isZeroHourExecuted=false;
    public int lastDayStep;   //如果今天没有开机的话，记录开机期间所有零点时的步数之和
    public static String clearDate="";   //此次清空的日期
    public int zeroEventValues=0;        //记录0点时的event.values[0]的值

    //记录上次关机时候的步数
    private int lastStep;    //用于记录之前的步数


    private static class MessengerHandler extends Handler {
        @Override
        public void handleMessage(Message msg){
            switch (msg.what){
                case Constant.MSG_FROM_CLIENT:
                    try{
                        Messenger messenger=msg.replyTo;
                        Message replyMsg=Message.obtain(null,Constant.MSG_FROM_SERVER);
                        Bundle bundle=new Bundle();
                        //将现在的步数以消息的形式进行发送
                        bundle.putInt("step",StepDetector.CURRENT_STEP);
                        replyMsg.setData(bundle);
                        messenger.send(replyMsg);  //发送要返回的消息
                    } catch (RemoteException e) {
                        e.printStackTrace();
                    }
                    break;
                default:
                    super.handleMessage(msg);
            }
        }
    }

    @Override
    public void onCreate(){
        super.onCreate();
        //初始化广播
        initBroadcastReceiver();
        new Thread(new Runnable() {
            @Override
            public void run() {
                //启动步数监测器
                startStepDetector();
            }
        }).start();
        startTimeCount();

        //Macro:  初始化,第一次安装的时候假设当天关过机了，把今天关机的时间和步数0保存，
        String lastDate=getLastDate();
        String todayDate=getTodayDate();

        editor=getSharedPreferences("DateStep",MODE_PRIVATE).edit();   //存储每天的日期和对应的步数
        pref=getSharedPreferences("DateStep",MODE_PRIVATE);
        editor.putInt(lastDate,0);
        editor.apply();

        editor2=getSharedPreferences("ShutDown",MODE_PRIVATE).edit();   //存储每天开机的日期和对应的步数，另外还要存入上次关机的时间
        pref2=getSharedPreferences("ShutDown",MODE_PRIVATE);

        editor2.putInt(todayDate,0);
        editor2.putString("lastOpenDate",todayDate);    //第一次安装时，设今天为上次开机的时间，步数为0
        editor2.apply();


    }
    @Override
    public int onStartCommand(Intent intent, int flags, int startId){
        initTodayData();
        updateNotification("今日步数:"+StepDetector.CURRENT_STEP+" 步");
        return START_STICKY;
    }
    /**
     * 获得今天的日期
     */
    private String getTodayDate(){
        Date date=new Date(System.currentTimeMillis());
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        return sdf.format(date);
    }

    //Macro:  获得昨天的日期
    private String getLastDate(){
        Date date=new Date(System.currentTimeMillis());
        Calendar cal=Calendar.getInstance();
        cal.setTime(date);
        cal.add(Calendar.DAY_OF_MONTH,-1);
        date=cal.getTime();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        return sdf.format(date);
    }
    public static String getLastDate(String specifiedDay){
//SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd");
        Calendar c = Calendar.getInstance();
        Date date=null;
        try {
            date = new SimpleDateFormat("yy-MM-dd").parse(specifiedDay);
        } catch (ParseException e) {
            e.printStackTrace();
        }
        c.setTime(date);
        int day=c.get(Calendar.DATE);
        c.set(Calendar.DATE,day-1);

        String dayBefore=new SimpleDateFormat("yyyy-MM-dd").format(c.getTime());
        return dayBefore;
    }
    //Macro:  获得从开机到现在这段时间内每天的步数和
    public int getLastDayStep(String lastOpenDate){
        String curr_date=getTodayDate();
        int totalStep=0;
        while(true){
            String last_date=getLastDate(curr_date);
            Log.v("StepLastDate",last_date);
            totalStep+=pref.getInt(last_date,0);
            Log.v("totalStep",totalStep+"");
            if(last_date.equals(lastOpenDate)){
                Log.v("StepEquals",last_date);
                break;
            }
            curr_date=last_date;
        }
        return totalStep;
    }

    /**
     * 初始化当天的日期
     * Macro:  只是判断是不是newDay的
     */
    private void initTodayData(){
        CURRENTDATE=getTodayDate();
        //在创建方法中有判断，如果数据库已经创建了不会二次创建的
        DbUtils.createDb(this,Constant.DB_NAME);

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
            Log.e(TAG, "出错了！");
        }
    }

    /**
     * 初始化广播
     */
    private void initBroadcastReceiver(){
        //定义意图过滤器
        final IntentFilter filter=new IntentFilter();
        //屏幕灭屏广播
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        //日期修改
        filter.addAction(Intent.ACTION_TIME_CHANGED);
        //关闭广播
        filter.addAction(Intent.ACTION_SHUTDOWN);
        //屏幕高亮广播
        filter.addAction(Intent.ACTION_SCREEN_ON);
        //屏幕解锁广播
        filter.addAction(Intent.ACTION_USER_PRESENT);
        //当长按电源键弹出“关机”对话或者锁屏时系统会发出这个广播
        //example：有时候会用到系统对话框，权限可能很高，会覆盖在锁屏界面或者“关机”对话框之上，
        //所以监听这个广播，当收到时就隐藏自己的对话，如点击pad右下角部分弹出的对话框
        filter.addAction(Intent.ACTION_CLOSE_SYSTEM_DIALOGS);

        mBatInfoReceiver=new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action=intent.getAction();

                if(Intent.ACTION_SCREEN_ON.equals(action)){
                    Log.v(TAG,"screen on");
                }else if(Intent.ACTION_SCREEN_OFF.equals(action)){
                    Log.v(TAG,"screen off");
                    save();
                    //改为60秒一存储
                    duration=60000;
                }else if(Intent.ACTION_USER_PRESENT.equals(action)){
                    Log.v(TAG,"screen unlock");
                    save();
                    //改为30秒一存储
                    duration=30000;
                }else if(Intent.ACTION_CLOSE_SYSTEM_DIALOGS.equals(intent.getAction())){
                    Log.v(TAG,"receive Intent.ACTION_CLOSE_SYSTEM_DIALOGS  出现系统对话框");
                    //保存一次
                    save();
                }else if(Intent.ACTION_SHUTDOWN.equals(intent.getAction())){
                    Log.v(TAG,"receive ACTION_SHUTDOWN");
                    //Macro： 本来想记录关机时刻的步数和对应的日期，但发现没有用，接收不到关机广播，所以只能转向另一个方法，利用event.values[0]==0来判断是否刚开机，记录开机的日期和步数
//                    Log.v("ShutCurrent",StepDetector.CURRENT_STEP+"");
//
//                    String current_Date = getTodayDate();
//
//                    Log.v("ShutPref",pref.getInt(current_Date,0)+"");
//                    editor2.putInt(current_Date,StepDetector.CURRENT_STEP);
//                    editor2.putString("lastShutDownDate",current_Date);
//                    editor2.apply();
                    save();
                }else if(Intent.ACTION_TIME_CHANGED.equals(intent.getAction())){
                    Log.v(TAG,"receive ACTION_TIME_CHANGED");
                    initTodayData();
                }
            }
        };
        registerReceiver(mBatInfoReceiver,filter);
    }

    private void startTimeCount(){
        time=new TimeCount(duration,1000);
        time.start();
    }

    /**
     * 更新通知(显示通知栏信息)
     * @param content
     */
    private void updateNotification(String content){
        builder=new NotificationCompat.Builder(this);
        builder.setPriority(Notification.PRIORITY_MIN);
        PendingIntent contentIntent=PendingIntent.getActivity(this,0,
                new Intent(this, MainActivity.class),0);
        builder.setContentIntent(contentIntent);
        builder.setSmallIcon(R.mipmap.ic_launcher_round);
        builder.setTicker("BasePedo");
        builder.setContentTitle("BasePedo");
        //设置不可清除
        builder.setOngoing(true);
        builder.setContentText(content);
        Notification notification=builder.build(); //上面均为构造Notification的构造器中设置的属性

        startForeground(0,notification);
        nm=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        nm.notify(R.string.app_name,notification);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return messenger.getBinder();
    }

    private void startStepDetector(){
        if(sensorManager!=null&& stepDetector !=null){
            sensorManager.unregisterListener(stepDetector);
            sensorManager=null;
            stepDetector =null;
        }
        //得到休眠锁，目的是为了当手机黑屏后仍然保持CPU运行，使得服务能持续运行
        getLock(this);
        sensorManager=(SensorManager)this.getSystemService(SENSOR_SERVICE);
        //android4.4以后可以使用计步传感器
        int VERSION_CODES = Build.VERSION.SDK_INT;
        if(VERSION_CODES>=19){
            addCountStepListener();
        }else{
            addBasePedoListener();
        }
    }

    /**
     * 使用自带的计步传感器
     *
     * 说明：
     *     开始使用这个传感器的时候很头疼，虽然它计步很准确，然而计步一开始就是5w多步，搞不懂它是怎么计算的，而且每天
     * 都在增长，不会因为日期而清除。今天灵光一闪，我脑海中飘过一个想法，会不会手机上的计步传感器是以一个月为计算周期
     * 呢？     于是乎，我打开神器QQ，上面有每天的步数，我把这个月的步数加到了一起在和手机上显示的步数进行对比，呵，
     * 不比不知道，一比吓一跳哈。就差了几百步，我猜测误差是因为QQ定期去得到计步传感器的步数，而我的引用是实时获取。要不然
     * 就是有点小误差。不过对于11W多步的数据几百步完全可以忽略。从中我可以得到下面两个信息：
     * 1.手机自带计步传感器存储的步数信息是以一个月为周期进行清算
     * 2.QQ计步使用的是手机自带的计步传感器   啊哈哈哈
     *
     *
     * 后来当我要改的时候又发现问题了
     * 我使用了StepDetector.CURRENT_STEP = (int)event.values[0];
     * 所以它会返回这一个月的步数，当每次传感器发生改变时，我直接让CURRENT_STEP++；就可以从0开始自增了
     * 不过上面的分析依然正确。不过当使用CURRENT_STEP++如果服务停掉计步就不准了。如果使用计步传感器中
     * 统计的数据减去之前的数据就是当天的数据了，这样每天走多少步就能准确的显示出来
     */
    private void addCountStepListener(){
        Sensor detectorSensor=sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR);
        Sensor countSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER);
        if(countSensor!=null){
            stepSensor = 0;
            Log.v(TAG, "countSensor 步数传感器");
            sensorManager.registerListener(StepService.this,countSensor,SensorManager.SENSOR_DELAY_UI);
        }else if(detectorSensor!=null){
            stepSensor = 1;
            Log.v("base", "detector");
            sensorManager.registerListener(StepService.this,detectorSensor,SensorManager.SENSOR_DELAY_UI);
        }else{
            stepSensor = 2;
            Log.e(TAG,"Count sensor not available! 没有可用的传感器，只能用加速传感器了");
            addBasePedoListener();
        }
    }



    /**
     * 使用加速度传感器
     */
    private void addBasePedoListener(){
        //只有在使用加速传感器的时候才会调用StepDetector这个类
        stepDetector =new StepDetector(this);
        //获得传感器类型，这里获得的类型是加速度传感器
        //此方法用来注册，只有注册过才会生效，参数：SensorEventListener的实例，Sensor的实例，更新速率
        Sensor sensor=sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        sensorManager.registerListener(stepDetector,sensor,SensorManager.SENSOR_DELAY_UI);
        stepDetector.setOnSensorChangeListener(new StepDetector.OnSensorChangeListener() {
            @Override
            public void onChange() {
                updateNotification("今日步数:"+StepDetector.CURRENT_STEP+" 步");
            }
        });
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if(stepSensor == 0){   //使用计步传感器
            //Macro:  其实我改写之后，这个isNewDay的if-else已经没有什么意义了
//            if(isNewDay) {
//                //用于判断是否为新的一天，如果是那么记录下计步传感器统计步数中的数据
//                // 今天走的步数=传感器当前统计的步数-之前统计的步数
//                previousStep = (int) event.values[0];    //得到传感器统计的步数
//                isNewDay = false;
//                save();
//                //为防止在previousStep赋值之前数据库就进行了保存，我们将数据库中的信息更新一下
//                List<StepData> list=DbUtils.getQueryByWhere(StepData.class,"today",new String[]{CURRENTDATE});
//                //修改数据
//                StepData data=list.get(0);
//                data.setPreviousStep(previousStep+"");
//                DbUtils.update(data);
//            }else {
//                //取出之前的数据
//                List<StepData> list = DbUtils.getQueryByWhere(StepData.class, "today", new String[]{CURRENTDATE});
//                StepData data=list.get(0);
//                Log.v("previousStepOld0",previousStep+"");
//            }
            String current_Date = getTodayDate();
            String last_Date=getLastDate();
            if(event.values[0]==0){    //判断是否是重启后的，如果是，保存当日的日期和对应的步数，以及"lastOpenDate"对应的当前日期，方便后面计算的时候找到最近一次启动时候的数据
                editor2.putInt(current_Date,StepDetector.CURRENT_STEP);
                editor2.putString("lastOpenDate",current_Date);
                editor2.apply();
            }
//            if(pref.contains(last_Date)){
//                Log.v("StepContains0",last_Date);
//                lastDayStep=getLastDayStep();   //得到昨天最后的步数
//            }
//            else{
//                Log.v("StepNotContains0",last_Date);
//            }

            if(pref2.contains(current_Date)){              //如果今天开机过的话，得到开机时候的步数lastStep
                if(pref2.contains(current_Date)){
                    Log.v("StepContains",current_Date);
                    lastStep=pref2.getInt(current_Date,1000);
                }
                else{
                    Log.v("StepNotContain",current_Date);
                }
                StepDetector.CURRENT_STEP=(int)event.values[0]+lastStep;
            }
            else{
                String lastOpenDate=pref2.getString("lastOpenDate","");
                lastDayStep=getLastDayStep(lastOpenDate);
                if(pref2.contains(lastOpenDate)){
                    Log.v("StepContains2",lastOpenDate);
                    lastStep=pref2.getInt(lastOpenDate,1000);
                }
                else{
                    Log.v("StepNotContains2",lastOpenDate);
                }
                StepDetector.CURRENT_STEP=(int)event.values[0]+lastStep-lastDayStep;   //Macro:  lastDayStep应该包括从现在到第一次开机之间所有日子的步数
            }
                editor.putInt(current_Date,StepDetector.CURRENT_STEP);
                editor.apply();
                Log.v("StepAll",pref.getAll()+"");
                Log.v("StepAllOpen",pref2.getAll()+"");
                save();
//            }
//            Log.v("previousStep",previousStep+"");
            Log.v("lastStep",lastStep+"");
            Log.v("StepDetector",StepDetector.CURRENT_STEP+"");
            Log.v("Step.event.values",event.values[0]+"");
            Log.v("Step.lastDayStep",lastDayStep+"");

//            Log.v("StepShared",pref.getInt(current_Date,0)+"");
            Log.v("StepLastOpenDate",pref2.getString("lastOpenDate",""));
//            Log.v("StepLastDate",last_Date);

            //或者只是使用下面一句话，不过程序关闭后可能无法计步。根据需求可自行选择。
            //如果记录程序开启时走的步数可以使用这种方式——StepDetector.CURRENT_STEP++;
            //StepDetector.CURRENT_STEP++;
        }else if(stepSensor == 1){
            StepDetector.CURRENT_STEP++;
        }
        //更新状态栏信息
        updateNotification("今日步数：" + StepDetector.CURRENT_STEP + " 步");
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }


//    //Macro:  判断是不是零点，如果是，则清零
//    public void clearZero(){
////        if(!isZeroHourExecuted){
//        isZeroHour();
//        if(isZeroHour  && !clearDate.equals(getTodayDate())){
//            List<StepData> list=DbUtils.getQueryByWhere(StepData.class,"today",new String[]{CURRENTDATE});
//            StepDetector.CURRENT_STEP=Integer.parseInt(list.get(0).getStep());
//
//            lastDayStep=StepDetector.CURRENT_STEP;
////                lastStep=0;
////                StepDetector.CURRENT_STEP=0;
//            save();
//            clearDate = getTodayDate();
//            Log.v("StepExecuted",lastDayStep+"");
//        }
//        isZeroHour=false;
//
////        }
//        Log.v("StepZero",isZeroHourExecuted+"");
//        Log.v("StepLastDay",lastDayStep+"");
//    }

//    public void isZeroHour(){
//
//        Calendar cal=Calendar.getInstance();     //当前日期
//        int hour=cal.get(Calendar.HOUR_OF_DAY);  //获取小时
//        int minute=cal.get(Calendar.MINUTE);     //获取分钟
//        int minuteOfDay=hour*60+minute;          //从0:00分到目前为止的分钟数
//        final int end=2;
//        if(minuteOfDay<end){
//            isZeroHour=true;
//            Log.v("StepZeroHour",isZeroHour+"");
//        }
//        Log.v("StepminuteOfDay",minuteOfDay+"");
//    }


    /**
     * 保存数据
     */
    private void save(){
        int tempStep=StepDetector.CURRENT_STEP;
        List<StepData> list=DbUtils.getQueryByWhere(StepData.class,"today",new String[]{CURRENTDATE});
        if(list.size()==0||list.isEmpty()){
            StepData data=new StepData();
            data.setToday(CURRENTDATE);
            data.setStep(tempStep+"");
            data.setPreviousStep(previousStep+"");
            Log.v("saveTempStep",tempStep+"");
            Log.v("savePreviousStep",previousStep+"");
            DbUtils.insert(data);
        }else if(list.size()==1){
            //修改数据
            StepData data=list.get(0);
            data.setStep(tempStep+"");
            DbUtils.update(data);
            Log.v("updateStep",tempStep+"");
        }
    }

    @Override
    public void onDestroy(){
        //取消前台进程
        stopForeground(true);
        DbUtils.closeDb();
        unregisterReceiver(mBatInfoReceiver);
        Intent intent=new Intent(this,StepService.class);
        startService(intent);
        super.onDestroy();
    }

    //  同步方法   得到休眠锁
    synchronized private PowerManager.WakeLock getLock(Context context){
        if(mWakeLock!=null){
            if(mWakeLock.isHeld()) {
                mWakeLock.release();
                Log.v(TAG,"释放锁");
            }

            mWakeLock=null;
        }

        if(mWakeLock==null){
            PowerManager mgr=(PowerManager)context.getSystemService(Context.POWER_SERVICE);
            mWakeLock=mgr.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,StepService.class.getName());
            mWakeLock.setReferenceCounted(true);
            Calendar c=Calendar.getInstance();
            c.setTimeInMillis((System.currentTimeMillis()));
            int hour =c.get(Calendar.HOUR_OF_DAY);
            if(hour>=23||hour<=6){
                mWakeLock.acquire(5000);
            }else{
                mWakeLock.acquire(300000);
            }
        }
        Log.v(TAG,"得到了锁");
        return (mWakeLock);
    }

    class TimeCount extends CountDownTimer {
        public TimeCount(long millisInFuture,long countDownInterval){
            super(millisInFuture,countDownInterval);
        }

        @Override
        public void onTick(long millisUntilFinished) {

        }

        @Override
        public void onFinish() {
            //如果计时器正常结束，则开始计步
            time.cancel();
            save();
            startTimeCount();
        }
    }


}
