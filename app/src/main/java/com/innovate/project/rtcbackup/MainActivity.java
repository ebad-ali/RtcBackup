package com.innovate.project.rtcbackup;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.IntentSender;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.support.annotation.NonNull;
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
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.drive.CreateFileActivityOptions;
import com.google.android.gms.drive.Drive;
import com.google.android.gms.drive.DriveClient;
import com.google.android.gms.drive.DriveContents;
import com.google.android.gms.drive.DriveResourceClient;
import com.google.android.gms.drive.MetadataChangeSet;
import com.google.android.gms.tasks.Continuation;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.Task;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";

    private static final int REQUEST_WRITE_PERMISSION = 786;
    private static final int REQUEST_CODE_SIGN_IN = 0;
    private static final int REQUEST_CODE_CREATOR = 2;


    AppCompatButton viewSavedVideosButton;
    Uri videoUri;

    private Boolean exit = false;

    MaterialDialog chooseDialog;

    private GoogleSignInClient mGoogleSignInClient;
    private DriveClient mDriveClient;
    private DriveResourceClient mDriveResourceClient;


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
                        signIn();

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


    /** Start sign in activity. */
    private void signIn() {
        Log.i(TAG, "Start sign in");
        mGoogleSignInClient = buildGoogleSignInClient();
        startActivityForResult(mGoogleSignInClient.getSignInIntent(), REQUEST_CODE_SIGN_IN);
    }

    /** Build a Google SignIn client. */
    private GoogleSignInClient buildGoogleSignInClient() {
        GoogleSignInOptions signInOptions =
                new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                        .requestScopes(Drive.SCOPE_FILE)
                        .build();
        return GoogleSignIn.getClient(this, signInOptions);
    }

    private void saveFileToDrive() {
        // Start by creating a new contents, and setting a callback.
        Log.i(TAG, "Creating new contents.");
        final Uri uri = videoUri;

        mDriveResourceClient
                .createContents()
                .continueWithTask(
                        new Continuation<DriveContents, Task<Void>>() {
                            @Override
                            public Task<Void> then(@NonNull Task<DriveContents> task) throws Exception {
                                return createFileIntentSender(task.getResult(), uri);
                            }
                        })
                .addOnFailureListener(
                        new OnFailureListener() {
                            @Override
                            public void onFailure(@NonNull Exception e) {
                                Log.w(TAG, "Failed to create new contents.", e);
                            }
                        });
    }

    private Task<Void> createFileIntentSender(DriveContents driveContents, Uri uri) {
        Log.i(TAG, "New contents created.");
        // Get an output stream for the contents.
        OutputStream outputStream = driveContents.getOutputStream();
        // Write the bitmap data from it.

        File file = new File(uri.getPath());
        FileInputStream fis;

        try {
            fis = new FileInputStream(file.getPath());
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buf = new byte[1024];
            int n;
            while (-1 != (n = fis.read(buf)))
                baos.write(buf, 0, n);
            byte[] photoBytes = baos.toByteArray();
            outputStream.write(photoBytes);

            /*
            outputStream.close();
            outputStream = null;
            fis.close();
            fis = null;
*/
        } catch (IOException e) {
            Log.w(TAG, "Unable to write file contents.", e);
        }

        // Create the initial metadata - MIME type and title.
        // Note that the user will be able to change the title later.
        MetadataChangeSet metadataChangeSet =
                new MetadataChangeSet.Builder()
                        .setMimeType("video/mp4")
                        .setTitle(file.getName())
                        .build();

        // Set up options to configure and display the create file activity.
        CreateFileActivityOptions createFileActivityOptions =
                new CreateFileActivityOptions.Builder()
                        .setInitialMetadata(metadataChangeSet)
                        .setInitialDriveContents(driveContents)
                        .build();

        return mDriveClient
                .newCreateFileActivityIntentSender(createFileActivityOptions)
                .continueWith(
                        new Continuation<IntentSender, Void>() {
                            @Override
                            public Void then(@NonNull Task<IntentSender> task) throws Exception {
                                startIntentSenderForResult(task.getResult(), REQUEST_CODE_CREATOR, null, 0, 0, 0);
                                return null;
                            }
                        });


    }


    @Override
    protected void onActivityResult(final int requestCode, final int resultCode, final Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode) {
            case REQUEST_CODE_SIGN_IN:
                Log.i(TAG, "Sign in request code");
                // Called after user is signed in.
                if (resultCode == RESULT_OK) {
                    Log.i(TAG, "Signed in successfully.");
                    // Use the last signed in account here since it already have a Drive scope.
                    mDriveClient = Drive.getDriveClient(this, GoogleSignIn.getLastSignedInAccount(this));
                    // Build a drive resource client.
                    mDriveResourceClient =
                            Drive.getDriveResourceClient(this, GoogleSignIn.getLastSignedInAccount(this));
                    // Start camera.
                    saveFileToDrive();
                }
                break;

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
