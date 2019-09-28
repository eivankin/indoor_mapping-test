package here.com;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.annotation.RequiresApi;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import com.here.android.mpa.common.GeoCoordinate;
import com.here.android.mpa.common.GeoPolygon;
import com.here.android.mpa.common.GeoPosition;
import com.here.android.mpa.common.LocationDataSourceHERE;
import com.here.android.mpa.common.OnEngineInitListener;
import com.here.android.mpa.common.PositioningManager;
import com.here.android.mpa.mapping.Map;
import com.here.android.mpa.mapping.MapObject;
import com.here.android.mpa.mapping.MapPolygon;
import com.here.android.mpa.mapping.MapRoute;
import com.here.android.mpa.mapping.SupportMapFragment;
import com.here.android.mpa.routing.CoreRouter;
import com.here.android.mpa.routing.RouteOptions;
import com.here.android.mpa.routing.RoutePlan;
import com.here.android.mpa.routing.RouteResult;
import com.here.android.mpa.routing.RouteWaypoint;
import com.here.android.mpa.routing.RoutingError;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.here.android.mpa.common.LocationDataSourceHERE.IndoorPositioningMode.DRAFT;

public class MainActivity extends AppCompatActivity {


    // map embedded in the map fragment
    private Map map = null;

    MapRoute mapRoute;

    ArrayList<MapObject> Mlist = new ArrayList<MapObject>();

    // map fragment embedded in this activity
    private SupportMapFragment mapFragment = null;

    /**
     * permissions request code
     */
    private final static int REQUEST_CODE_ASK_PERMISSIONS = 1;

    /**
     * Permissions that need to be explicitly requested from end user.
     */
    private static final String[] REQUIRED_SDK_PERMISSIONS = new String[] {
            Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.WRITE_EXTERNAL_STORAGE };

    private PositioningManager posManager;

    boolean paused = false;

    private PositioningManager.OnPositionChangedListener positionListener = new
            PositioningManager.OnPositionChangedListener() {

                public void onPositionUpdated(PositioningManager.LocationMethod method,
                                              GeoPosition position, boolean isMapMatched) {
                    // set the center only when the app is in the foreground
                    // to reduce CPU consumption
//                    if (!paused) { //эта штука меня бесит
//                        map.setCenter(new GeoCoordinate(51.680793, 39.184382), Map.Animation.NONE);
//                    }
                }

                public void onPositionFixChanged(PositioningManager.LocationMethod method,
                                                 PositioningManager.LocationStatus status) {
                }
            };

    public void onResume() {
        super.onResume();
        paused = false;
        if (posManager != null) {
            posManager.start(
                    PositioningManager.LocationMethod.GPS_NETWORK);
        }
    }

    // To pause positioning listener
    public void onPause() {
        if (posManager != null) {
            posManager.stop();
        }
        super.onPause();
        paused = true;
    }

    // To remove the positioning listener
    public void onDestroy() {
        if (posManager != null) {
            // Cleanup
            posManager.removeListener(
                    positionListener);
        }
        map = null;
        super.onDestroy();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        checkPermissions();
    }

    private void initialize() {
        setContentView(R.layout.activity_main);

        // Search for the map fragment to finish setup by calling init().
        mapFragment = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.mapfragment);

        // Set up disk cache path for the map service for this application
        // It is recommended to use a path under your application folder for storing the disk cache
        boolean success = com.here.android.mpa.common.MapSettings.setIsolatedDiskCacheRootPath(
                getApplicationContext().getExternalFilesDir(null) + File.separator + ".here-maps",
                "IndoorMapService"); /* ATTENTION! Do not forget to update {YOUR_INTENT_NAME} */

