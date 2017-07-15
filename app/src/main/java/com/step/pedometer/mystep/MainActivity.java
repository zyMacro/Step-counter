package com.step.pedometer.mystep;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;
import android.widget.Toast;

import com.step.pedometer.mystep.config.Constant;
import com.step.pedometer.mystep.service.StepService;

public class MainActivity extends AppCompatActivity  implements Handler.Callback {
    //循环取当前时刻的步数中间的时间间隔
    private long TIME_INTERVAL = 500;
    //控件
    private TextView text_step;    //显示走的步数

    private Messenger messenger;
    private Messenger mGetReplyMessenger = new Messenger(new Handler(this));
    private Handler delayHandler;



    //以bind形式开启service，故有ServiceConnection接收回调
    ServiceConnection conn = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            try {
                messenger = new Messenger(service);
                Message msg = Message.obtain(null, Constant.MSG_FROM_CLIENT);
                msg.replyTo = mGetReplyMessenger;
                messenger.send(msg);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
        @Override
        public void onServiceDisconnected(ComponentName name) {}
    };

    //接收从服务端回调的步数
    @Override
    public boolean handleMessage(Message msg) {
        int data=0;
        switch (msg.what) {
            case Constant.MSG_FROM_SERVER:
                //更新步数
                text_step.setText(msg.getData().getInt("step") + "");

                if(data!=msg.getData().getInt("step")){
                    data=msg.getData().getInt("step");
                    Log.v("main",msg.getData().getInt("step")+"");
                    Intent intent=new Intent(MainActivity.this,webview.class);
                    Bundle bundle=new Bundle();
                    bundle.putString("stepData",data+"");
//                    startActivity(intent);

                }

//
                delayHandler.sendEmptyMessageDelayed(Constant.REQUEST_SERVER, TIME_INTERVAL);
                break;
            case Constant.REQUEST_SERVER:
                try {
                    Message msgl = Message.obtain(null, Constant.MSG_FROM_CLIENT);
                    msgl.replyTo = mGetReplyMessenger;
                    messenger.send(msgl);
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
                break;
        }
        return false;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        text_step = (TextView) findViewById(R.id.main_text_step);
        delayHandler = new Handler(this);
        Toolbar toolbar=(Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);




    }
    //add menu

//    public boolean onCreateOptionsMenu(Menu menu){
//        getMenuInflater().inflate(R.menu.main,menu);
//        return true;
//    }
//    public boolean onOptionsItemSelected(MenuItem item){
//        switch (item.getItemId()){
//            case R.id.item1:
//                Toast.makeText(this,"YOU clicked item1",Toast.LENGTH_SHORT).show();
//                break;
//            case R.id.item2:
//                Intent intent=new Intent(MainActivity.this,webview.class);
//                startActivity(intent);
////                Toast.makeText(this,"YOU clicked item2",Toast.LENGTH_LONG).show();
//
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
            case R.id.stepCounter:
                Toast.makeText(this,"YOU clicked item1",Toast.LENGTH_SHORT).show();
                break;
            case R.id.jdjbz:
                Intent intent=new Intent(MainActivity.this,webview.class);
//                Bundle myBundleForName=new Bundle();
//                myBundleForName.putInt("steps",)
                startActivity(intent);
//                Toast.makeText(this,"YOU clicked item2",Toast.LENGTH_LONG).show();

                break;

            default:
        }
        return true;
    }

    //
    @Override
    public void onStart() {
        super.onStart();
        setupService();
    }
    /**
     * 开启服务
     */
    private void setupService() {
        Intent intent = new Intent(this, StepService.class);
        bindService(intent, conn, Context.BIND_AUTO_CREATE);
        startService(intent);
    }

    @Override
    public void onBackPressed() {
        moveTaskToBack(true);
        super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        //取消服务绑定
        unbindService(conn);
        super.onDestroy();
    }


}

