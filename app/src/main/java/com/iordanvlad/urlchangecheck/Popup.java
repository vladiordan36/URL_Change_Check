package com.iordanvlad.urlchangecheck;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;

public class Popup extends Activity {

    private static final String TAG = "Popup class";
    private static final String FIELDS_ERROR = "Both fields are required!";
    private static final String DUPLICATE_ERROR = "This URL is already monitored!";
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

        setPopupSize();
        initializePopup();

        submit.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(validateInputs()){
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
    }

    private void initializePopup() {
        final EditText url_name = findViewById(R.id.url_name);
        final EditText url_value = findViewById(R.id.url_value);
        Button add = findViewById(R.id.add_url);
        Button delete = findViewById(R.id.delete_url);

        if (operation.equals("edit")) {
            url_name.setEnabled(false);
            delete.setVisibility(View.VISIBLE);
            add.setText("Save");
            try {
                url_name.setText(new JSONObject(url_data_list.get(position)).getString("name"));
                url_value.setText(new JSONObject(url_data_list.get(position)).getString("url"));
            } catch (JSONException e) {
                e.printStackTrace();
            }
        } else {
            delete.setVisibility(View.GONE);
            add.setText("Add");

        }

    }

    private void updateUrlList() {
        final EditText url_name = findViewById(R.id.url_name);
        final EditText url_value = findViewById(R.id.url_value);

        String name = url_name.getText().toString();
        String url = url_value.getText().toString();

        try {
            if (operation.equals("edit")) {
                JSONObject json = new JSONObject(url_data_list.get(position));
                json.put("name", name);
                json.put("url", url);
                url_data_list.set(position, json.toString());
            } else {
                JSONObject json = new JSONObject();
                json.put("name", name);
                json.put("url", url);
                json.put("hash", "undefined");
                json.put("size", 0);
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

        if (name.isEmpty() || url.isEmpty()) {
            url_error.setText(FIELDS_ERROR);
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

    private void setPopupSize() {
        DisplayMetrics dm = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(dm);

        int width = (int) (dm.widthPixels * 0.9);
        int height = (int) (dm.heightPixels * 0.45);

        getWindow().setLayout(width, height);
    }
}
