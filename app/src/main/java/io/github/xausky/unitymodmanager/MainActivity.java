package io.github.xausky.unitymodmanager;

import android.app.Fragment;
import android.app.ProgressDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.design.widget.NavigationView;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBar;
import android.support.design.widget.FloatingActionButton;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import com.lody.virtual.client.core.VirtualCore;
import com.lody.virtual.client.ipc.VActivityManager;

import java.io.File;

import io.github.xausky.unitymodmanager.adapter.VisibilityAdapter;
import io.github.xausky.unitymodmanager.fragment.BaseFragment;
import io.github.xausky.unitymodmanager.fragment.HomeFragment;
import io.github.xausky.unitymodmanager.fragment.ModFragment;
import io.github.xausky.unitymodmanager.utils.ModUtils;

public class MainActivity extends AppCompatActivity {
    private DrawerLayout drawerLayout;
    private FloatingActionButton actionButton;
    private NavigationView navigationView;
    private int currentNavigation;
    private ProgressDialog dialog;
    private HomeFragment homeFragment;
    private ModFragment modFragment;

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        String scheme = intent.getScheme();
        Uri uri = intent.getData();
        if("umm".equals(scheme) && uri != null && "import".equals(uri.getHost())){
            MainActivity.this.setTitle(getString(R.string.app_name) + "-" + getString(R.string.nav_mod));
            navigation(R.id.nav_mod);
            modFragment.importMod(uri.getQueryParameter("url"), uri.getQueryParameter("name"));
        } else {
            MainActivity.this.setTitle(getString(R.string.app_name) + "-" + getString(R.string.nav_home));
            navigation(R.id.nav_home);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        SharedPreferences preferences = this.getSharedPreferences("default", MODE_PRIVATE);
        BaseFragment.initialize(this);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        ActionBar actionBar = getSupportActionBar();
        if(actionBar != null){
            actionBar.setHomeAsUpIndicator(R.drawable.ic_menu);
            actionBar.setDisplayHomeAsUpEnabled(true);
        }
        drawerLayout = (DrawerLayout) findViewById(R.id.dl_main_drawer);
        navigationView = (NavigationView) findViewById(R.id.nv_main_navigation);
        actionButton = (FloatingActionButton) findViewById(R.id.action_button);
        actionButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                BaseFragment fragment = (BaseFragment)BaseFragment.fragment(currentNavigation, MainActivity.this.getApplication());
                try {
                    fragment.OnActionButtonClick();
                }catch (UnsupportedOperationException e){
                    Toast.makeText(MainActivity.this, R.string.unsupport_operation, Toast.LENGTH_SHORT).show();
                }
            }
        });
        actionButton.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                BaseFragment fragment = (BaseFragment)BaseFragment.fragment(currentNavigation, MainActivity.this.getApplication());
                try {
                    fragment.OnActionButtonLongClick();
                }catch (UnsupportedOperationException e){
                    Toast.makeText(MainActivity.this, R.string.unsupport_operation, Toast.LENGTH_SHORT).show();
                }
                return true;
            }
        });
        navigationView.setNavigationItemSelectedListener(new NavigationView.OnNavigationItemSelectedListener() {
            @Override
            public boolean onNavigationItemSelected(@NonNull MenuItem item) {
                if(item.getItemId() == R.id.nav_online_mods){
                    Intent intent = new Intent();
                    intent.setData(Uri.parse("https://umms.xausky.cn/"));
                    intent.setAction(Intent.ACTION_VIEW);
                    MainActivity.this.startActivity(intent);
                    return false;
                }
                navigation(item.getItemId());
                item.setChecked(true);
                MainActivity.this.setTitle(getString(R.string.app_name) + "-" + item.getTitle());
                return true;
            }
        });
        dialog = new ProgressDialog(this);
        dialog.setTitle(R.string.progress_dialog_title);
        dialog.setMessage(getString(R.string.progress_dialog_message));
        dialog.setCancelable(false);
        modFragment = (ModFragment) BaseFragment.fragment(R.id.nav_mod, MainActivity.this.getApplication());
        homeFragment = (HomeFragment) BaseFragment.fragment(R.id.nav_home, MainActivity.this.getApplication());
        homeFragment.ImportMapFile();
        onNewIntent(getIntent());
    }

    private void navigation(int item){
        Fragment fragment = BaseFragment.fragment(item, MainActivity.this.getApplication());
        getFragmentManager().beginTransaction().replace(R.id.fragment_container, fragment).commit();
        if(fragment instanceof BaseFragment){
            actionButton.setVisibility(((BaseFragment)fragment).actionButtonVisibility());
        } else {
            actionButton.setVisibility(View.INVISIBLE);
        }
        drawerLayout.closeDrawers();
        navigationView.setCheckedItem(item);
        currentNavigation = item;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                drawerLayout.openDrawer(GravityCompat.START);
                break;
            case R.id.menu_launch_game:
                launch();
                break;
        }
        return true;
    }

    public void launch(){
        if(homeFragment.apkModifyModel != HomeFragment.APK_MODIFY_MODEL_NONE && (homeFragment.apkPath == null || homeFragment.baseApkPath == null || !new File(homeFragment.baseApkPath).exists())){
            Toast.makeText(this, R.string.install_source_not_found, Toast.LENGTH_LONG).show();
        } else {
            if(homeFragment.apkModifyModel == HomeFragment.APK_MODIFY_MODEL_NONE && homeFragment.obbSupport){
                Toast.makeText(this, R.string.apk_none_obb_support, Toast.LENGTH_LONG).show();
            }
            new PatchApkTask(dialog).execute();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.option_menu, menu);
        return super.onCreateOptionsMenu(menu);
    }


    static class PatchApkTask extends AsyncTask<Object, Object, Integer> {
        private ProgressDialog dialog;

        public PatchApkTask(ProgressDialog dialog) {
            super();
            this.dialog = dialog;
        }

        @Override
        protected void onPreExecute() {
            dialog.show();
        }

        @Override
        protected Integer doInBackground(Object... params) {
            int result = ModUtils.RESULT_STATE_OK;
            ModFragment modFragment = (ModFragment) BaseFragment.fragment(R.id.nav_mod, dialog.getContext());
            HomeFragment homeFragment = (HomeFragment) BaseFragment.fragment(R.id.nav_home, dialog.getContext());
            if (modFragment.isNeedPatch()) {
                result = modFragment.patch(homeFragment.apkPath, homeFragment.baseApkPath, homeFragment.persistentPath, homeFragment.obbPath, homeFragment.baseObbPath, homeFragment.backupPath, homeFragment.apkModifyModel, homeFragment.persistentSupport, homeFragment.obbSupport);
            }
            if (result == ModUtils.RESULT_STATE_OK) {
                if(homeFragment.apkModifyModel != HomeFragment.APK_MODIFY_MODEL_VIRTUAL){
                    Intent intent = dialog.getContext().getPackageManager().getLaunchIntentForPackage(homeFragment.packageName);
                    dialog.getContext().startActivity(intent);
                } else {
                    Intent intent = VirtualCore.get().getLaunchIntent(homeFragment.packageName, 0);
                    VActivityManager.get().startActivity(intent, 0);
                }
            }
            return result;
        }

        @Override
        protected void onPostExecute(Integer result) {
            dialog.hide();
            ModFragment modFragment = (ModFragment) BaseFragment.fragment(R.id.nav_mod, dialog.getContext());
            switch (result) {
                case  ModUtils.RESULT_STATE_OK:
                    modFragment.setNeedPatch(false);
                    break;
                case ModUtils.RESULT_STATE_OBB_ERROR:
                    Toast.makeText(modFragment.getBase(), R.string.install_mods_obb_error, Toast.LENGTH_LONG).show();
                    break;
                case ModUtils.RESULT_STATE_ROOT_ERROR:
                    Toast.makeText(modFragment.getBase(), R.string.install_mods_root_error, Toast.LENGTH_LONG).show();
                    break;
                default:
                    Toast.makeText(modFragment.getBase(), R.string.install_mods_failed, Toast.LENGTH_LONG).show();
                    break;
            }
        }
    }
}
