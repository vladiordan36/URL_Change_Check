package com.iordanvlad.urlchangecheck;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity class";
    private static final int LAUNCH_POPUP_ACTIVITY = 107;
    private static final String UCC_WORK_NAME = "Periodic url change check";
    ArrayList<String> url_data_list = new ArrayList<>();
    ArrayAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        ListView list = findViewById(R.id.url_list);
        Button popup_button = findViewById(R.id.add_popup);

        if(isReadStoragePermissionGranted() && isWriteStoragePermissionGranted()){
            readElementsFromFile();
        }

        adapter = new ArrayAdapter(this, R.layout.list_item, R.id.list_name, url_data_list) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                View view = super.getView(position, convertView, parent);
                TextView name = (TextView) view.findViewById(R.id.list_name);
                TextView url = (TextView) view.findViewById(R.id.list_url);
                TextView lastUpdate = (TextView) view.findViewById(R.id.list_lastUpdate);

                try {
                    JSONObject json = new JSONObject(url_data_list.get(position));
                    name.setText("Name : " + json.getString("name"));
                    url.setText("URL : " + json.getString("url"));
                    lastUpdate.setText("Last updated at : " + json.getString("lastUpdate"));
                } catch (JSONException e) {
                    e.printStackTrace();
                }
                return view;
            }
        };
        list.setAdapter(adapter);

        setUpUrlCheckJob();

        popup_button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(MainActivity.this, Popup.class);
                intent.putExtra("url_data_list" , url_data_list);
                intent.putExtra("operation", "add");
                startActivityForResult(intent, LAUNCH_POPUP_ACTIVITY);
            }
        });

        list.setOnItemClickListener(new AdapterView.OnItemClickListener(){
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                Intent intent = new Intent(MainActivity.this,Popup.class);
                intent.putExtra("url_data_list" , url_data_list);
                intent.putExtra("operation", "edit");
                intent.putExtra("position" , position);
                startActivityForResult(intent, LAUNCH_POPUP_ACTIVITY);
            }
        });

    }

    public void setUpUrlCheckJob() {
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();
        final PeriodicWorkRequest periodicWorkRequest
                = new PeriodicWorkRequest.Builder(UrlChangeWorker.class, 15, TimeUnit.MINUTES)
                //.setConstraints(constraints)
                .build();

        WorkManager.getInstance().enqueueUniquePeriodicWork(
                UCC_WORK_NAME,
                ExistingPeriodicWorkPolicy.REPLACE,
                periodicWorkRequest
        );
    }

    public void readElementsFromFile() {
        try {
            String path = Environment.getExternalStorageDirectory().toString() + "/";
            File directory = new File(path + "UrlChangeCheck");
            String filename = "monitored_urls.txt";
            File file = new File(directory, filename);

            if (checkDirectory(directory, file)) {
                try {
                    BufferedReader br = new BufferedReader(new FileReader(file));
                    String line;
                    url_data_list.clear();

                    while ((line = br.readLine()) != null) {
                        JSONObject jsonObject = new JSONObject(line);
                        url_data_list.add(jsonObject.toString());
                    }
                    br.close();
                } catch (IOException e) {
                    e.printStackTrace();
                    Log.d(TAG, "Error reading file!");
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        } catch (IOException e){
            e.printStackTrace();
        }
    }

    public void writeUrlsToFile() {
        try {
            String path = Environment.getExternalStorageDirectory().toString() + "/";
            File directory = new File(path + "UrlChangeCheck");
            String filename = "monitored_urls.txt";
            File file = new File(directory, filename);

            if (checkDirectory(directory, file)) {
                Toast.makeText(this, "File created!" + directory.getPath() + " " + filename, Toast.LENGTH_SHORT).show();

                FileOutputStream outputStream = new FileOutputStream(file);
                BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(outputStream));

                if (!url_data_list.isEmpty()) {
                    for (String urlListElement: url_data_list) {
                        bw.write(urlListElement);
                        bw.newLine();
                    }
                }

                Toast.makeText(this, "File updated!", Toast.LENGTH_SHORT).show();
                bw.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private boolean checkDirectory(File directory, File file) throws IOException {
        boolean write = true;
        if (!directory.exists()) {
            if (!directory.mkdir()) {
                Log.d(TAG, "Error creating directory!");
                write = false;
            } else {
                if (!file.exists()) {
                    if (!file.createNewFile()) {
                        Log.d(TAG, "Error creating file!");
                        write = false;
                    }
                }
            }
        }
        return write;
    }

    public boolean isReadStoragePermissionGranted() {
        if (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE)
                == PackageManager.PERMISSION_GRANTED) {
            Log.v(TAG, "Permission is granted1");
            return true;
        } else {

            Log.v(TAG, "Permission is revoked1");
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, 3);
            return false;
        }
    }

    public boolean isWriteStoragePermissionGranted() {
        if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                == PackageManager.PERMISSION_GRANTED) {
            Log.v(TAG, "Permission is granted2");
            return true;
        } else {

            Log.v(TAG, "Permission is revoked2");
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 2);
            return false;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 3) {
            Log.d(TAG, "External storage1");
            if(grantResults[0]== PackageManager.PERMISSION_GRANTED){
                Log.v(TAG,"Permission: "+permissions[0]+ "was "+grantResults[0]);
                //resume tasks needing this permission
                readElementsFromFile();
            }
        } else if (requestCode == 2) {
            Log.d(TAG, "External storage2");
            if(grantResults[0]== PackageManager.PERMISSION_GRANTED){
                Log.v(TAG,"Permission: "+permissions[0]+ "was "+grantResults[0]);
                //resume tasks needing this permission
                writeUrlsToFile();
            }
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if(isReadStoragePermissionGranted()){
            readElementsFromFile();
        }
        adapter.notifyDataSetChanged();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == LAUNCH_POPUP_ACTIVITY) {
            if(resultCode == Activity.RESULT_OK){
                url_data_list.clear();
                url_data_list.addAll(data.getStringArrayListExtra("url_data_list"));
                if (isWriteStoragePermissionGranted()) {
                    writeUrlsToFile();
                }
                adapter.notifyDataSetChanged();
            }
        }
    }
}
