package com.innovate.project.rtcbackup;

import android.Manifest;
import android.accounts.AccountManager;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.widget.Toast;

import com.afollestad.materialdialogs.DialogAction;
import com.afollestad.materialdialogs.MaterialDialog;
import com.crashlytics.android.Crashlytics;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.api.client.extensions.android.http.AndroidHttp;
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;
import com.google.api.client.googleapis.extensions.android.gms.auth.GooglePlayServicesAvailabilityIOException;
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException;
import com.google.api.client.googleapis.media.MediaHttpUploader;
import com.google.api.client.googleapis.media.MediaHttpUploaderProgressListener;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.InputStreamContent;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.ExponentialBackOff;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import io.fabric.sdk.android.Fabric;
import pub.devrel.easypermissions.AfterPermissionGranted;
import pub.devrel.easypermissions.EasyPermissions;


public class UploadVideo extends AppCompatActivity implements EasyPermissions.PermissionCallbacks {


    static final String TAG = "UploadVideo";

    //Credentials defined to login with google account if application open first time
    GoogleAccountCredential mCredential;

    // A progress dialog defined which will show the progress of how much file is uploaded.
    ProgressDialog mDialog;

    // Uri that contains the path of the video file selected in previous activity
    Uri videoUri;

    static final int REQUEST_ACCOUNT_PICKER = 1000;
    static final int REQUEST_AUTHORIZATION = 1001;
    static final int REQUEST_GOOGLE_PLAY_SERVICES = 1002;
    static final int REQUEST_PERMISSION_GET_ACCOUNTS = 1003;


    private static final String PREF_ACCOUNT_NAME = "accountName";

    private static final String[] SCOPES = DriveScopes.all().toArray(new String[0]);

    // A thread defined where video would be uploaded later.
    private aTask mTask;


    /**
     * Create the main activity.
     *
     * @param savedInstanceState previously saved instance data.
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Fabric.with(this, new Crashlytics());

        // used to get the Uri/path of the video you selected and the value of path you passed in MainActivity
        // is get by using getExtras() function and saved in a string which then initialized into a String.
        Bundle bundle = getIntent().getExtras();
        String uri = null;
        if (bundle != null) {
            uri = bundle.getString("videoUri");
        }

        // videoUri is initialized with the path of video to be uploaded
        videoUri = Uri.parse(uri);


        // Initialize credentials and service object.
        mCredential = GoogleAccountCredential.usingOAuth2(
                getApplicationContext(), Arrays.asList(SCOPES))
                .setBackOff(new ExponentialBackOff());


        // Progress Dialog is Initialized here
        mDialog = new ProgressDialog(this);
        mDialog.setTitle("Uploading ...");
        mDialog.setMessage("\nPlease wait while your file is being uploaded to the drive.");
        mDialog.setMax(100);
        mDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        mDialog.setProgress(0);
        mDialog.setCancelable(false);


        getResultsFromApi();

    }


    /**
     * Attempt to call the API, after verifying that all the preconditions are
     * satisfied. The preconditions are: Google Play Services installed, an
     * account was selected and the device currently has online access. If any
     * of the preconditions are not satisfied, the app will prompt the user as
     * appropriate.
     */
    private void getResultsFromApi() {
        if (!isGooglePlayServicesAvailable()) {
            acquireGooglePlayServices();
        } else if (mCredential.getSelectedAccountName() == null) {
            chooseAccount();
        } else if (!isDeviceOnline()) {
            Toast.makeText(UploadVideo.this, "No network connection available.", Toast.LENGTH_SHORT).show();
        } else {
            mTask = (aTask) new aTask(mCredential).execute();
        }
    }


