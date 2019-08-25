package com.uniqolabel.weatherapp.activities;

import android.Manifest;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.arch.lifecycle.Observer;
import android.arch.lifecycle.ViewModelProviders;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.constraint.ConstraintLayout;
import android.support.v4.app.ActivityCompat;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.animation.AnimationUtils;
import android.view.animation.Interpolator;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.uniqolabel.weatherapp.ForecastAdapter;
import com.uniqolabel.weatherapp.R;
import com.uniqolabel.weatherapp.model.CurrentWeatherResponse;
import com.uniqolabel.weatherapp.model.ForecastModel;
import com.uniqolabel.weatherapp.model.WeatherForecastResponse;
import com.uniqolabel.weatherapp.services.GPSTracker;
import com.uniqolabel.weatherapp.utils.DateUtility;
import com.uniqolabel.weatherapp.viewmodel.WeatherViewModel;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Locale;
import java.util.TimeZone;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;

public class MainActivity extends AppCompatActivity implements SwipeRefreshLayout.OnRefreshListener {
    private static final String TAG = "MainActivity";
    private static final int LOCATIO_REQUEST_CODE = 111;
    private static final String CELSIUS = "metric";
    public static final String IMAGE_LOADING_URL = "http://openweathermap.org/img/wn/";
    @BindView(R.id.toolbar_text)
    TextView toolbarText;
    @BindView(R.id.toolbar)
    Toolbar toolbar;
    @BindView(R.id.reload_button)
    Button reloadButton;
    @BindView(R.id.mask_frame)
    FrameLayout maskFrame;
    @BindView(R.id.temp_tv)
    TextView tempTv;
    @BindView(R.id.imageView)
    ImageView imageView;
    @BindView(R.id.temp_range_tv)
    TextView tempRangeTv;
    @BindView(R.id.weather_description)
    TextView weatherDescription;
    @BindView(R.id.description_icon)
    ImageView descriptionIcon;
    @BindView(R.id.humdity_tv)
    TextView humdityTv;
    @BindView(R.id.sunrise_tv)
    TextView sunriseTv;
    @BindView(R.id.sunset_tv)
    TextView sunsetTv;
    @BindView(R.id.forecast_recycler_view)
    RecyclerView forecastRecyclerView;
    @BindView(R.id.root)
    RelativeLayout root;
    @BindView(R.id.swipeToRefresh)
    SwipeRefreshLayout swipeToRefresh;
    @BindView(R.id.linearLayout)
    LinearLayout linearLayout;
    @BindView(R.id.root_one)
    ConstraintLayout rootOne;
    private WeatherViewModel weatherViewModel;
    private ProgressDialog progressDialog;
    private ForecastAdapter adapter;
    private ArrayList<ForecastModel> forecastModelArrayList;
    private Handler handler;
    private int delay = 0;
    private DateTimeZone dateTimeZone;


    enum Days {
        SUNDAY,
        MONDAY, TUESDAY, WEDNESDAY,
        THURSDAY, FRIDAY, SATURDAY;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        hideSystemUI(getWindow());

        ButterKnife.bind(this);
        setSupportActionBar(toolbar);
        weatherViewModel = ViewModelProviders.of(this).get(WeatherViewModel.class);
        forecastModelArrayList = new ArrayList<>();
        dateTimeZone = DateTimeZone.forTimeZone(Calendar.getInstance().getTimeZone());
        adapter = new ForecastAdapter(this, forecastModelArrayList);
        forecastRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        forecastRecyclerView.setAdapter(adapter);
        progressDialog = new ProgressDialog(this);
        progressDialog.setMessage("Loading...");
        attachApiCallObserver();
        swipeToRefresh.setOnRefreshListener(this);
        toolbarText.setText(getResources().getString(R.string.test_city_name));
        if (isNetworkAvailable()) {
            permissionRequest();
        } else {
            giveReloadOption("Turn on internet or WiFi");
        }
    }

    private void attachApiCallObserver() {
        WeatherViewModel.loading.observe(this, new Observer<Boolean>() {
            @Override
            public void onChanged(@Nullable Boolean aBoolean) {
                if(aBoolean) progressDialog.show();
                else progressDialog.hide();
            }
        });
    }

