package com.beloncode.hackinarm;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.res.ResourcesCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.util.Log;
import android.widget.Toast;

import com.beloncode.hackinarm.adapter.IPAAdapter;
import com.beloncode.hackinarm.databinding.ActivityMainBinding;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.navigation.NavigationBarView;

public class MainActivity extends AppCompatActivity {
    final int f_viewerList = R.drawable.ic_baseline_view_list_24;
    final int f_gridList = R.drawable.ic_baseline_grid_view_24;

    int inUseListIcon = f_viewerList;
    HackLogger p_mainLogger = null;
    IPAHandler p_mainIPAHandler = null;

    ActivityResultLauncher<Intent> getProviderResult = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getData() == null || result.getResultCode() != RESULT_OK) {
                    p_mainLogger.release("Can't open the file or no file has been " +
                            "selected");
                    return;
                }
                Uri resolveURI = result.getData().getData();
                String URIAbsolutePath = "(Undefined)";

                try {
                    URIAbsolutePath = p_mainIPAHandler.pushIPAFromIPA(resolveURI);
                } catch (IPAException ipaException) {
                    p_mainLogger.release(Log.WARN, ipaException.getMessage());
                }

                final String toastMessage = String.format("Adding an IPA package with pathname: %s",
                        URIAbsolutePath);
                Toast.makeText(getApplicationContext(), toastMessage, Toast.LENGTH_LONG).show();

            });

    public void selectIPAArchive() {
        Intent openDocumentProvider = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        openDocumentProvider.setType("*/*");
        openDocumentProvider.addCategory(Intent.CATEGORY_OPENABLE);

        getProviderResult.launch(openDocumentProvider);
    }

    @RequiresApi(api = Build.VERSION_CODES.R)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ActivityMainBinding binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        p_mainLogger = new HackLogger(Log.INFO);
        p_mainIPAHandler = new IPAHandler(getApplicationContext(), p_mainLogger);

        hackInitSystem();
        NavigationBarView mainBarView = findViewById(R.id.nav_screen);
        mainBarView.setOnItemSelectedListener(item -> {
            if (item.getItemId() == R.id.view_button) {
                Drawable listIcon = ResourcesCompat.getDrawable(getResources(), f_viewerList,
                        getTheme());
                Drawable gridIcon = ResourcesCompat.getDrawable(getResources(), f_gridList,
                        getTheme());
                if (inUseListIcon == f_viewerList) {
                    item.setIcon(gridIcon);
                    inUseListIcon = f_gridList;
                } else {
                    item.setIcon(listIcon);
                    inUseListIcon = f_viewerList;
                }
            }
            return true;
        });

        FloatingActionButton mainAddButton = findViewById(R.id.add_button);
        mainAddButton.setOnClickListener(view -> selectIPAArchive());

        final Context mainContext = getApplicationContext();
        RecyclerView.LayoutManager mainContextListLayout = new LinearLayoutManager(mainContext);
        IPAAdapter mainIPAAdapter = new IPAAdapter();

        final RecyclerView mainIPAList = findViewById(R.id.ipa_list);
        mainIPAList.setLayoutManager(mainContextListLayout);
        mainIPAList.setHasFixedSize(true);
        mainIPAList.setAdapter(mainIPAAdapter);
    }

    static {
        System.loadLibrary("hackinarm");
    }

    @Override
    protected void onPause() {
        super.onPause();
        hackPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        hackResume();

        /* Requesting the permissions needed by the simulator */
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R)
        {
            final String readExternalPer = Manifest.permission.READ_EXTERNAL_STORAGE;
            final String writeExternalPer = Manifest.permission.WRITE_EXTERNAL_STORAGE;

            if ((ContextCompat.checkSelfPermission(getApplicationContext(), readExternalPer)
                    != PackageManager.PERMISSION_GRANTED) ||
                    (ContextCompat.checkSelfPermission(getApplicationContext(), writeExternalPer)
                    != PackageManager.PERMISSION_GRANTED))
            {
                final String[] requestPermissions = { readExternalPer, writeExternalPer };
                ActivityCompat.requestPermissions(this, requestPermissions, 1);
            }

        } else {
            Intent accessPermissionIntent = new Intent(
                    Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
            if (!Environment.isExternalStorageEmulated()) {
                startActivityIfNeeded(accessPermissionIntent, 1);
            }
        }

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        hackDestroy();
        getProviderResult.unregister();
        try {
            // Destroying all IO controllable resources
            p_mainIPAHandler.deleteAllResources();
        } catch (IPAException ipaException) {
            p_mainLogger.release(Log.ERROR, ipaException.getMessage());
        }
    }

    public native boolean hackInitSystem();
    public native boolean hackPause();
    public native boolean hackResume();
    public native boolean hackDestroy();
}