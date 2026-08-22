package com.onecore.loader.activity;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;

import androidx.appcompat.app.AppCompatActivity;

import com.Jagdish.tastytoast.TastyToast;
import com.onecore.loader.R;
import com.onecore.loader.databinding.ActivityCrashBinding;

public class CrashActivity extends AppCompatActivity {
    private ActivityCrashBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityCrashBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        TastyToast.makeText(this, "Application Crash", TastyToast.LENGTH_LONG, TastyToast.ERROR);

        setSupportActionBar(binding.topAppBar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Application Crash");
        }

        String manufacturer = Build.MANUFACTURER;
        String deviceModel = Build.MODEL;
        String softwareInfo = getIntent().getStringExtra("Software");
        String errorInfo = getIntent().getStringExtra("Error");
        String dateInfo = getIntent().getStringExtra("Date");

        StringBuilder errorBuilder = new StringBuilder()
                .append("Manufacturer: ").append(manufacturer).append("\n")
                .append("Device: ").append(deviceModel).append("\n")
                .append(softwareInfo).append("\n\n")
                .append(errorInfo).append("\n\n")
                .append(dateInfo);

        binding.result.setText(errorBuilder.toString());

        binding.fab.setOnClickListener(v -> {
            ClipboardManager clipboard =
                    (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard != null) {
                clipboard.setPrimaryClip(ClipData.newPlainText("crash-report", binding.result.getText()));
            }
            TastyToast.makeText(this, "Text Copied", TastyToast.LENGTH_SHORT, TastyToast.SUCCESS);
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuItem close = menu.add(getString(R.string.close));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            close.setContentDescription("Close App");
        }
        close.setIcon(R.drawable.ic_close);
        close.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getTitle().equals(getString(R.string.close))) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        binding = null;
    }

    @Override
    public void finish() {
        super.finish();
        finishAndRemoveTask();
    }
}