    /**
     * Attempts to set the account used with the API credentials. If an account
     * name was previously saved it will use that one; otherwise an account
     * picker dialog will be shown to the user. Note that the setting the
     * account to use with the credentials object requires the app to have the
     * GET_ACCOUNTS permission, which is requested here if it is not already
     * present. The AfterPermissionGranted annotation indicates that this
     * function will be rerun automatically whenever the GET_ACCOUNTS permission
     * is granted.
     */
    @AfterPermissionGranted(REQUEST_PERMISSION_GET_ACCOUNTS)
    private void chooseAccount() {
        if (EasyPermissions.hasPermissions(
                this, Manifest.permission.GET_ACCOUNTS)) {
            String accountName = getPreferences(Context.MODE_PRIVATE)
                    .getString(PREF_ACCOUNT_NAME, null);
            if (accountName != null) {
                mCredential.setSelectedAccountName(accountName);
                getResultsFromApi();
            } else {
                // Start a dialog from which the user can choose an account
                startActivityForResult(
                        mCredential.newChooseAccountIntent(),
                        REQUEST_ACCOUNT_PICKER);
            }
        } else {
            // Request the GET_ACCOUNTS permission via a user dialog
            EasyPermissions.requestPermissions(
                    this,
                    "This app needs to access your Google account (via Contacts).",
                    REQUEST_PERMISSION_GET_ACCOUNTS,
                    Manifest.permission.GET_ACCOUNTS);
        }
    }


