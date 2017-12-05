package com.innovate.project.rtcbackup;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.AppCompatButton;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import com.afollestad.materialdialogs.DialogAction;
import com.afollestad.materialdialogs.MaterialDialog;
import com.github.angads25.filepicker.controller.DialogSelectionListener;
import com.github.angads25.filepicker.model.DialogConfigs;
import com.github.angads25.filepicker.model.DialogProperties;
import com.github.angads25.filepicker.view.FilePickerDialog;

import java.io.File;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_WRITE_PERMISSION = 786;

    private static final String TAG = "MainActivity";

    AppCompatButton viewSavedVideosButton;
    Uri videoUri;

    private Boolean exit = false;

    MaterialDialog chooseDialog;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        setUpUI();
    }


    private void setUpUI() {
        viewSavedVideosButton = findViewById(R.id.viewSavedVideosActionButton);
        viewSavedVideosButton.setOnClickListener(viewSavedVideosButtonListener);
    }


    View.OnClickListener viewSavedVideosButtonListener = new View.OnClickListener() {
        @SuppressLint("LongLogTag")
        @Override
        public void onClick(View view) {

            Log.e("viewSavedVideosButtonListener", "button pressed");
            requestPermission();
        }
    };


    private void createVideoRecordedFolder() {
        String folder_main = "Recorded Videos";

        File f = new File(Environment.getExternalStorageDirectory(), folder_main);

        if (!f.exists()) {
            Log.e(TAG, "folder Created");
            f.mkdirs();
        }

    }

    private void callFilePickerDialog() {

        DialogProperties properties = new DialogProperties();

        properties.selection_mode = DialogConfigs.SINGLE_MODE;
        properties.selection_type = DialogConfigs.FILE_SELECT;

        properties.root = new File(Environment.getExternalStorageDirectory().getPath()
                + File.separator + "Recorded Videos" + File.separator);

        properties.error_dir = new File(DialogConfigs.DEFAULT_DIR);
        properties.offset = new File(DialogConfigs.DEFAULT_DIR);

        properties.extensions = new String[]{"mp4", "avi", "flv", "mov", "wmv"};


        FilePickerDialog dialog = new FilePickerDialog(MainActivity.this, properties);
        dialog.setTitle("Select a Video File");


        dialog.setDialogSelectionListener(new DialogSelectionListener() {
            @Override
            public void onSelectedFilePaths(String[] files) {
                //files is the array of the paths of files selected by the Application User.
                Log.e(TAG, "" + files[0]);

                if (files.length != 0) {
                    videoUri = Uri.parse(files[0]);
                }

                if (videoUri != null) {
                    chooseDialog(videoUri);
                }

            }
        });

        dialog.show();
    }

    private void requestPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            requestPermissions(new String[]{android.Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_WRITE_PERMISSION);
        } else {
            Log.e(TAG, "permission already granted");
            createVideoRecordedFolder();
            callFilePickerDialog();
        }
    }


    private void chooseDialog(final Uri uri) {


        chooseDialog = new MaterialDialog.Builder(this)
                .title("Video Selected Successfully !")
                .titleColorRes(R.color.black)
                .content("Choose Whether to Play Video or Upload it.")
                .positiveText("Upload")
                .negativeText("Play")
                .positiveColorRes(R.color.colorAccent)
                .negativeColorRes(R.color.colorAccent)

                .onPositive(new MaterialDialog.SingleButtonCallback() {
                    @Override
                    public void onClick(MaterialDialog dialog, DialogAction which) {
                        // TODO

                        // Upload to google drive here.

                        Log.e(TAG,"upload to google drive");

                    }
                })

                .onNegative(new MaterialDialog.SingleButtonCallback() {
                    @Override
                    public void onClick(MaterialDialog dialog, DialogAction which) {
                        // TODO

                        //Play video here
                        Log.e(TAG,"play video");

                        watchVideo(uri);
                    }
                })

                .cancelable(true)
                .show();


    }

    private void watchVideo(Uri uri) {
        Intent playVideoIntent = new Intent(Intent.ACTION_VIEW);
        playVideoIntent.setDataAndType(uri, "video/*");
        startActivity(playVideoIntent);
    }


    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == REQUEST_WRITE_PERMISSION && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "permission granted");

            createVideoRecordedFolder();
            callFilePickerDialog();

        } else {
            Log.e(TAG, "permission not granted");
        }
    }

    @Override
    public void onBackPressed() {
        if (exit) {
            Intent intent = new Intent(Intent.ACTION_MAIN);
            intent.addCategory(Intent.CATEGORY_HOME);
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);//***Change Here***
            startActivity(intent);
            finish();
            System.exit(0);
            // finish activity
        } else {
            Toast.makeText(getApplicationContext(), "Press Back again to leave.",
                    Toast.LENGTH_SHORT).show();
            exit = true;
            new Handler().postDelayed(new Runnable() {
                @Override
                public void run() {
                    exit = false;
                }
            }, 3 * 1000);

        }

    }
}
