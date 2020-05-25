package com.iordanvlad.urlchangecheck;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Environment;
import android.os.StrictMode;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.work.ListenableWorker;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.URL;
import java.net.URLConnection;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;

import static com.iordanvlad.urlchangecheck.UrlChangeCheck.CHANNEL_ID;

public class UrlCheckAlarm extends BroadcastReceiver {

    public static final String TAG = "UrlCheckAlarm";
    ArrayList<String> url_data_list = new ArrayList<>();
    private NotificationManagerCompat notificationManager;

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d(TAG, "Triggered");
        readElementsFromFile();

        StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
        StrictMode.setThreadPolicy(policy);

        if (!url_data_list.isEmpty()) {
            int position = 0;
            for (String urlListElement : url_data_list) {
                try {
                    JSONObject json = new JSONObject(urlListElement);
                    Log.d(TAG, "Checking entry: " + json.getString("url"));
                    String hash = json.getString("hash");
                    int size = json.getInt("size");

                    String new_html = getSourceHtml(json.getString("url"));
                    int new_size = new_html.length();
                    String new_hash = md5(new_html);

                    if(!new_hash.equals(hash) && json.getString("disabled").equals("false")) {
                        LocalDateTime now = LocalDateTime.now();
                        String now_date = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").format(now);

                        json.put("hash", new_hash);
                        json.put("size", new_size);
                        json.put("lastUpdate", now_date);
                        url_data_list.set(position, json.toString());
                        sendNotification(position, context);
                    }

                } catch (JSONException e) {
                    e.printStackTrace();
                }
                position++;
            }
            writeUrlsToFile();
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

                FileOutputStream outputStream = new FileOutputStream(file);
                BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(outputStream));

                if (!url_data_list.isEmpty()) {
                    for (String urlListElement: url_data_list) {
                        bw.write(urlListElement);
                        bw.newLine();
                    }
                }

                bw.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void sendNotification(int position, Context context) {
        try {
            JSONObject element = new JSONObject(url_data_list.get(position));
            Intent notificationIntent = new Intent(Intent.ACTION_VIEW);
            notificationIntent.setData(Uri.parse(element.getString("url")));
            PendingIntent pi = PendingIntent.getActivity(context, 0, notificationIntent, 0);
            Notification notification = new NotificationCompat.Builder(context, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ucc_logo)
                    .setContentTitle(element.getString("name"))
                    .setContentText("The webpage has changed")
                    .setContentIntent(pi)
                    .setAutoCancel(true)
                    .build();

            int ut1 = (int) Instant.now().getEpochSecond() + position;
            notificationManager = NotificationManagerCompat.from(context);
            notificationManager.notify(ut1, notification);
        } catch (JSONException e) {
            e.printStackTrace();
        }
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
}
