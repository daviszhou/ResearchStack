package org.researchstack.skin.ui;

import android.annotation.TargetApi;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;

import org.researchstack.backbone.StorageAccess;
import org.researchstack.backbone.ui.PinCodeActivity;
import org.researchstack.backbone.utils.ObservableUtils;
import org.researchstack.skin.AppPrefs;
import org.researchstack.skin.DataProvider;
import org.researchstack.skin.notification.TaskAlertReceiver;


public class SplashActivity extends PinCodeActivity
{
    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
    }

    @Override
    public void onDataReady()
    {
        super.onDataReady();
        // Init all notifications
        verifyBluetooth();

        sendBroadcast(new Intent(TaskAlertReceiver.ALERT_CREATE_ALL));

        DataProvider.getInstance()
                .initialize(this)
                .compose(ObservableUtils.applyDefault())
                .subscribe(response -> {

                    if(AppPrefs.getInstance(this).isOnboardingComplete() ||
                            DataProvider.getInstance().isSignedIn(this))
                    {
                        launchMainActivity();
                    }
                    else
                    {
                        launchOnboardingActivity();
                    }

                    finish();
                });
    }

    @Override
    public void onDataAuth()
    {
        if(StorageAccess.getInstance().hasPinCode(this))
        {
            super.onDataAuth();
        }
        else // allow them through to onboarding if no pincode
        {
            onDataReady();
        }
    }

    @Override
    public void onDataFailed()
    {
        super.onDataFailed();
        finish();
    }

    private void launchOnboardingActivity()
    {
        startActivity(new Intent(this, OnboardingActivity.class));
    }

    private void launchMainActivity()
    {
        startActivity(new Intent(this, MainActivity.class));
    }

    //Bluetooth Function
    private void requestBluetooth(){
        Intent intentRequestBluetooth = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE); //#create new intent that sends user-mediate bluetooth request
        startActivity(intentRequestBluetooth); //check usage
    }

    //Bluetooth Function
    @TargetApi(17)
    private void verifyBluetooth() {
        try {
            BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter(); //#attaches variable to bluetooth adapter
            if (!bluetoothAdapter.isEnabled()) {
                requestBluetooth(); //#if bluetooth is not enabled, launch requestBluetooth method
            }
        } catch (RuntimeException e) { //#create alert for user if the phone does nto support bluetooth
            final AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("Bluetooth LE not available");
            builder.setMessage("Sorry, this device does not support Bluetooth LE.");
            builder.setPositiveButton(android.R.string.ok, null);
            builder.setOnDismissListener(new DialogInterface.OnDismissListener() {

                @Override
                public void onDismiss(DialogInterface dialog) {;
                    System.exit(0);
                }

            });
            builder.show();
        }
    }
}