    private void permissionRequest() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION}, LOCATIO_REQUEST_CODE);
        } else {
            getWeatherReport();
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            hideSystemUI(getWindow());
        } else showSystemUI();
    }

    private void showSystemUI() {
        View decorView = getWindow().getDecorView();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            decorView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        }
    }

    private void hideSystemUI(Window window) {
        View decorView = window.getDecorView();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            decorView.setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);
        }
    }

    private void getWeatherReport() {
        maskFrame.setVisibility(View.GONE);
        GPSTracker gpsTracker = new GPSTracker(this);
        final Location location = gpsTracker.getLocation();
        if (location != null) {
            weatherViewModel.getCurrentWeatherInfoNetworkCall(String.valueOf(location.getLatitude()), String.valueOf(location.getLongitude()), CELSIUS, getResources().getString(R.string.appid)).observe(this, new Observer<CurrentWeatherResponse>() {
                @Override
                public void onChanged(@Nullable CurrentWeatherResponse weatherSuccessResponse) {
                    if (weatherSuccessResponse != null) {
                        tempTv.setText(String.format(Locale.ENGLISH, "%.2f", weatherSuccessResponse.getMain().getTemp()));
                        humdityTv.setText(String.format(Locale.ENGLISH, "%.2f", weatherSuccessResponse.getMain().getHumidity()));
                        tempRangeTv.setText(String.format(Locale.ENGLISH, "%.0f\u00B0~%.0f\u00B0", weatherSuccessResponse.getMain().getTempMin(), weatherSuccessResponse.getMain().getTempMax()));
                        weatherDescription.setText(String.valueOf(weatherSuccessResponse.getWeather().get(0).getDescription()));
                        toolbarText.setText(weatherSuccessResponse.getName());
                        Glide.with(getApplicationContext()).load(IMAGE_LOADING_URL + weatherSuccessResponse.getWeather().get(0).getIcon() + ".png").into(descriptionIcon);
                        sunriseTv.setText(DateUtility.getTimeFromTimestamp(weatherSuccessResponse.getSys().getSunrise()));
                        sunsetTv.setText(DateUtility.getTimeFromTimestamp(weatherSuccessResponse.getSys().getSunset()));
                        setTransitionYAnimationOnViewGroup(rootOne);
                        getWeatherForecastReport(String.valueOf(location.getLatitude()), String.valueOf(location.getLongitude()), CELSIUS, getResources().getString(R.string.appid));
                    } else {
                        Log.d(TAG, "onChanged: response is null");
                        Toast.makeText(MainActivity.this, "Response is null", Toast.LENGTH_SHORT).show();
                    }

                }
            });
            Log.d(TAG, "permissionRequest: long: " + location.getLongitude() + "\nLat: " + location.getLatitude());
        } else giveReloadOption("Turn on the GPS-Location service");

    }

    private void getWeatherForecastReport(String lat, String lon, String units, String appid) {
        weatherViewModel.getWeatherInfoNetworkCall(lat, lon, units, appid).observe(this, new Observer<WeatherForecastResponse>() {
            @Override
            public void onChanged(@Nullable WeatherForecastResponse weatherForecastResponse) {
                if (weatherForecastResponse != null) {
                    forecastModelArrayList.clear();
                    ForecastModel forecastModel;
                    DateTimeZone dateTimeZone = DateTimeZone.forTimeZone(TimeZone.getTimeZone(String.valueOf(weatherForecastResponse.getCity().getTimezone())));
                    for (int i = 0; i < weatherForecastResponse.getCnt(); i = i + 8) {
                        forecastModel = new ForecastModel();
                        forecastModel.setDate(DateUtility.getDateFromTimestamp(weatherForecastResponse.getList().get(i).getDt()));
                        Double minTemp = (weatherForecastResponse.getList().get(i).getMain().getTempMin());
//                        Double maxTemp = (weatherForecastResponse.getList().get(i).getMain().getTempMax()); // giving almost same in 3hrs duration , so not using
                        forecastModel.setTempRange(String.format(Locale.ENGLISH, "%.2f\u00B0", minTemp));
                        forecastModel.setDayName(getDayName(weatherForecastResponse.getList().get(i).getDt()));
                        forecastModel.setIconName(weatherForecastResponse.getList().get(i).getWeather().get(0).getIcon());
                        forecastModelArrayList.add(forecastModel);
                    }
                    adapter.notifyDataSetChanged();
                } else {
                    Log.d(TAG, "onChanged: forecast response is null");
                    Toast.makeText(MainActivity.this, "Forecast Response is null", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    public String getDayName(Long millisSinceEpochInUtc) {
        DateTime dateTime = new DateTime(millisSinceEpochInUtc * 1000, dateTimeZone);
        int dayOfWeekNumber = dateTime.getDayOfWeek(); // ISO 8601 standard says Monday is 1.
        DateTimeFormatter formatter = DateTimeFormat.forPattern("EEEE").withLocale(Locale.ENGLISH);
//        Days days;
//        return Days.values()[dayOfWeekNumber - 1].toString();
        return formatter.print(dateTime);
    }

    public void giveReloadOption(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
        maskFrame.setVisibility(View.VISIBLE);
        progressDialog.hide();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATIO_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                getWeatherReport();
            } else if (grantResults[0] == PackageManager.PERMISSION_DENIED) {
                if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.ACCESS_COARSE_LOCATION)) {
                    showRationaleDialog();
                } else {
                    showRationaleDialogSecond();

                }
            }
        }
    }

    private void showRationaleDialog() {
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setMessage("Allow Location Access to use the application")
                .setPositiveButton("Allow", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        // Continue with delete operation
                        Intent intent = new Intent();
                        intent.setAction(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                        Uri uri = Uri.fromParts("package", getPackageName(), null);
                        intent.setData(uri);
                        startActivity(intent);
                    }
                })
                .setNegativeButton("Deny", null)
                .create();
        dialog.show();
    }


    private void showRationaleDialogSecond() {
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setMessage("Location Access Permission is required for usage of this application.")
                .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        Intent intent = new Intent();
                        intent.setAction(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                        Uri uri = Uri.fromParts("package", getPackageName(), null);
                        intent.setData(uri);
                        startActivity(intent);

                    }
                })
                .create();
        dialog.show();
    }


    @Override
    protected void onResume() {
        super.onResume();
    }

    @OnClick(R.id.reload_button)
    public void onViewClicked() {
        permissionRequest();
    }

    private boolean isNetworkAvailable() {
        ConnectivityManager connectivityManager
                = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
        return activeNetworkInfo != null && activeNetworkInfo.isConnected();
    }

    public void setTransitionYAnimationOnViewGroup(ViewGroup root) {
        int count = root.getChildCount();
        float offset = getResources().getDimensionPixelSize(R.dimen.offset_y);
        Interpolator interpolator;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            interpolator = AnimationUtils.loadInterpolator(this, android.R.interpolator.linear_out_slow_in);
            // loop over the children setting an increasing translation y but the same animation
            // duration + interpolation
            for (int i = 0; i < count; i++) {
                View view = root.getChildAt(i);
                view.setVisibility(View.VISIBLE);
                view.setTranslationY(offset);
                view.setAlpha(0.67f);
                // then animate back to natural position
                view.animate()
                        .translationY(0f)
                        .alpha(1f)
                        .setInterpolator(interpolator)
                        .setDuration(400L)
                        .start();
                // increase the offset distance for the next view
                offset *= 1.5f;
            }
        }

    }

    @Override
    public void onRefresh() {
        permissionRequest();
        swipeToRefresh.setRefreshing(false);
        Toast.makeText(this, "Refreshed", Toast.LENGTH_SHORT).show();
    }

    //-----------setting double tap close feature ------------------
    boolean doubleBackToExitPressedOnce = false;


    @Override
    public void onBackPressed() {
        if (doubleBackToExitPressedOnce) {
            super.onBackPressed();
            return;
        }

        this.doubleBackToExitPressedOnce = true;
        Toast.makeText(this, "Please click BACK again to exit", Toast.LENGTH_SHORT).show();

        new Handler().postDelayed(new Runnable() {

            @Override
            public void run() {
                doubleBackToExitPressedOnce = false;
            }
        }, 2000);
    }

}
