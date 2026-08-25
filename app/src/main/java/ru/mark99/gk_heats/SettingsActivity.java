package ru.mark99.gk_heats;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

public class SettingsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.settings_activity);
        if (savedInstanceState == null) {
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.settings, new SettingsFragment())
                    .commit();
        }
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }
    }

    @SuppressWarnings("DataFlowIssue")
    public static class SettingsFragment extends PreferenceFragmentCompat {
        private static final String TAG = "GKH_SettingsFragment";

        Preference stateService;
        Preference board1State;
        Preference board1OpenWeb;

        Handler handler;

        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            setPreferencesFromResource(R.xml.root_preferences, rootKey);
            var rootPreference = getPreferenceScreen();
            ActionBar actionBar = ((AppCompatActivity) requireActivity()).getSupportActionBar();
            if (actionBar != null)
                actionBar.setTitle(rootPreference.getTitle());

            handler = new Handler(Looper.getMainLooper());

            stateService = findPreference("state_service");
            board1State = findPreference("board1_state");
            board1OpenWeb = findPreference("board1_open_web");
            board1OpenWeb.setOnPreferenceClickListener(preference -> {
                openBoard(1);
                return true;
            });

            stateService.setOnPreferenceClickListener(preference -> {
                if (MainService.context == null) {
                    Log.d(TAG, "starting service");
                    requireActivity().startForegroundService(new Intent(
                            requireActivity(), MainService.class
                    ));
                }
                return true;
            });

            findPreference("board1_enabled").setOnPreferenceChangeListener((preference, newValue) -> {
                if (MainService.context == null) return false;
                handler.postDelayed(() -> MainService.context.onConfigurationChanged(), 100);
                return true;
            });

            Log.d(TAG, "opened");
        }

        @Override
        public void onResume() {
            super.onResume();
            handler.postDelayed(this::reloadUi, 500);
        }

        @Override
        public void onPause() {
            handler.removeCallbacksAndMessages(null);
            super.onPause();
        }

        void reloadUi() {
            stateService.setSummary(
                    MainService.context == null ? "Не запущен, нажмите чтобы запустить" : "Запущен"
            );

            if (MainService.context == null) {
                board1State.setSummary("Сервис приложения не запущен");
                board1OpenWeb.setEnabled(false);
            } else {
                board1OpenWeb.setEnabled(MainService.context.board1.host != null);
                board1State.setSummary(MainService.context.board1.toString());
            }
            handler.postDelayed(this::reloadUi, 200);
        }

        void openBoard(int num) {
            if (MainService.context == null) {
                Log.d(TAG, "openBoard: MainService.context == null");
                return;
            }

            var board = num == 1 ? MainService.context.board1 : MainService.context.board2;
            if (board.host == null) {
                Log.d(TAG, "openBoard: board.host == null");
                return;
            }

            WebViewActivity.openWeb(
                requireActivity(),
                board.host,
                board.login,
                board.password
            );
        }
    }
}