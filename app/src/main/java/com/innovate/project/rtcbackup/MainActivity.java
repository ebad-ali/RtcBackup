package com.innovate.project.rtcbackup;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.AppCompatButton;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.afollestad.materialdialogs.DialogAction;
import com.afollestad.materialdialogs.MaterialDialog;
import com.github.angads25.filepicker.controller.DialogSelectionListener;
import com.github.angads25.filepicker.model.DialogConfigs;
import com.github.angads25.filepicker.model.DialogProperties;
import com.github.angads25.filepicker.view.FilePickerDialog;

import java.io.File;


public class MainActivity extends AppCompatActivity {

    // Activity Tag to call in Log command
    private static final String TAG = "MainActivity";

    // Permission code called when write and read permission is triggered (SDK>Marshmallow)
    private static final int REQUEST_WRITE_PERMISSION = 786;

    // A button view that is displayed on UI Screen
    AppCompatButton viewSavedVideosButton;

    // A String of Type Uri that contain the path of the file selected when you choose file from file picker
    Uri videoUri;

    // boolean exit to exit application when back pressed three times
    private Boolean exit = false;

    // A view of dialog that ask to choose between upload or watch video
    MaterialDialog chooseDialog;


    /**
     * Called when the activity is first created.
     */

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        //Connect the java class with the UI file
        setContentView(R.layout.activity_main);

        // A function to intialize button with the above viewSavedVideosButton
        setUpUI();
    }


    private void setUpUI() {
        viewSavedVideosButton = findViewById(R.id.viewSavedVideosActionButton);
        viewSavedVideosButton.setOnClickListener(viewSavedVideosButtonListener);
    }


    // A listener trigered when you clicked on view Saved Videos Button
    View.OnClickListener viewSavedVideosButtonListener = new View.OnClickListener() {
        @SuppressLint("LongLogTag")
        @Override
        public void onClick(View view) {

            Log.e("viewSavedVideosButtonListener", "button pressed");
            requestPermission();
        }
    };


    /*
    * Function is called to check if the user has reading storage permission in case of Marshmallow
    * */
    private void requestPermission() {
        // If user doesn't have permission ask user to give permission
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            requestPermissions(new String[]{android.Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_WRITE_PERMISSION);
        }
        // if user does have permission call FileDialog Function
        else {

            Log.e(TAG, "permission already granted");
            createVideoRecordedFolder();
            callFilePickerDialog();
        }
    }


    /**
     * This function creates a folder in your directory if Recorded Videos folder doesn't
     * exist in your phone.
     */

    private void createVideoRecordedFolder() {
        String folder_main = "Recorded Videos";

        File f = new File(Environment.getExternalStorageDirectory(), folder_main);

        if (!f.exists()) {
            Log.e(TAG, "folder Created");
            f.mkdirs();
        }

    }

    /*
    *
    * This function is called to open a file picker dialog that ask user to
    * select a video file from the Recorded video folder.
    *
    * */

    private void callFilePickerDialog() {

        // Set the properties of the File Dialog
        DialogProperties properties = new DialogProperties();

        // Selection mode of the file picked. In your case it is single file selection mode.
        properties.selection_mode = DialogConfigs.SINGLE_MODE;
        properties.selection_type = DialogConfigs.FILE_SELECT;

        // The folder which will be shown when fileDialog is called. In your case it is Recorded videos folder
        properties.root = new File(Environment.getExternalStorageDirectory().getPath()
                + File.separator + "Recorded Videos" + File.separator);

        // Properties to deal with exceptions.
        properties.error_dir = new File(DialogConfigs.DEFAULT_DIR);
        properties.offset = new File(DialogConfigs.DEFAULT_DIR);

        // Defining the extension of videos which the fileDialog will show to user.
        properties.extensions = new String[]{"mp4", "avi", "flv", "mov", "wmv"};

        // Creating Object of file Dialog and Setting the Title of it.
        FilePickerDialog dialog = new FilePickerDialog(MainActivity.this, properties);
        dialog.setTitle("Select a Video File");

        // Called when user select on a video file. This function will get the Uri of the file you select from the
        // video list
        dialog.setDialogSelectionListener(new DialogSelectionListener() {
            @Override
            public void onSelectedFilePaths(String[] files) {
                //files is the array of the paths of files selected by the Application User.
                Log.e(TAG, "" + files[0]);

                if (files.length != 0) {
                    videoUri = Uri.parse(files[0]);
                }

                if (videoUri != null) {
                    // Call a Dialog to choose whether to play video or upload it.
                    chooseDialog(videoUri);
                }

            }
        });

        // show the FileDialog to choose the video.
        dialog.show();
    }


    /*
    * This function open up a dialog which ask user whether to play a video or upload it.
    * */
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

                        // In case of Upload button is pressed, call the Upload Activity and pass the String to upload
                        // video activity on google drive.

                        if(!isDeviceOnline())
                        {
                            chooseDialog.dismiss();
                            showSnackBar();
                        }
                        else
                        {
                            Log.e(TAG, "upload to google drive");

                            // Intent is called to open another activity in Android
                            Intent intent = new Intent(MainActivity.this, UploadVideo.class);

                            //called to pass value of uri from this activity to the next one
                            intent.putExtra("videoUri", String.valueOf(videoUri));

                            // used to start the activity mean hiding the current screen and showing/opening
                            //next one
                            startActivity(intent);

                        }

                    }
                })

                .onNegative(new MaterialDialog.SingleButtonCallback() {
                    @Override
                    public void onClick(MaterialDialog dialog, DialogAction which) {

                        Log.e(TAG, "play video");

                        //call play video function here and pass the uri to it.
                        watchVideo(uri);
                    }
                })
                .cancelable(true)
                .show();

    }


    private void watchVideo(Uri uri) {

        // Intent called to play video using video player which already exist in your phone.
        Intent playVideoIntent = new Intent(Intent.ACTION_VIEW);

        //tell intent the path where the video exist
        playVideoIntent.setDataAndType(uri, "video/*");

        // start the activity to play the video
        startActivity(playVideoIntent);
    }


    /*
    * Function is called to check if user granted the reading permission or not
     * if not user is asked again when button is pressed and if user give permission
     * show FileDialog activity is called to select video file.
    * */
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == REQUEST_WRITE_PERMISSION && grantResults[0] == PackageManager.PERMISSION_GRANTED) {

            Log.e(TAG, "permission granted");

            createVideoRecordedFolder();
            callFilePickerDialog();

        }
        else {
            Log.e(TAG, "permission not granted");
        }
    }



    void showSnackBar() {
        Snackbar snackbar = Snackbar
                .make(findViewById(android.R.id.content), "No internet connection.", Snackbar.LENGTH_LONG);


        View view = snackbar.getView();
        TextView tv = (TextView) view.findViewById(android.support.design.R.id.snackbar_text);
        tv.setTextColor(Color.WHITE);

        snackbar.show();

    }

    /*
    * Called when user click on back button and a toast is generated to ask user to press back
    * again in order to quit the application. This is used to not close application if
    * user pressed back button unintentionally and important work can be lost.
    * */
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

    private boolean isDeviceOnline() {
        ConnectivityManager connMgr =
                (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = connMgr.getActiveNetworkInfo();
        return (networkInfo != null && networkInfo.isConnected());
    }

}
