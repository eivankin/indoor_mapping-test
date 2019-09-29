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
import com.here.android.mpa.common.GeoPolyline;
import com.here.android.mpa.common.GeoPosition;
import com.here.android.mpa.common.IconCategory;
import com.here.android.mpa.common.Image;
import com.here.android.mpa.common.LocationDataSourceHERE;
import com.here.android.mpa.common.OnEngineInitListener;
import com.here.android.mpa.common.PositioningManager;
import com.here.android.mpa.mapping.Map;
import com.here.android.mpa.mapping.MapLabeledMarker;
import com.here.android.mpa.mapping.MapObject;
import com.here.android.mpa.mapping.MapOverlayType;
import com.here.android.mpa.mapping.MapPolygon;
import com.here.android.mpa.mapping.MapPolyline;
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
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.here.android.mpa.common.LocationDataSourceHERE.IndoorPositioningMode.DRAFT;
import static java.lang.Math.round;

public class MainActivity extends AppCompatActivity {


    // map embedded in the map fragment
    private Map map = null;

    MapPolyline route;
    MapLabeledMarker marker;
    MapLabeledMarker cp;

    boolean floor = false;
    boolean routing = false;
    List<GeoCoordinate> rPoints = new ArrayList<>();

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
                    if (!paused) { //эта штука меня бесит
                        calculateRoute(false);
                        //map.setCenter(new GeoCoordinate(51.680793, 39.184382), Map.Animation.NONE);
                    }
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
                        requestIndoorLayer ();



                        //calculateRoute();

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
        //ArrayList<MapObject> Mlist1 = Mlist;
        floor = ((ToggleButton) view).isChecked();
        map.removeMapObjects(Mlist);
        Mlist.clear();
        requestIndoorLayer();
        //Mlist1.clear();
        addPoints();
        //calculateRoute(false);
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

    public void requestIndoorLayer () {
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
                                    mapPolygon.setOverlayType(MapOverlayType.POI_OVERLAY);
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

    public void calculateRoute (boolean first) {
        if (routing) {
            GeoCoordinate coord = posManager.getPosition().getCoordinate();
            coord.setAltitude(0);
            if (rPoints.size() > 2) {
                List<GeoCoordinate> p1 = new ArrayList<>(2);
                List<GeoCoordinate> p2 = new ArrayList<>(2);
                p1.add(coord);
                p1.add(rPoints.get(2));
                p2.add(rPoints.get(1));
                p2.add(rPoints.get(2));
                GeoPolyline t1 = new GeoPolyline(p1);
                GeoPolyline t2 = new GeoPolyline(p2);
                if (t1.length() <= t2.length()) {
                    rPoints.remove(1);
                }
            }
            if (!first) rPoints.set(0, coord);
            map.removeMapObject(route);
            GeoPolyline rLine = new GeoPolyline(rPoints);
            route = new MapPolyline(rLine);
            route.setLineColor(Color.CYAN);
            route.setOutlineColor(Color.BLACK);
            route.setOutlineWidth(1);
            route.setLineWidth(8);
            route.setOverlayType(MapOverlayType.POI_OVERLAY);
            map.addMapObject(route);
            if (first) {
                Toast toast = Toast.makeText(getApplicationContext(),
                        "Длина маршрута: " + round(rLine.length()) + " м, примерное время в пути: " + round(rLine.length() / 60) + " минут", Toast.LENGTH_SHORT);
                toast.show();
            }
        }
    }

    public void onButtonClicked(View view) {
//        Image mark = new Image();
//        try {
//            mark.setImageFile("c:/android/mark.png");
//        } catch (IOException e) {
//            e.printStackTrace();
//        }


        if (!routing) {
            routing = true;
            addPoints();
        }
        calculateRoute(true);
    }

    private void addPoints ()
    {
        map.removeMapObject(marker);
        map.removeMapObject(cp);
        Image cpImg = new Image();
        try {
            cpImg.setImageResource(R.drawable.cp);
        } catch (IOException e) {
            e.printStackTrace();
        }
        GeoCoordinate coord = posManager.getPosition().getCoordinate();
        coord.setAltitude(0);
        rPoints.clear();
        rPoints.add(coord);
        if (!floor) {
            Image img = new Image();
            try {
                img.setImageResource(R.drawable.marker);
            } catch (IOException e) {
                e.printStackTrace();
            }
            cp = new MapLabeledMarker(new GeoCoordinate(51.6797382259965, 39.18064965953605));
            cp.setIcon(cpImg);
            cp.setOverlayType(MapOverlayType.FOREGROUND_OVERLAY);
            map.addMapObject(cp);
            marker = new MapLabeledMarker(new GeoCoordinate(51.67994813196171, 39.180479579322565));
            marker.setIcon(img);
            marker.setOverlayType(MapOverlayType.FOREGROUND_OVERLAY);
            map.addMapObject(marker);
            rPoints.add(new GeoCoordinate(51.68052809132096, 39.18439259144609));
            rPoints.add(new GeoCoordinate(51.681231, 39.184325));
            rPoints.add(new GeoCoordinate(51.681304, 39.184267));
            rPoints.add(new GeoCoordinate(51.681357, 39.184179));
            rPoints.add(new GeoCoordinate(51.681394, 39.184007));
            rPoints.add(new GeoCoordinate(51.681315, 39.183253));
            rPoints.add(new GeoCoordinate(51.680749, 39.180466));
            rPoints.add(new GeoCoordinate(51.67979596421586, 39.18093968823298));
            rPoints.add(new GeoCoordinate(51.6797388356649, 39.18065173274834));
            rPoints.add(new GeoCoordinate(51.67978950013267, 39.180497330003135));
            rPoints.add(new GeoCoordinate(51.67989746767144, 39.18043844282476));
            rPoints.add(new GeoCoordinate(51.679924896277, 39.18056495164337));
            rPoints.add(new GeoCoordinate(51.67996106008188, 39.18054269285345));
            rPoints.add(new GeoCoordinate(51.67994813196171, 39.180479579322565));
        }
        else {
            Image img = new Image();
            try {
                img.setImageResource(R.drawable.marker1);
            } catch (IOException e) {
                e.printStackTrace();
            }
            cp = new MapLabeledMarker(new GeoCoordinate(51.67977662512259, 39.180579354284774));
            cp.setIcon(cpImg);
            cp.setOverlayType(MapOverlayType.FOREGROUND_OVERLAY);
            marker = new MapLabeledMarker(new GeoCoordinate(51.67977903666078, 39.18055003549911));
            marker.setIcon(img);
            marker.setOverlayType(MapOverlayType.FOREGROUND_OVERLAY);
            map.addMapObject(cp);
            map.addMapObject(marker);
            rPoints.add(new GeoCoordinate(51.67994813196171, 39.180479579322565));
            rPoints.add(new GeoCoordinate(51.679926567251584, 39.18049103361452));
            rPoints.add(new GeoCoordinate(51.67981669176847, 39.180501467212956));
            rPoints.add(new GeoCoordinate(51.67977495362334, 39.18052132470682));
            rPoints.add(new GeoCoordinate(51.67977903666078, 39.18055003549911));
        }
    }
}
