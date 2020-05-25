package com.iordanvlad.urlchangecheck;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.StrictMode;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;
import android.webkit.URLUtil;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.core.app.ActivityCompat;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;

public class Popup extends Activity {

    private static final String TAG = "Popup class";
    private static final String NAME_ERROR = "Name is required!";
    private static final String URL_ERROR = "URL is required!";
    private static final String DUPLICATE_ERROR = "This URL is already monitored!";
    private static final String INVALID_URL_ERROR = "This URL is invalid!";

    ArrayList<String> url_data_list = new ArrayList<>();
    int position;
    String operation;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.popup);


        url_data_list = getIntent().getStringArrayListExtra("url_data_list");
        operation = getIntent().getStringExtra("operation");
        position = getIntent().getIntExtra("position", 0);

        Button submit = findViewById(R.id.add_url);
        Button delete = findViewById(R.id.delete_url);
        Button disable = findViewById(R.id.disable_url);

        StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
        StrictMode.setThreadPolicy(policy);

        //setPopupSize();
        initializePopup();

        submit.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(validateInputs() && isInternetPermissionGranted()){
                    updateUrlList();
                }
            }
        });
        delete.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                deleteUrlFromList(position);
            }
        });
        disable.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                disableUrlFromList(position);
            }
        });
    }

    private void disableUrlFromList(int position) {

        try {
            JSONObject json = new JSONObject(url_data_list.get(position));
            json.put("disabled", json.getString("disabled").equals("true") ? "false" : "true");
            url_data_list.set(position, json.toString());
        } catch (JSONException e) {
            e.printStackTrace();
        }

        Intent returnIntent = new Intent();
        returnIntent.putExtra("url_data_list", url_data_list);
        setResult(Activity.RESULT_OK, returnIntent);
        finish();
    }

    private void initializePopup() {
        final EditText url_name = findViewById(R.id.url_name);
        final EditText url_value = findViewById(R.id.url_value);
        Button add = findViewById(R.id.add_url);
        Button delete = findViewById(R.id.delete_url);
        Button disable = findViewById(R.id.disable_url);

        if (operation.equals("edit")) {
            try {
                JSONObject json = new JSONObject(url_data_list.get(position));
                url_name.setEnabled(false);
                delete.setVisibility(View.VISIBLE);
                disable.setVisibility(View.VISIBLE);
                add.setText("Save");
                disable.setText(json.getString("disabled").equals("true") ? "Enable" : "Disable");
                try {
                    url_name.setText(json.getString("name"));
                    url_value.setText(json.getString("url"));
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
        } else {
            delete.setVisibility(View.GONE);
            disable.setVisibility(View.GONE);
            add.setText("Add");

        }

    }

    private void updateUrlList() {
        final EditText url_name = findViewById(R.id.url_name);
        final EditText url_value = findViewById(R.id.url_value);

        String name = url_name.getText().toString();
        String url = url_value.getText().toString();

        String html = getHtmlWithProtocols(url);
        String hash = md5(html);
        int size = html.length();

        try {
            LocalDateTime now = LocalDateTime.now();
            String now_date = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").format(now);

            if (operation.equals("edit")) {
                JSONObject json = new JSONObject(url_data_list.get(position));
                String disabled = json.getString("disabled");
                json.put("name", name);
                json.put("url", url);
                json.put("hash", hash);
                json.put("size", size);
                json.put("lastUpdate", now_date);
                json.put("disabled", disabled);
                url_data_list.set(position, json.toString());
            } else {
                JSONObject json = new JSONObject();
                json.put("name", name);
                json.put("url", url);
                json.put("hash", hash);
                json.put("size", size);
                json.put("lastUpdate", now_date);
                json.put("disabled", "false");
                url_data_list.add(json.toString());
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }


        Intent returnIntent = new Intent();
        returnIntent.putExtra("url_data_list", url_data_list);
        setResult(Activity.RESULT_OK, returnIntent);
        finish();
    }

    private void deleteUrlFromList(int position) {
        url_data_list.remove(position);
        Intent returnIntent = new Intent();
        returnIntent.putExtra("url_data_list", url_data_list);
        setResult(Activity.RESULT_OK, returnIntent);
        finish();
    }

    private boolean validateInputs() {
        final EditText url_name = findViewById(R.id.url_name);
        final EditText url_value = findViewById(R.id.url_value);
        final TextView url_error = findViewById(R.id.url_error);

        String name = url_name.getText().toString();
        String url = url_value.getText().toString();


        if (name.isEmpty()){
            url_error.setText(URL_ERROR);
            return false;
        } else if (url.isEmpty() ) {
            url_error.setText(NAME_ERROR);
            return false;
        } else if (!URLUtil.isValidUrl(url)) {
            url_error.setText(INVALID_URL_ERROR);
            return false;
        } else if (checkUrl(url) >= 0 && !operation.equals("edit")) {
            url_error.setText(DUPLICATE_ERROR);
            return false;
        } else {
            return true;
        }
    }

    private int checkUrl(String url) {
        int index = 0;
        if (!url_data_list.isEmpty()) {
            for (String urlListElement : url_data_list) {
                index++;
                try {
                    JSONObject json = new JSONObject(urlListElement);
                    if (json.getString("url").equals(url)) {
                        return index;
                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        }
        return -1;
    }

    public String getSourceHtml(String url) {
        try {
            URLConnection connection = (new URL(url)).openConnection();
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);
            connection.connect();

            // Read and store the result line by line then return the entire string.
            InputStream in = connection.getInputStream();
            BufferedReader reader = new BufferedReader(new InputStreamReader(in));
            StringBuilder html = new StringBuilder();
            for (String line; (line = reader.readLine()) != null; ) {
                html.append(line);
            }
            in.close();
            return html.toString();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return "";
    }

    public String getHtmlWithProtocols(String url) {
        String html = "";
        ArrayList<String> protocols = new ArrayList<>();
        protocols.add("https://");
        protocols.add("http://");
        protocols.add("ftp://");

        html = getSourceHtml(url);
        if (html.equals("")) {
            for (String protocol : protocols) {
                String[] url_split = url.split("//", 2);
                url = url_split[url_split.length - 1];
                html = getSourceHtml(protocol + url);
                if (!html.equals("")) {
                    return html;
                }
            }
        }

        return html;
    }

    public String md5(String s) {
        try {
            // Create MD5 Hash
            MessageDigest digest = java.security.MessageDigest.getInstance("MD5");
            digest.update(s.getBytes());
            byte messageDigest[] = digest.digest();

            // Create Hex String
            StringBuffer hexString = new StringBuffer();
            for (byte b : messageDigest) hexString.append(Integer.toHexString(0xFF & b));
            return hexString.toString();

        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
        return "";
    }

    public boolean isInternetPermissionGranted() {
        if (checkSelfPermission(Manifest.permission.INTERNET)
                == PackageManager.PERMISSION_GRANTED) {
            Log.v(TAG, "Permission is granted1");
            return true;
        } else {

            Log.v(TAG, "Permission is revoked1");
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, 1);
            return false;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 1) {
            Log.d(TAG, "Internet");
            if(grantResults[0]== PackageManager.PERMISSION_GRANTED){
                Log.v(TAG,"Permission: "+permissions[0]+ "was "+grantResults[0]);
                //resume tasks needing this permission
                updateUrlList();
            }
        }
    }
}