    /**
     * Called when an activity launched here (specifically, AccountPicker
     * and authorization) exits, giving you the requestCode you started it with,
     * the resultCode it returned, and any additional data from it.
     *
     * @param requestCode code indicating which activity result is incoming.
     * @param resultCode  code indicating the result of the incoming
     *                    activity result.
     * @param data        Intent (containing result data) returned by incoming
     *                    activity result.
     */
    @Override
    protected void onActivityResult(
            int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode) {
            case REQUEST_GOOGLE_PLAY_SERVICES:
                if (resultCode != RESULT_OK) {
                    Toast.makeText(this,
                            "This app requires Google Play Services. Please install " +
                                    "Google Play Services on your device and relaunch this app.", Toast.LENGTH_SHORT).show();
                } else {
                    getResultsFromApi();
                }
                break;
            case REQUEST_ACCOUNT_PICKER:
                if (resultCode == RESULT_OK && data != null &&
                        data.getExtras() != null) {
                    String accountName =
                            data.getStringExtra(AccountManager.KEY_ACCOUNT_NAME);
                    if (accountName != null) {
                        SharedPreferences settings =
                                getPreferences(Context.MODE_PRIVATE);
                        SharedPreferences.Editor editor = settings.edit();
                        editor.putString(PREF_ACCOUNT_NAME, accountName);
                        editor.apply();
                        mCredential.setSelectedAccountName(accountName);
                        getResultsFromApi();
                    }
                }
                break;
            case REQUEST_AUTHORIZATION:
                if (resultCode == RESULT_OK) {
                    getResultsFromApi();
                }
                break;
        }
    }


    /**
     * Respond to requests for permissions at runtime for API 23 and above.
     *
     * @param requestCode  The request code passed in
     *                     requestPermissions(android.app.Activity, String, int, String[])
     * @param permissions  The requested permissions. Never null.
     * @param grantResults The grant results for the corresponding permissions
     *                     which is either PERMISSION_GRANTED or PERMISSION_DENIED. Never null.
     */
    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        EasyPermissions.onRequestPermissionsResult(
                requestCode, permissions, grantResults, this);
    }


    /**
     * Callback for when a permission is granted using the EasyPermissions
     * library.
     *
     * @param requestCode The request code associated with the requested
     *                    permission
     * @param list        The requested permission list. Never null.
     */
    @Override
    public void onPermissionsGranted(int requestCode, List<String> list) {
        // Do nothing.
    }


    /**
     * Callback for when a permission is denied using the EasyPermissions
     * library.
     *
     * @param requestCode The request code associated with the requested
     *                    permission
     * @param list        The requested permission list. Never null.
     */
    @Override
    public void onPermissionsDenied(int requestCode, List<String> list) {
        // Do nothing.

        Log.e(TAG,"onPermissionsDenied");
        finish();

    }


    /**
     * Checks whether the device currently has a network connection.
     *
     * @return true if the device has a network connection, false otherwise.
     */
    private boolean isDeviceOnline() {
        ConnectivityManager connMgr =
                (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = connMgr.getActiveNetworkInfo();
        return (networkInfo != null && networkInfo.isConnected());
    }


    /**
     * Check that Google Play services APK is installed and up to date.
     *
     * @return true if Google Play Services is available and up to
     * date on this device; false otherwise.
     */
    private boolean isGooglePlayServicesAvailable() {
        GoogleApiAvailability apiAvailability =
                GoogleApiAvailability.getInstance();
        final int connectionStatusCode =
                apiAvailability.isGooglePlayServicesAvailable(this);
        return connectionStatusCode == ConnectionResult.SUCCESS;
    }


    /**
     * Attempt to resolve a missing, out-of-date, invalid or disabled Google
     * Play Services installation via a user dialog, if possible.
     */
    private void acquireGooglePlayServices() {
        GoogleApiAvailability apiAvailability =
                GoogleApiAvailability.getInstance();
        final int connectionStatusCode =
                apiAvailability.isGooglePlayServicesAvailable(this);
        if (apiAvailability.isUserResolvableError(connectionStatusCode)) {
            showGooglePlayServicesAvailabilityErrorDialog(connectionStatusCode);
        }
    }



    /**
     * Display an error dialog showing that Google Play Services is missing
     * or out of date.
     *
     * @param connectionStatusCode code describing the presence (or lack of)
     *                             Google Play Services on this device.
     */
    void showGooglePlayServicesAvailabilityErrorDialog(
            final int connectionStatusCode) {
        GoogleApiAvailability apiAvailability = GoogleApiAvailability.getInstance();
        Dialog dialog = apiAvailability.getErrorDialog(
                UploadVideo.this,
                connectionStatusCode,
                REQUEST_GOOGLE_PLAY_SERVICES);
        dialog.show();
    }


    /**
     * An asynchronous task that handles the Drive API call.
     * Placing the API calls in their own task ensures the UI stays responsive.
     *
     * This task is mainly used to upload the video file you selected and upload it
     * to the google drive with progress dialog
     */
    private class aTask extends AsyncTask<Void, Long, String> {

        // service of google drive API defined here
        private com.google.api.services.drive.Drive mService = null;

        // Exception defined here to check if authorization is done or some other exception occurs
        private Exception mLastError = null;


        // Defined to create a file from the uri you passed in this activity which will be later uploaded to Drive
        java.io.File fileContent;
        com.google.api.services.drive.model.File body;
        com.google.api.services.drive.model.File file;

        // Defined to find the size of the file
        long mFileLen;


        // Constructor of the task with credentials passed to check if user is authenticated and already logged in.
        // service object defined above is initialized here with login credentials
        aTask(GoogleAccountCredential credential) {
            HttpTransport transport = AndroidHttp.newCompatibleTransport();
            JsonFactory jsonFactory = JacksonFactory.getDefaultInstance();
            mService = new com.google.api.services.drive.Drive.Builder(
                    transport, jsonFactory, credential)
                    .setApplicationName("Rtc Backup")
                    .build();
        }


        /*
        * This function is called first when the aTask is called.
        * progress dialog is displayed here with progress defined to 0 percent
        */
        @Override
        protected void onPreExecute() {
            // TODO Auto-generated method stub
            super.onPreExecute();
            mDialog.show();
        }


        /*
        * doInBackground is the main function of this aTask Class.
        * All the uploading of the file is done here and progress dialog listener is
        * updated according to this function
        * */
        @Override
        protected String doInBackground(Void... arg0) {
            try {

                // Define the path of the file to be uploaded with Uri
                java.io.File UPLOAD_FILE = new java.io.File(String.valueOf(videoUri));

                // File's metadata.
                fileContent = new java.io.File(String.valueOf(videoUri));
                mFileLen = fileContent.length();

                // Define what kind of file it would be of type.In your case it is of type video with extension of mp4
                InputStreamContent mediaContent2 = new InputStreamContent("video/mp4", new BufferedInputStream(new FileInputStream(UPLOAD_FILE)));
                mediaContent2.setLength(UPLOAD_FILE.length());

                // Drive client is defined with the type of file and the name of it which will be uploaded.
                body = new com.google.api.services.drive.model.File();
                body.setTitle(fileContent.getName());
                body.setMimeType("video/mp4");

                // File is inserted with the body of file(containing title and type) and mediaContent2(which is your video
                // file) is put in Insert which later will uploaded.
                Drive.Files.Insert mInsert = mService.files().insert(body, mediaContent2);

                // A httpUploader defined which will upload the insert object defined above.
                MediaHttpUploader uploader = mInsert.getMediaHttpUploader();
                uploader.setDirectUploadEnabled(false);

                // Setting the chunk size to minimum so we can show it to progress bar and
                uploader.setChunkSize(MediaHttpUploader.MINIMUM_CHUNK_SIZE);

                //Setting progressbar listener so we can update progressbar value when uploading
                uploader.setProgressListener(new FileUploadProgressListener());

                // file is start to upload
                file = mInsert.execute();

                // Used to catch exception if any occurs and if occurs stop the uploading process and deal with it.
            } catch (IOException e) {

                mLastError = e;
                cancel(true);

                e.printStackTrace();
            }

            return null;
        }

        /*
        * A progress listener class that check how much file is uploaded and update the progress dialog
        * accordingly
        * */
        class FileUploadProgressListener implements MediaHttpUploaderProgressListener {

            @Override
            public void progressChanged(MediaHttpUploader uploader) throws IOException {
                Log.d("Uploader got Started", String.valueOf(uploader.getProgress()));
                switch (uploader.getUploadState()) {
                    case INITIATION_STARTED:
                        System.out.println("Initiation Started");
                        break;
                    case INITIATION_COMPLETE:
                        System.out.println("Initiation Completed");
                        break;
                    case MEDIA_IN_PROGRESS:
                        double percent = uploader.getProgress() * 100;
                        mDialog.setProgress((int) percent);
                        System.out.println("Upload in progress");
                        System.out.println("Upload percentage: " + uploader.getProgress());
                        break;
                    case MEDIA_COMPLETE:
                        System.out.println("Upload Completed!");
                        break;
                    case NOT_STARTED:
                        System.out.println("Upload Not Started!");
                        break;
                }
            }
        }

        // called when file is uploaded completely and a dialog is called to tell
        // user file has been uploaded successfully.
        protected void onPostExecute(String result) {
            mDialog.dismiss();
            showCompleteDialog();
        }

        // called and used if some exception has occured and now have to deal with it
        @Override
        protected void onCancelled() {
            mDialog.hide();

            if (mLastError instanceof GooglePlayServicesAvailabilityIOException) {
                showGooglePlayServicesAvailabilityErrorDialog(
                        ((GooglePlayServicesAvailabilityIOException) mLastError)
                                .getConnectionStatusCode());
            }
            else if (mLastError instanceof UserRecoverableAuthIOException) {
                startActivityForResult(
                        ((UserRecoverableAuthIOException) mLastError).getIntent(),
                        UploadVideo.REQUEST_AUTHORIZATION);
            }
            else {
                Toast.makeText(UploadVideo.this, "Network error occurred !", Toast.LENGTH_LONG).show();
                finish();
            }
        }
    }


    /**
     * A dialog that will be displayed when file is uploaded successfully and
     * user will be back to Main Screen when ok is pressed.
     */
    private void showCompleteDialog() {
        MaterialDialog dialog = new MaterialDialog.Builder(this)
                .title("Video Uploaded Successfully !")
                .titleColorRes(R.color.black)
                .content("Click Ok to Proceed.")
                .positiveText("OK")
                .cancelable(false)
                .onPositive(new MaterialDialog.SingleButtonCallback() {
                    @Override
                    public void onClick(MaterialDialog dialog, DialogAction which) {
                        // TODO
                        dialog.dismiss();
                        finish();
                    }
                }).show();
    }

}