        if (!success) {
            Toast.makeText(getApplicationContext(), "Unable to set isolated disk cache path.", Toast.LENGTH_LONG);
        } else {
            mapFragment.init(new OnEngineInitListener() {
                @Override
                public void onEngineInitializationCompleted(OnEngineInitListener.Error error) {
                    if (error == OnEngineInitListener.Error.NONE) {
                        // retrieve a reference of the map from the map fragment
                        map = mapFragment.getMap();
                        // Set the map center to the Vancouver region (no animation)
                        map.setCenter(new GeoCoordinate(51.679786, 39.180533, 0.0),
                                Map.Animation.NONE);
                        // Set the zoom level to the average between min and max
                        map.setZoomLevel((map.getMaxZoomLevel() + map.getMinZoomLevel()) / 2);
                        map.setExtrudedBuildingsVisible(false);
                        requestIndoorLayer (false);



                        calculateRoute();

                        LocationDataSourceHERE m_hereDataSource = LocationDataSourceHERE.getInstance();
                        m_hereDataSource.setIndoorPositioningMode(LocationDataSourceHERE.IndoorPositioningMode.DRAFT);

                        if (m_hereDataSource != null) {
                            posManager = PositioningManager.getInstance();

                            posManager.setDataSource(m_hereDataSource);
                            posManager.addListener(new WeakReference<>(positionListener));
                            posManager.start(
                                    PositioningManager.LocationMethod.GPS_NETWORK_INDOOR);
                            map.getPositionIndicator().setVisible(true);

                        }


                    } else {
                        System.out.println("ERROR: Cannot initialize Map Fragment");
                    }
                }
            });
        }
    }

    public void onToggleClicked(View view) {

        // включена ли кнопка
        boolean on = ((ToggleButton) view).isChecked();
        map.removeMapObjects(Mlist);
        Mlist.clear();
        requestIndoorLayer(on);
        calculateRoute();
    }

    /**
     * Checks the dynamically-controlled permissions and requests missing permissions from end user.
     */
    protected void checkPermissions() {
        final List<String> missingPermissions = new ArrayList<String>();
        // check all required dynamic permissions
        for (final String permission : REQUIRED_SDK_PERMISSIONS) {
            final int result = ContextCompat.checkSelfPermission(this, permission);
            if (result != PackageManager.PERMISSION_GRANTED) {
                missingPermissions.add(permission);
            }
        }
        if (!missingPermissions.isEmpty()) {
            // request all missing permissions
            final String[] permissions = missingPermissions
                    .toArray(new String[missingPermissions.size()]);
            ActivityCompat.requestPermissions(this, permissions, REQUEST_CODE_ASK_PERMISSIONS);
        } else {
            final int[] grantResults = new int[REQUIRED_SDK_PERMISSIONS.length];
            Arrays.fill(grantResults, PackageManager.PERMISSION_GRANTED);
            onRequestPermissionsResult(REQUEST_CODE_ASK_PERMISSIONS, REQUIRED_SDK_PERMISSIONS,
                    grantResults);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String permissions[],
                                           @NonNull int[] grantResults) {
        switch (requestCode) {
            case REQUEST_CODE_ASK_PERMISSIONS:
                for (int index = permissions.length - 1; index >= 0; --index) {
                    if (grantResults[index] != PackageManager.PERMISSION_GRANTED) {
                        // exit the app if one permission is not granted
                        Toast.makeText(this, "Required permission '" + permissions[index]
                                + "' not granted, exiting", Toast.LENGTH_LONG).show();
                        finish();
                        return;
                    }
                }
                // all permissions were granted
                initialize();
                break;
        }
    }

    public void requestIndoorLayer (boolean floor) {
        String indoorUrl;
        if (!floor) {
            indoorUrl = "https://xyz.api.here.com/hub/spaces/LXuM0dZr/iterate?access_token=AK-64RQUyn4l-H655k1-zn0";
        } else {
            indoorUrl = "https://xyz.api.here.com/hub/spaces/p1Zn28A5/iterate?access_token=AFSE60JQ53Wx69wz2nMe82Y";
        }

        RequestQueue queue = Volley.newRequestQueue(this);

        StringRequest stringRequest = new StringRequest(Request.Method.GET, indoorUrl,
                new Response.Listener<String>() {
                    @RequiresApi(api = Build.VERSION_CODES.O)
                    @Override
                    public void onResponse(String response) {

                        JSONObject jsonObject = null;

                        try {
                            jsonObject = new JSONObject(response);
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }

                        try {
                            JSONArray jsonArray = jsonObject.getJSONArray("features");

                            String coords = "";

                            for (int i = 0; i < jsonArray.length(); i++) {
                                JSONObject jo = jsonArray.getJSONObject(i);
                                JSONObject geometry = jo.getJSONObject("geometry");
                                String geometryType = geometry.getString("type");

                                JSONArray coordsArray = geometry.getJSONArray("coordinates").getJSONArray(0);
                                JSONArray venue = coordsArray.getJSONArray(0);

                                List<GeoCoordinate> testPoints = new ArrayList<>();

                                for(int k = 0; k < venue.length(); k++) {
                                    JSONArray points = venue.getJSONArray(k);
                                    double latitude = (Double) points.get(1);
                                    double longitude = (Double) points.get(0);

                                    coords += " " + points.get(1).toString();

                                    testPoints.add(new GeoCoordinate(latitude, longitude, 0));
                                }



                                GeoPolygon polygon = new GeoPolygon(testPoints);
                                MapPolygon mapPolygon = new MapPolygon(polygon);

                                mapPolygon.setLineColor(Color.RED);

                                mapPolygon.setFillColor(Color.WHITE);

                                Mlist.add(mapPolygon);
//
                                map.addMapObject(mapPolygon);


                            }

//                            textView.setText(coords);

                        } catch (JSONException e) {
                            e.printStackTrace();
                        }

                    }
                }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {

            }
        });

        queue.add(stringRequest);
    }

    public void calculateRoute () {

        // Declare the variable (the CoreRouter)
        CoreRouter router = new CoreRouter();

        // Create the RoutePlan and add two waypoints
        RoutePlan routePlan = new RoutePlan();
        routePlan.addWaypoint(new RouteWaypoint(new GeoCoordinate(51.67991, 39.18473)));
        routePlan.addWaypoint(new RouteWaypoint(new GeoCoordinate(51.67974, 39.18092)));

        // Create the RouteOptions and set its transport mode & routing type
        RouteOptions routeOptions = new RouteOptions();
        routeOptions.setTransportMode(RouteOptions.TransportMode.PEDESTRIAN);
        routeOptions.setRouteType(RouteOptions.Type.SHORTEST);

        routePlan.setRouteOptions(routeOptions);

        // Calculate the route
        router.calculateRoute(routePlan, new RouteListener());

    }

    private class RouteListener implements CoreRouter.Listener {

        // Method defined in Listener
        public void onProgress(int percentage) {
            // Display a message indicating calculation progress
        }

        // Method defined in Listener
        public void onCalculateRouteFinished(List<RouteResult> routeResult, RoutingError error) {
            // If the route was calculated successfully
            if (error == RoutingError.NONE) {
                // Render the route on the map
                mapRoute = new MapRoute(routeResult.get(0).getRoute());
                map.addMapObject(mapRoute);
            }
            else {
                Toast toast = Toast.makeText(getApplicationContext(),
                        "Ошибка построения маршрута " + error, Toast.LENGTH_SHORT);
                toast.show();
            }
        }

    }
}
