package com.orbotix.drivesample;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.os.Bundle;
import android.util.Log;
import android.widget.ImageView;
import android.widget.ScrollView;
import android.widget.TextView;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.components.YAxis.AxisDependency;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.data.ScatterData;
import com.github.mikephil.charting.utils.ColorTemplate;
import com.jjoe64.graphview.series.DataPoint;
import com.orbotix.ConvenienceRobot;
import com.orbotix.Sphero;
import com.orbotix.async.CollisionDetectedAsyncData;
import com.orbotix.async.DeviceSensorAsyncMessage;
import com.orbotix.classic.DiscoveryAgentClassic;
import com.orbotix.common.DiscoveryAgent;
import com.orbotix.common.DiscoveryAgentEventListener;
import com.orbotix.common.DiscoveryException;
import com.orbotix.common.ResponseListener;
import com.orbotix.common.Robot;
import com.orbotix.common.RobotChangedStateListener;
import com.orbotix.common.internal.AsyncMessage;
import com.orbotix.common.internal.DeviceResponse;
import com.orbotix.common.sensor.SensorFlag;
import com.orbotix.subsystem.SensorControl;

import java.util.ArrayList;
import java.util.List;

public class RoomMapperActivity extends Activity implements DiscoveryAgentEventListener,
                                                            RobotChangedStateListener {

    private static final String TAG = "RoomMapperActivity";

    private DiscoveryAgent _currentDiscoveryAgent;
    private ConvenienceRobot _connectedRobot;
    private static long _sensorFlags = SensorFlag.ACCELEROMETER_NORMALIZED.longValue()
            | SensorFlag.GYRO_NORMALIZED.longValue()
            | SensorFlag.VELOCITY.longValue()
            | SensorFlag.LOCATOR.longValue();

    private double _posX;
    private double _posY;
    private double _posZ;
    private double _zDot;
    private double _yaw;
    private double _pitch;

    private double _accelX;
    private double _accelY;
    private double _accelZ;
    private double _gyroX;
    private double _gyroY;
    private double _gyroZ;
    private double _locX;
    private double _locY;
    private double _velX;
    private double _velY;

    private double _locXMin;
    private double _locXMax;
    private double _locYMin;
    private double _locYMax;

    private ArrayList<Point> _rawHistory;
    private ArrayList<Point> _scaledHistory;

    private TextView _logTextView;
    private ScrollView _scrollView;

    private Canvas _canvas;
    private Paint _paint;
    private Path _path;

    private class Point {
        public Point(double x, double y) { _x = x; _y = y;}
        public double get_x() { return _x; }
        public double get_y() { return _y; }
        private double _x;
        private double _y;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_room_mapper);
        _scrollView = (ScrollView) this.findViewById(R.id.scrollView);
        _logTextView = (TextView) this.findViewById(R.id.logTextView);
        ImageView imageView = (ImageView) this.findViewById(R.id.imageView);
        Bitmap bmp = Bitmap.createBitmap((int) getWindowManager().getDefaultDisplay().getWidth(),
                                        (int) getWindowManager().getDefaultDisplay().getWidth(),
                                        Bitmap.Config.ARGB_8888);
        _canvas = new Canvas(bmp);
        imageView.setImageBitmap(bmp);

        _paint = new Paint();
        _paint.setStyle(Paint.Style.STROKE);
        _paint.setStrokeWidth(5);
        _paint.setColor(Color.CYAN);
        _path = new Path();
        _path.moveTo(20, 20);
        _path.lineTo(100, 200);
        _path.lineTo(200, 100);
        _canvas.drawColor(Color.GRAY);
        _canvas.drawPath(_path, _paint);

        _rawHistory = new ArrayList<Point>();
        _scaledHistory = new ArrayList<Point>();
        _locXMin = 0;
        _locXMax = 0;
        _locYMin = 0;
        _locYMax = 0;

    }

    @Override
    protected void onStart() {
        super.onStart();

        // Start discovery of any spheros in the area
        _currentDiscoveryAgent = DiscoveryAgentClassic.getInstance();
        startDiscovery();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (_currentDiscoveryAgent != null) {
            for (Robot r : _currentDiscoveryAgent.getConnectedRobots()) {
                r.sleep();
                _currentDiscoveryAgent.removeRobotStateListener(this);
            }
        }
    }

    @Override
    protected void onStop() {
        if (_currentDiscoveryAgent != null) {
            for (Robot r : _currentDiscoveryAgent.getConnectedRobots()) {
                r.sleep();
            }
            _currentDiscoveryAgent.removeRobotStateListener(this);
        }
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        DiscoveryAgentClassic.getInstance().addRobotStateListener(null);
    }

    /**
     * Starts discovery on the set discovery agent and look for robots
     */
    private void startDiscovery() {
        try {
            // You first need to set up so that the discovery agent will notify you when it finds robots.
            // To do this, you need to implement the DiscoveryAgentEventListener interface (or declare
            // it anonymously) and then register it on the discovery agent with DiscoveryAgent#addDiscoveryListener()
            _currentDiscoveryAgent.addDiscoveryListener(this);
            // Second, you need to make sure that you are notified when a robot changes state. To do this,
            // implement RobotChangedStateListener (or declare it anonymously) and use
            // DiscoveryAgent#addRobotStateListener()
            _currentDiscoveryAgent.addRobotStateListener(this);
            // Then to start looking for a Sphero, you use DiscoveryAgent#startDiscovery()
            // You do need to handle the discovery exception. This can occur in cases where the user has
            // Bluetooth off, or when the discovery cannot be started for some other reason.
            _currentDiscoveryAgent.startDiscovery(this);
        } catch (DiscoveryException e) {
            Log.e(TAG, "Could not start discovery. Reason: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Invoked when the discovery agent finds a new available robot, or updates and already available robot
     *
     * @param robots The list of all robots, connected or not, known to the discovery agent currently
     */
    @Override
    public void handleRobotsAvailable(List<Robot> robots) {
        _currentDiscoveryAgent.connect(robots.get(0));

        _logTextView.append("Identifier: " + robots.get(0).getIdentifier() + "\n");
    }

    /**
     * Invoked when a robot changes state. For example, when a robot connects or disconnects.
     *
     * @param robot The robot whose state changed
     * @param type  Describes what changed in the state
     */
    @Override
    public void handleRobotChangedState(Robot robot, RobotChangedStateNotificationType type) {
        // For the purpose of this sample, we'll only handle the connected and disconnected notifications
        switch (type) {
            // A robot was connected, and is ready for you to send commands to it.
            case Online:
                _currentDiscoveryAgent.stopDiscovery();
                _currentDiscoveryAgent.removeDiscoveryListener(this);
                _connectedRobot = new Sphero(robot);
                _logTextView.append("Name: " + _connectedRobot.getRobot().getName() + "\n");

                // For visual feedback let's turn the robot green saying that it's been connected
                _connectedRobot.setLed(.5f, .7f, .5f);

                // Enable sensors and register a listener for messages
                _connectedRobot.enableCollisions(true);
                _connectedRobot.enableSensors(_sensorFlags, SensorControl.StreamingRate.STREAMING_RATE10);
                _connectedRobot.addResponseListener(new ResponseListener() {
                    @Override
                    public void handleAsyncMessage(AsyncMessage asyncMessage, Robot robot) {
                        if (asyncMessage instanceof DeviceSensorAsyncMessage) {
                            DeviceSensorAsyncMessage message = (DeviceSensorAsyncMessage) asyncMessage;
                            if (message.getAsyncData() == null
                                    || message.getAsyncData().isEmpty()
                                    || message.getAsyncData().get(0) == null)
                                return;
                            _accelX = message.getAsyncData().get(0).getAccelerometerData().getFilteredAcceleration().x;
                            _accelY = message.getAsyncData().get(0).getAccelerometerData().getFilteredAcceleration().y;
                            _accelZ = message.getAsyncData().get(0).getAccelerometerData().getFilteredAcceleration().z;

                            _gyroX = message.getAsyncData().get(0).getGyroData().getRotationRateFiltered().x;
                            _gyroY = message.getAsyncData().get(0).getGyroData().getRotationRateFiltered().y;
                            _gyroZ = message.getAsyncData().get(0).getGyroData().getRotationRateFiltered().z;

                            _locX = message.getAsyncData().get(0).getLocatorData().getPositionX();
                            _locY = message.getAsyncData().get(0).getLocatorData().getPositionY();
                            _velX = message.getAsyncData().get(0).getLocatorData().getVelocity().x;
                            _velY = message.getAsyncData().get(0).getLocatorData().getVelocity().y;
                            updateTrack();
                        } else if (asyncMessage instanceof CollisionDetectedAsyncData) {
                            addLandmark();
                            setNewHeading();
                        }
                    }

                    @Override
                    public void handleResponse(DeviceResponse deviceResponse, Robot robot) {
                    }

                    @Override
                    public void handleStringResponse(String s, Robot robot) {
                    }
                });

                _connectedRobot.drive(0.0f, 0.1f);
                break;
            case Disconnected:
                startDiscovery();
                break;
            default:
                Log.v(TAG, "Not handling state change notification: " + type);
                break;
        }
    }

    private void updateTrack() {
        // Basic implementation, no filtering
        _posX = _locX;
        _posY = _locY;
        _posZ = _posZ + _zDot * 0.1;
        _zDot = _zDot + _accelZ * 0.1;

        _yaw = Math.atan2(_velY, _velX);
        _pitch = _pitch + _gyroZ * 0.1;

        _rawHistory.add(_rawHistory.size(), new Point(_posX, _posY));
        updateCanvas();
        _logTextView.append("Update: Position: (" + _posX + ", " + _posY + "), Heading: " + (_yaw * 180 / Math.PI) + "\n");
        _scrollView.post(new Runnable() {
            @Override
            public void run() {
                _scrollView.fullScroll(ScrollView.FOCUS_DOWN);
            }
        });
    }

    private void addLandmark() {

    }

    private void setNewHeading() {
        float newHeading = (float) (_yaw + (Math.random() * 170.0 + 95) * 180 / Math.PI);
        if (Math.abs(newHeading) > Math.PI) {
            newHeading *= Math.signum(newHeading);
        }
        _connectedRobot.drive(newHeading, 0.1f);
    }

    private void updateCanvas() {
        boolean newRange = false;
        if (_locX < _locXMin) {_locXMin = _locX; newRange = true;}
        if (_locX > _locXMax) {_locXMax = _locX; newRange = true;}
        if (_locY < _locYMin) {_locYMin = _locY; newRange = true;}
        if (_locY > _locYMax) {_locYMax = _locY; newRange = true;}
        if (newRange) {
            // scale x and y values
            _scaledHistory = new ArrayList<Point>();
            _path = new Path();
            for (int i = 0; i < _rawHistory.size(); i++) {
                _scaledHistory.add(i, new Point(
                        _canvas.getWidth() * (_locXMax - _rawHistory.get(i).get_x()) / (_locXMax - _locXMin),
                        _canvas.getHeight() * (_locYMax - _rawHistory.get(i).get_y()) / (_locYMax - _locYMin)
                ));
                if (i == 0) {
                    _path.moveTo((float) _scaledHistory.get(i).get_x(),(float) _scaledHistory.get(i).get_y());
                }
                _path.lineTo((float) _scaledHistory.get(i).get_x(), (float) _scaledHistory.get(i).get_y());
            }
        } else {
            _scaledHistory.add(_scaledHistory.size(), new Point(
                    _canvas.getWidth() * (_locXMax - _rawHistory.get(_scaledHistory.size()).get_x()) / (_locXMax - _locXMin),
                    _canvas.getHeight() * (_locYMax - _rawHistory.get(_scaledHistory.size()).get_y()) / (_locYMax - _locYMin)
            ));
            _path.lineTo((float) _scaledHistory.get(_scaledHistory.size()-1).get_x(), (float) _scaledHistory.get(_scaledHistory.size()-1).get_y());
        }
        _canvas.drawColor(Color.GRAY);
        _canvas.drawPath(_path, _paint);
    }

}

